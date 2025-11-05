package com.example.uhf.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Local;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_XLSX = 1001;

    private EditText edtLocal, edtCodLocal, edtCodFilial;
    private ListView listViewLocais;
    private DBHelper dbHelper;
    private List<Local> listaLocais;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local);

        // Inicializa Views
        edtLocal = findViewById(R.id.edtlocal);
        edtCodLocal = findViewById(R.id.edtCodLoc);
        edtCodFilial = findViewById(R.id.edtCodFilial);

        listViewLocais = findViewById(R.id.listViewLocais);

        dbHelper = new DBHelper(this);

        // Carrega lista ao abrir a activity
        carregarLocais();

        // Botão Salvar
        findViewById(R.id.btnSalvar).setOnClickListener(v -> salvarLocal());

        // Botão Exibir → abre a lista completa em outra Activity
        findViewById(R.id.btnExibir).setOnClickListener(v -> {
            Intent intent = new Intent(LocalActivity.this, ListaLocaisActivity.class);
            startActivity(intent);
        });

        // Botão Carregar XLSX
        findViewById(R.id.btnUpload).setOnClickListener(v -> abrirSeletorXLSX());

        // Clique em item da lista (se ListView existir)
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

    // ================= Funções =================

    private void salvarLocal() {
        String nome = edtLocal.getText().toString().trim();
        String codLocal = edtCodLocal.getText().toString().trim();
        String codFilial = edtCodFilial.getText().toString().trim();

        if (nome.isEmpty() || codLocal.isEmpty() || codFilial.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
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

    // ================= XLSX =================

    private void abrirSeletorXLSX() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // aceita todos os arquivos
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Selecione um arquivo Excel"), REQUEST_CODE_XLSX);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_XLSX && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importarXLSX(uri);
            }
        }
    }

    private void importarXLSX(Uri uri) {
        // Mostra status inicial
        Toast.makeText(this, "Iniciando importação, aguarde...", Toast.LENGTH_SHORT).show();

        // Executor em thread separada
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            int importados = 0;
            int duplicados = 0;
            int erros = 0;

            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

                XSSFSheet sheet = workbook.getSheetAt(0);
                int totalLinhas = sheet.getLastRowNum();

                for (int i = 1; i <= totalLinhas; i++) { // pula cabeçalho
                    try {
                        Row row = sheet.getRow(i);
                        if (row == null) continue;

                        // ⚙️ Lê colunas exatamente conforme o cabeçalho
                        String nome = row.getCell(0).getStringCellValue().trim();          // Local_Nome
                        String codFilial = row.getCell(1).getStringCellValue().trim();     // Código Filial
                        String codLocal = row.getCell(2).getStringCellValue().trim();      // Código Local

                        // 🔍 Verifica duplicado
                        if (dbHelper.existeCodigoLocal(codLocal)) {
                            duplicados++;
                            continue;
                        }

                        // 💾 Salva no banco
                        Local local = new Local(0, nome, codFilial, codLocal);
                        dbHelper.salvarLocal(local);
                        importados++;

                        // 🔄 Atualiza a UI a cada 100 registros ou no final
                        if (i % 100 == 0 || i == totalLinhas) {
                            final int finalI = i;
                            final int finalImportados = importados;
                            final int finalDuplicados = duplicados;
                            final int finalErros = erros;

                            handler.post(() -> Toast.makeText(
                                    this,
                                    "Processadas " + finalI + " linhas...\n" +
                                            "Importados: " + finalImportados +
                                            "\nDuplicados: " + finalDuplicados +
                                            "\nErros: " + finalErros,
                                    Toast.LENGTH_SHORT
                            ).show());
                        }

                    } catch (Exception linhaEx) {
                        erros++;
                        // Loga o erro, mas continua o loop
                        Log.e("IMPORT_XLSX", "Erro na linha " + i + ": " + linhaEx.getMessage());
                    }
                }

                // ✅ Finaliza a execução na thread principal
                final int totalImportados = importados;
                final int totalDuplicados = duplicados;
                final int totalErros = erros;

                handler.post(() -> {
                    carregarLocais();
                    Toast.makeText(
                            this,
                            "✅ Importação concluída!\n" +
                                    "Importados: " + totalImportados +
                                    "\nDuplicados: " + totalDuplicados +
                                    "\nErros: " + totalErros,
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(
                        this,
                        "❌ Erro geral ao importar: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            } finally {
                executor.shutdown();
            }
        });
    }
}