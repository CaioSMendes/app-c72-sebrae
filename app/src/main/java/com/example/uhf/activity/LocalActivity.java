package com.example.uhf.activity;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Local;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalActivity extends AppCompatActivity {

    private static final int    REQUEST_CODE_XLSX = 1001;
    private static final String TAG               = "LocalActivity";
    // API_KEY e ENDPOINT_ID removidos — vêm do SettingsActivity dinamicamente

    private static final String REQUEST_BODY = "{\"CODCOLIGADA\":1,\"CODFILIAL\":1,\"ATIVO\":1}";

    // Poison pill para sinalizar fim da fila
    private static final Local POISON_PILL = new Local(-1, null, null, null);

    // Formulário
    private EditText    edtLocal, edtCodLocal, edtCodFilial;
    private ListView    listViewLocais;
    private TextView    txtDadosSalvos;
    private DBHelper    dbHelper;
    private List<Local> listaLocais;

    // Overlay de sync
    private FrameLayout overlaySync;
    private ProgressBar progressSpinner, progressBar;
    private TextView    txtSyncTitulo, txtSyncContador;
    private TextView    txtSyncNovos, txtSyncDuplicados, txtSyncErros;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local);

        edtLocal       = findViewById(R.id.edtlocal);
        edtCodLocal    = findViewById(R.id.edtCodLoc);
        edtCodFilial   = findViewById(R.id.edtCodFilial);
        listViewLocais = findViewById(R.id.listViewLocais);
        txtDadosSalvos = findViewById(R.id.txtDadosSalvos);

        overlaySync       = findViewById(R.id.overlaySync);
        progressSpinner   = findViewById(R.id.progressSpinner);
        progressBar       = findViewById(R.id.progressBar);
        txtSyncTitulo     = findViewById(R.id.txtSyncTitulo);
        txtSyncContador   = findViewById(R.id.txtSyncContador);
        txtSyncNovos      = findViewById(R.id.txtSyncNovos);
        txtSyncDuplicados = findViewById(R.id.txtSyncDuplicados);
        txtSyncErros      = findViewById(R.id.txtSyncErros);

        dbHelper = DBHelper.getInstance(this);

        configurarEditTextNumerico(edtCodLocal,  4);
        configurarEditTextNumerico(edtCodFilial, 3);

        carregarLocais();

        findViewById(R.id.btnSalvar).setOnClickListener(v -> salvarLocal());
        findViewById(R.id.btnSync).setOnClickListener(v -> sincronizarLocais());
        findViewById(R.id.btnExibir).setOnClickListener(v ->
                startActivity(new Intent(LocalActivity.this, ListaLocaisActivity.class)));

        if (listViewLocais != null) {
            listViewLocais.setOnItemClickListener((parent, view, position, id) -> {
                Local sel = listaLocais.get(position);
                Intent intent = new Intent(LocalActivity.this, DetalheLocalActivity.class);
                intent.putExtra("localId", sel.getId());
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLocais();
    }

    // =========================================================================
    // Overlay
    // =========================================================================

    private void mostrarOverlay() {
        progressBar.setProgress(0);
        txtSyncTitulo.setText("Sincronizando...");
        txtSyncContador.setText("Conectando ao servidor...");
        txtSyncNovos.setText("0");
        txtSyncDuplicados.setText("0");
        txtSyncErros.setText("0");
        overlaySync.setVisibility(View.VISIBLE);
    }

    private void atualizarOverlay(int processados, int total,
                                  int inseridos, int ignorados, int erros) {
        int pct = total > 0 ? (processados * 100 / total) : 0;
        progressBar.setProgress(pct);
        txtSyncContador.setText(processados + " / " + total + " itens  (" + pct + "%)");
        txtSyncNovos.setText(String.valueOf(inseridos));
        txtSyncDuplicados.setText(String.valueOf(ignorados));
        txtSyncErros.setText(String.valueOf(erros));
    }

    private void finalizarOverlay(boolean sucesso, String mensagem,
                                  int inseridos, int ignorados, int erros) {
        progressBar.setProgress(100);
        txtSyncTitulo.setText(sucesso ? "✅ Concluído!" : "⚠️ Erro!");
        txtSyncContador.setText(mensagem);
        txtSyncNovos.setText(String.valueOf(inseridos));
        txtSyncDuplicados.setText(String.valueOf(ignorados));
        txtSyncErros.setText(String.valueOf(erros));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            overlaySync.setVisibility(View.GONE);
            txtDadosSalvos.setText(
                    (sucesso ? "✅ " : "⚠️ ") + mensagem + "\n" +
                            "Inseridos: " + inseridos + "\n" +
                            "Ignorados: " + ignorados + "\n" +
                            "Erros: "     + erros
            );
            if (sucesso) carregarLocais();
        }, 1500);
    }

    // =========================================================================
    // Sync — 1 thread de rede (producer) + 1 thread de banco (consumer)
    // =========================================================================

    private void sincronizarLocais() {
        // URL completa já vem do SettingsActivity — sem concatenar endpoint aqui
        String urlCompleta = SettingsActivity.getBaseUrl(this);
        String apiKey      = SettingsActivity.getApiKey(this);

        if (urlCompleta == null || urlCompleta.isEmpty()) {
            Toast.makeText(this,
                    "Configure o ambiente em Configurações antes de sincronizar.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Sync URL: " + urlCompleta);

        mostrarOverlay();

        Handler       handler   = new Handler(Looper.getMainLooper());
        AtomicInteger inseridos = new AtomicInteger(0);
        AtomicInteger ignorados = new AtomicInteger(0);
        AtomicInteger erros     = new AtomicInteger(0);
        AtomicInteger total     = new AtomicInteger(0);
        AtomicBoolean falhou    = new AtomicBoolean(false);

        BlockingQueue<Local> fila        = new LinkedBlockingQueue<>(200);
        ExecutorService      producerExe = Executors.newSingleThreadExecutor();
        ExecutorService      dbExe       = Executors.newSingleThreadExecutor();
        ExecutorService      monitorExe  = Executors.newSingleThreadExecutor();

        // ── Consumer ─────────────────────────────────────────────────────────
        dbExe.submit(() -> {
            SQLiteDatabase sqlDB = dbHelper.getWritableDatabase();
            sqlDB.beginTransaction();
            try {
                while (true) {
                    Local local;
                    try {
                        local = fila.poll(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    if (local == null) {
                        if (producerExe.isTerminated()) break;
                        continue;
                    }
                    if (local.getId() == -1) break; // poison pill

                    try {
                        boolean existe = dbHelper.existeCodigoLocalNaTransacao(
                                sqlDB, local.getCodigoLocal());

                        if (existe) {
                            ignorados.incrementAndGet();
                        } else {
                            dbHelper.salvarLocalNaTransacao(sqlDB, local);
                            inseridos.incrementAndGet();
                        }

                        int processados = inseridos.get() + ignorados.get() + erros.get();
                        if (processados % 20 == 0) {
                            final int fi = inseridos.get(), fg = ignorados.get(),
                                    fe = erros.get(), ft = total.get();
                            handler.post(() -> atualizarOverlay(fi + fg + fe, ft, fi, fg, fe));
                        }

                    } catch (Exception e) {
                        erros.incrementAndGet();
                        Log.e(TAG, "DB erro: " + e.getMessage(), e);
                    }
                }

                sqlDB.setTransactionSuccessful();

            } finally {
                sqlDB.endTransaction();
                sqlDB.close();
            }
        });

        // ── Producer ─────────────────────────────────────────────────────────
        producerExe.submit(() -> {
            try {
                URL               url  = new URL(urlCompleta);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept",       "application/json");
                conn.setRequestProperty("apiKey",       apiKey);

                try (OutputStream os = conn.getOutputStream();
                     BufferedWriter bw = new BufferedWriter(
                             new OutputStreamWriter(os, "UTF-8"))) {
                    bw.write(REQUEST_BODY);
                    bw.flush();
                }

                int httpCode = conn.getResponseCode();
                if (httpCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + httpCode + ": " + conn.getResponseMessage());
                }

                handler.post(() -> txtSyncContador.setText("Lendo resposta..."));

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONArray result = new JSONObject(sb.toString()).getJSONArray("result");
                total.set(result.length());
                handler.post(() ->
                        txtSyncContador.setText("0 / " + total.get() + " itens  (0%)"));

                for (int i = 0; i < result.length(); i++) {
                    try {
                        JSONObject item     = result.getJSONObject(i);
                        String     codLocal = item.optString("CODLOCAL",   "").trim();
                        String     nome     = item.optString("NOME_LOCAL", "").trim();

                        if (codLocal.isEmpty() || nome.isEmpty()) {
                            erros.incrementAndGet();
                            continue;
                        }
                        fila.put(new Local(0, nome, "", codLocal));

                    } catch (Exception ex) {
                        erros.incrementAndGet();
                        Log.e(TAG, "Parse item " + i + ": " + ex.getMessage(), ex);
                    }
                }

            } catch (Exception e) {
                falhou.set(true);
                Log.e(TAG, "Producer erro: " + e.getMessage(), e);
                try { fila.put(POISON_PILL); } catch (InterruptedException ignored) {}
                handler.post(() ->
                        finalizarOverlay(false, "Erro: " + e.getMessage(),
                                inseridos.get(), ignorados.get(), erros.get()));
                return;
            }

            try { fila.put(POISON_PILL); } catch (InterruptedException ignored) {}
            producerExe.shutdown();
        });

        // ── Monitor ──────────────────────────────────────────────────────────
        monitorExe.submit(() -> {
            try {
                dbExe.shutdown();
                dbExe.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (falhou.get()) return;

            final int fIns = inseridos.get(), fIgn = ignorados.get(), fErr = erros.get();
            handler.post(() ->
                    finalizarOverlay(true, "Sincronização concluída!", fIns, fIgn, fErr));

            monitorExe.shutdown();
        });
    }

    // =========================================================================
    // Salvar manual
    // =========================================================================

    private void salvarLocal() {
        String nome      = edtLocal.getText().toString().trim();
        String codLocal  = edtCodLocal.getText().toString().trim();
        String codFilial = edtCodFilial.getText().toString().trim();

        if (nome.isEmpty() || codLocal.isEmpty() || codFilial.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (codLocal.length() != 4 || codFilial.length() != 3) {
            Toast.makeText(this, "Código Local/Filial com tamanho incorreto", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dbHelper.existeCodigoLocal(codLocal)) {
            Toast.makeText(this, "Este código local já existe!", Toast.LENGTH_SHORT).show();
            return;
        }

        Local local = new Local(0, nome, codFilial, codLocal);
        long id = dbHelper.salvarLocal(local);
        if (id != -1) {
            Toast.makeText(this, "Local salvo com sucesso!", Toast.LENGTH_SHORT).show();
            edtLocal.setText("");
            edtCodLocal.setText("");
            edtCodFilial.setText("");
            carregarLocais();
        } else {
            Toast.makeText(this, "Erro ao salvar local", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void carregarLocais() {
        if (listViewLocais == null) return;
        listaLocais = dbHelper.listarLocais();
        List<String> nomes = new ArrayList<>();
        for (Local l : listaLocais)
            nomes.add(l.getLocalNome() + " (" + l.getCodigoLocal() + ")");
        listViewLocais.setAdapter(new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, nomes));
    }

    private void configurarEditTextNumerico(EditText edt, int maxLength) {
        edt.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(maxLength) });
        edt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String limpo = s.toString().replaceAll("[^0-9]", "");
                if (!limpo.equals(s.toString())) {
                    edt.setText(limpo);
                    edt.setSelection(limpo.length());
                }
            }
        });
    }

    // =========================================================================
    // Import XLSX (mantido intacto)
    // =========================================================================

    private void abrirSeletorXLSX() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(
                Intent.createChooser(intent, "Selecione um arquivo Excel"),
                REQUEST_CODE_XLSX);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_XLSX && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importarXLSX(uri);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case STRING:  return cell.getStringCellValue().trim();
                    case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
                    case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                    default:      return "";
                }
            default: return "";
        }
    }

    private void importarXLSX(Uri uri) {
        Toast.makeText(this, "Iniciando importação, aguarde...", Toast.LENGTH_SHORT).show();
        Handler         handler      = new Handler(Looper.getMainLooper());
        ExecutorService executorMain = Executors.newSingleThreadExecutor();

        executorMain.execute(() -> {
            AtomicInteger importados = new AtomicInteger(0);
            AtomicInteger duplicados = new AtomicInteger(0);
            AtomicInteger erros      = new AtomicInteger(0);

            try (InputStream is = getContentResolver().openInputStream(uri);
                 XSSFWorkbook wb = new XSSFWorkbook(is)) {

                XSSFSheet sheet  = wb.getSheetAt(0);
                Row       header = sheet.getRow(0);
                if (header == null) throw new Exception("Cabeçalho não encontrado!");

                int colLocalNome = -1, colFilial = -1, colLocal = -1;
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    if (header.getCell(c) == null) continue;
                    String t = getCellValue(header.getCell(c)).trim();
                    if (t.equalsIgnoreCase("Local_Nome"))    colLocalNome = c;
                    if (t.equalsIgnoreCase("Código Filial")) colFilial    = c;
                    if (t.equalsIgnoreCase("Código Local"))  colLocal     = c;
                }

                if (colLocalNome == -1 || colFilial == -1 || colLocal == -1)
                    throw new Exception("Colunas não encontradas!");

                final int fNome = colLocalNome, fFil = colFilial, fLoc = colLocal;
                int       totalLinhas = sheet.getLastRowNum();
                ExecutorService exPar = Executors.newFixedThreadPool(
                        Runtime.getRuntime().availableProcessors());

                for (int inicio = 1; inicio <= totalLinhas; inicio += 100) {
                    int fim = Math.min(inicio + 99, totalLinhas);
                    int fI  = inicio, fF = fim;
                    exPar.submit(() -> {
                        for (int i = fI; i <= fF; i++) {
                            try {
                                Row row = sheet.getRow(i);
                                if (row == null) continue;
                                String nome      = getCellValue(row.getCell(fNome)).trim();
                                String rawFil    = getCellValue(row.getCell(fFil)).replaceAll("[^0-9]", "");
                                String rawLoc    = getCellValue(row.getCell(fLoc)).replaceAll("[^0-9]", "");
                                String codFilial = rawFil.isEmpty() ? "" : String.format("%03d", Integer.parseInt(rawFil));
                                String codLoc    = rawLoc.isEmpty() ? "" : String.format("%04d", Integer.parseInt(rawLoc));
                                if (codFilial.length() != 3 || codLoc.length() != 4) { erros.incrementAndGet(); continue; }
                                if (dbHelper.existeCodigoLocal(codLoc)) { duplicados.incrementAndGet(); continue; }
                                dbHelper.salvarLocal(new Local(0, nome, codFilial, codLoc));
                                importados.incrementAndGet();
                            } catch (Exception ex) {
                                erros.incrementAndGet();
                                Log.e(TAG, "XLSX linha " + i + ": " + ex.getMessage(), ex);
                            }
                            if (i % 10 == 0 || i == fF) {
                                final int fi = importados.get(), fd = duplicados.get(),
                                        fe = erros.get(), tot = totalLinhas, lin = i;
                                handler.post(() -> txtDadosSalvos.setText(
                                        "Processadas: " + lin + " / " + tot +
                                                "\nImportados: " + fi + "\nDuplicados: " + fd +
                                                "\nErros: " + fe));
                            }
                        }
                    });
                }
                exPar.shutdown();
                while (!exPar.isTerminated()) Thread.sleep(100);
                handler.post(() -> Toast.makeText(this,
                        "✅ Importação concluída!", Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e(TAG, "Erro XLSX: " + e.getMessage(), e);
                handler.post(() -> Toast.makeText(this,
                        "❌ Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                executorMain.shutdown();
            }
        });
    }
}