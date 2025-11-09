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

        edtLocal = findViewById(R.id.edtlocal);
        edtCodLocal = findViewById(R.id.edtCodLoc);
        edtCodFilial = findViewById(R.id.edtCodFilial);
        listViewLocais = findViewById(R.id.listViewLocais);

        dbHelper = new DBHelper(this);

        // ✅ CÓDIGO LOCAL — somente números e máximo 4 dígitos
        edtCodLocal.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(4)
        });

        edtCodLocal.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String limpo = s.toString().replaceAll("[^0-9]", "");
                if (!limpo.equals(s.toString())) {
                    edtCodLocal.setText(limpo);
                    edtCodLocal.setSelection(limpo.length());
                }
            }
        });

        // ✅ CÓDIGO FILIAL — somente números e máximo 3 dígitos
        edtCodFilial.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(3)
        });

        edtCodFilial.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String limpo = s.toString().replaceAll("[^0-9]", "");
                if (!limpo.equals(s.toString())) {
                    edtCodFilial.setText(limpo);
                    edtCodFilial.setSelection(limpo.length());
                }
            }
        });

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

    // ==================== SALVAR ====================

    private void salvarLocal() {
        String nome = edtLocal.getText().toString().trim();
        String codLocal = edtCodLocal.getText().toString().trim();
        String codFilial = edtCodFilial.getText().toString().trim();

        if (nome.isEmpty() || codLocal.isEmpty() || codFilial.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ validações dos tamanhos
        if (codLocal.length() != 4) {
            Toast.makeText(this, "O Código Local deve ter exatamente 4 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (codFilial.length() != 3) {
            Toast.makeText(this, "O Código Filial deve ter exatamente 3 dígitos", Toast.LENGTH_SHORT).show();
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

    // ==================== LISTAR ====================

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

    // ==================== XLSX ====================

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
            if (uri != null) {
                importarXLSX(uri);
            }
        }
    }

    private void importarXLSX(Uri uri) {
        Toast.makeText(this, "Iniciando importação, aguarde...", Toast.LENGTH_SHORT).show();

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

                for (int i = 1; i <= totalLinhas; i++) {
                    try {
                        Row row = sheet.getRow(i);
                        if (row == null) continue;

                        String nome = row.getCell(0).getStringCellValue().trim();
                        String codFilial = row.getCell(1).getStringCellValue().trim();
                        String codLocal = row.getCell(2).getStringCellValue().trim();

                        // ✅ limpa caracteres inválidos
                        codFilial = codFilial.replaceAll("[^0-9]", "");
                        codLocal = codLocal.replaceAll("[^0-9]", "");

                        // ✅ valida tamanhos
                        if (codFilial.length() != 3 || codLocal.length() != 4) {
                            erros++;
                            continue;
                        }

                        if (dbHelper.existeCodigoLocal(codLocal)) {
                            duplicados++;
                            continue;
                        }

                        Local local = new Local(0, nome, codFilial, codLocal);
                        dbHelper.salvarLocal(local);
                        importados++;

                    } catch (Exception ex) {
                        erros++;
                        Log.e("IMPORT_XLSX", "Erro linha " + i + ": " + ex.getMessage());
                    }
                }

                int fImportados = importados;
                int fDuplicados = duplicados;
                int fErros = erros;

                handler.post(() -> {
                    carregarLocais();
                    Toast.makeText(
                            this,
                            "✅ Importação concluída!\nImportados: " + fImportados +
                                    "\nDuplicados: " + fDuplicados +
                                    "\nErros: " + fErros,
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception e) {
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