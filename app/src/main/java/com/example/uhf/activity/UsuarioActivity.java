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

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Usuario;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

public class UsuarioActivity extends AppCompatActivity {

    private static final String TAG         = "UsuarioActivity";
    private static final String POISON_PILL_MATRICULA = "__POISON__";

    // Formulário
    private EditText  edtNome, edtMat;
    private ListView  listViewUsuarios;
    private TextView  txtDadosSalvos;
    private DBHelper  dbHelper;
    private List<Usuario> listaUsuarios;

    // Overlay de sync
    private FrameLayout overlaySync;
    private ProgressBar progressSpinner, progressBar;
    private TextView    txtSyncTitulo, txtSyncContador;
    private TextView    txtSyncNovos, txtSyncDuplicados, txtSyncErros;

    // Poison pill
    private static final Usuario POISON_PILL = new Usuario(POISON_PILL_MATRICULA, POISON_PILL_MATRICULA);

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usuario);

        edtNome        = findViewById(R.id.edtNome);
        edtMat         = findViewById(R.id.edtMat);
        listViewUsuarios = findViewById(R.id.listViewUsuarios);
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

        // Matrícula: só números, máx 4 dígitos
        edtMat.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(4) });
        edtMat.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String limpo = s.toString().replaceAll("[^0-9]", "");
                if (!limpo.equals(s.toString())) {
                    edtMat.setText(limpo);
                    edtMat.setSelection(limpo.length());
                }
            }
        });

        carregarUsuarios();

        findViewById(R.id.btnSalvar).setOnClickListener(v -> salvarUsuario());
        findViewById(R.id.btnUpload).setOnClickListener(v -> sincronizarUsuarios());
        findViewById(R.id.btnExibir).setOnClickListener(v ->
                startActivity(new Intent(UsuarioActivity.this, ListaUsuariosActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarUsuarios();
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
                            "Inseridos: "  + inseridos + "\n" +
                            "Ignorados: "  + ignorados + "\n" +
                            "Erros: "      + erros
            );
            if (sucesso) carregarUsuarios();
        }, 1500);
    }

    // =========================================================================
    // Sync — mesmo padrão producer/consumer do LocalActivity
    // POST no mesmo endpoint → pega CODIGO_FUNCIONARIO e NOME_FUNCIONARIO
    // =========================================================================

    private void sincronizarUsuarios() {
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

        BlockingQueue<Usuario> fila        = new LinkedBlockingQueue<>(200);
        ExecutorService        producerExe = Executors.newSingleThreadExecutor();
        ExecutorService        dbExe       = Executors.newSingleThreadExecutor();
        ExecutorService        monitorExe  = Executors.newSingleThreadExecutor();

        // ── Consumer: salva no banco em transação única ───────────────────────
        dbExe.submit(() -> {
            SQLiteDatabase sqlDB = dbHelper.getWritableDatabase();
            sqlDB.beginTransaction();
            try {
                while (true) {
                    Usuario usuario;
                    try {
                        usuario = fila.poll(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    if (usuario == null) {
                        if (producerExe.isTerminated()) break;
                        continue;
                    }
                    // poison pill
                    if (POISON_PILL_MATRICULA.equals(usuario.getMatricula())) break;

                    try {
                        // Verifica duplicata dentro da transação — sem abrir nova conexão
                        if (dbHelper.existeMatriculaNaTransacao(sqlDB, usuario.getMatricula())) {
                            ignorados.incrementAndGet();
                        } else {
                            dbHelper.salvarUsuarioNaTransacao(sqlDB, usuario);
                            inseridos.incrementAndGet();
                        }

                        int processados = inseridos.get() + ignorados.get() + erros.get();
                        if (processados % 20 == 0) {
                            final int fi = inseridos.get(), fg = ignorados.get(),
                                    fe = erros.get(),    ft = total.get();
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

        // ── Producer: faz POST e parseia JSON ─────────────────────────────────
        producerExe.submit(() -> {
            try {
                String requestBody = "{" +
                        "\"CODCOLIGADA\":" + SettingsActivity.getCodColigada(this) + "," +
                        "\"CODFILIAL\":"   + SettingsActivity.getCodFilial(this)   + "," +
                        "\"ATIVO\":"       + SettingsActivity.getAtivo(this)       +
                        "}";

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
                    bw.write(requestBody);
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

                        // CODIGO_FUNCIONARIO → matricula (últimos 4 dígitos)
                        // "00000143" → "0143" / "00000442" → "0442"
                        // NOME_FUNCIONARIO   → nome
                        String codFull   = item.optString("CODIGO_FUNCIONARIO", "").trim();
                        String matricula = codFull.length() >= 4
                                ? codFull.substring(codFull.length() - 4)
                                : codFull;
                        String nome      = item.optString("NOME_FUNCIONARIO",   "").trim();

                        if (matricula.isEmpty() || nome.isEmpty()) {
                            erros.incrementAndGet();
                            Log.w(TAG, "Item " + i + " sem CODIGO/NOME — ignorado");
                            continue;
                        }

                        fila.put(new Usuario(nome, matricula));

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

        // ── Monitor: aguarda DB terminar e fecha overlay ───────────────────────
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

    private void salvarUsuario() {
        String nome = edtNome.getText().toString().trim();
        String mat  = edtMat.getText().toString().trim();

        if (nome.isEmpty() || mat.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dbHelper.existeMatricula(mat)) {
            Toast.makeText(this, "Esta matrícula já existe!", Toast.LENGTH_SHORT).show();
            return;
        }

        long id = dbHelper.salvarUsuario(new Usuario(nome, mat));
        if (id != -1) {
            Toast.makeText(this, "Usuário salvo com sucesso!", Toast.LENGTH_SHORT).show();
            edtNome.setText("");
            edtMat.setText("");
            carregarUsuarios();
        } else {
            Toast.makeText(this, "Erro ao salvar usuário", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void carregarUsuarios() {
        if (listViewUsuarios == null) return;
        listaUsuarios = dbHelper.listarUsuarios();
        List<String> nomes = new ArrayList<>();
        for (Usuario u : listaUsuarios)
            nomes.add(u.getNome() + " (" + u.getMatricula() + ")");
        listViewUsuarios.setAdapter(new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, nomes));
    }
}