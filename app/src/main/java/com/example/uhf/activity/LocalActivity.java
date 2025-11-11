package com.example.uhf.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_XLSX = 1001;

    private EditText edtLocal, edtCodLocal, edtCodFilial;
    private ListView listViewLocais;
    private TextView txtDadosSalvos;
    private DBHelper dbHelper;
    private List<Local> listaLocais;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local);

        edtLocal = findViewById(R.id.edtlocal);
        edtCodLocal = findViewById(R.id.edtCodLoc);
        edtCodFilial = findViewById(R.id.edtCodFilial);
        listViewLocais = findViewById(R.id.listViewLocais);
        txtDadosSalvos = findViewById(R.id.txtDadosSalvos);

        dbHelper = new DBHelper(this);

        // Limitar e aceitar somente números
        configurarEditTextNumerico(edtCodLocal, 4);
        configurarEditTextNumerico(edtCodFilial, 3);

        carregarLocais();

        findViewById(R.id.btnSalvar).setOnClickListener(v -> salvarLocal());
        findViewById(R.id.btnExibir).setOnClickListener(v -> {
            Intent intent = new Intent(LocalActivity.this, ListaLocaisActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.btnUpload).setOnClickListener(v -> abrirSeletorXLSX());

        if (listViewLocais != null) {
            listViewLocais.setOnItemClickListener((parent, view, position, id) -> {
                Local localSelecionado = listaLocais.get(position);
                Intent intent = new Intent(LocalActivity.this, DetalheLocalActivity.class);
                intent.putExtra("localId", localSelecionado.getId());
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLocais();
    }

    private void configurarEditTextNumerico(EditText edt, int maxLength) {
        edt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
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

    private void salvarLocal() {
        String nome = edtLocal.getText().toString().trim();
        String codLocal = edtCodLocal.getText().toString().trim();
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

    private void carregarLocais() {
        if (listViewLocais == null) return;

        listaLocais = dbHelper.listarLocais();
        List<String> nomesLocais = new ArrayList<>();
        for (Local l : listaLocais) {
            nomesLocais.add(l.getLocalNome() + " (" + l.getCodigoLocal() + ")");
        }

        listViewLocais.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, nomesLocais));
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case STRING: return cell.getStringCellValue().trim();
                    case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
                    case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                    default: return "";
                }
            default: return "";
        }
    }

    private void abrirSeletorXLSX() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Selecione um arquivo Excel"), REQUEST_CODE_XLSX);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_XLSX && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importarXLSX(uri);
        }
    }

    private void importarXLSX(Uri uri) {
        Toast.makeText(this, "Iniciando importação, aguarde...", Toast.LENGTH_SHORT).show();

        Handler handler = new Handler(Looper.getMainLooper());
        ExecutorService executorMain = Executors.newSingleThreadExecutor();

        executorMain.execute(() -> {
            AtomicInteger importados = new AtomicInteger(0);
            AtomicInteger duplicados = new AtomicInteger(0);
            AtomicInteger erros = new AtomicInteger(0);

            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

                XSSFSheet sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(0);
                if (header == null) throw new Exception("Cabeçalho não encontrado!");

                int colLocalNome = -1, colFilial = -1, colLocal = -1;
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    if (header.getCell(c) == null) continue;
                    String title = getCellValue(header.getCell(c)).trim();
                    if (title.equalsIgnoreCase("Local_Nome")) colLocalNome = c;
                    if (title.equalsIgnoreCase("Código Filial")) colFilial = c;
                    if (title.equalsIgnoreCase("Código Local")) colLocal = c;
                }

                if (colLocalNome == -1 || colFilial == -1 || colLocal == -1)
                    throw new Exception("As colunas Local_Nome / Código Filial / Código Local não foram encontradas!");

                final int finalColLocalNome = colLocalNome;
                final int finalColFilial = colFilial;
                final int finalColLocal = colLocal;

                int totalLinhas = sheet.getLastRowNum();
                int bloco = 100;

                ExecutorService executorParalelo = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                for (int inicio = 1; inicio <= totalLinhas; inicio += bloco) {
                    int fim = Math.min(inicio + bloco - 1, totalLinhas);
                    int finalInicio = inicio;
                    int finalFim = fim;

                    executorParalelo.submit(() -> {
                        for (int i = finalInicio; i <= finalFim; i++) {
                            try {
                                Row row = sheet.getRow(i);
                                if (row == null) continue;

                                String nome = getCellValue(row.getCell(finalColLocalNome)).trim();
                                String codFilialRaw = getCellValue(row.getCell(finalColFilial)).replaceAll("[^0-9]", "");
                                String codLocalRaw = getCellValue(row.getCell(finalColLocal)).replaceAll("[^0-9]", "");

                                String codFilial = codFilialRaw.isEmpty() ? "" : String.format("%03d", Integer.parseInt(codFilialRaw));
                                String codLocalStr = codLocalRaw.isEmpty() ? "" : String.format("%04d", Integer.parseInt(codLocalRaw));

                                if (codFilial.length() != 3 || codLocalStr.length() != 4) {
                                    erros.incrementAndGet();
                                    Log.e("IMPORT_XLSX", "Linha " + i + " ignorada: códigos inválidos - Filial: " + codFilial + ", Local: " + codLocalStr);
                                    continue;
                                }

                                if (dbHelper.existeCodigoLocal(codLocalStr)) {
                                    duplicados.incrementAndGet();
                                    Log.w("IMPORT_XLSX", "Linha " + i + " duplicada: Local " + codLocalStr);
                                    continue;
                                }

                                Local local = new Local(0, nome, codFilial, codLocalStr);
                                dbHelper.salvarLocal(local);
                                importados.incrementAndGet();

                            } catch (Exception ex) {
                                erros.incrementAndGet();
                                Log.e("IMPORT_XLSX", "Erro linha " + i + ": " + ex.getMessage(), ex);
                            }

                            // Atualiza UI a cada 10 linhas
                            if (i % 10 == 0 || i == finalFim) {
                                int fImportados = importados.get();
                                int fDuplicados = duplicados.get();
                                int fErros = erros.get();
                                int processadas = i;

                                handler.post(() -> txtDadosSalvos.setText(
                                        "Processadas: " + processadas + " / " + totalLinhas +
                                                "\nImportados: " + fImportados +
                                                "\nDuplicados: " + fDuplicados +
                                                "\nErros: " + fErros
                                ));
                            }
                        }
                    });
                }

                executorParalelo.shutdown();
                while (!executorParalelo.isTerminated()) Thread.sleep(100);

                handler.post(() -> Toast.makeText(this, "✅ Importação concluída!", Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e("IMPORT_XLSX", "Erro geral na importação: " + e.getMessage(), e);
                handler.post(() -> Toast.makeText(this, "❌ Erro geral ao importar: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                executorMain.shutdown();
            }
        });
    }
}
