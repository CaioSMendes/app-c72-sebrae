package com.example.uhf.activity;

import android.app.Activity;
import android.content.ContentValues;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.uhf.R;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.example.uhf.model.Usuario;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsuarioActivity extends Activity {

    private EditText edtNome, edtMat;
    private LinearLayout btnSalvar, btnUpload, btnExibir;
    private TextView txtDadosSalvos;
    private DBHelper dbHelper;

    private static final int PICK_FILE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usuario);

        edtNome = findViewById(R.id.edtNome);
        edtMat = findViewById(R.id.edtMat);
        btnSalvar = findViewById(R.id.btnSalvar);
        btnUpload = findViewById(R.id.btnUpload);
        btnExibir = findViewById(R.id.btnExibir);
        txtDadosSalvos = findViewById(R.id.txtDadosSalvos);

        dbHelper = new DBHelper(this);

        // ✅ LIMITA MATRÍCULA PARA 4 DÍGITOS NUMÉRICOS
        edtMat.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(4) // máximo 4 dígitos
        });

        edtMat.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String limpo = s.toString().replaceAll("[^0-9]", ""); // só números
                if (!limpo.equals(s.toString())) {
                    edtMat.setText(limpo);
                    edtMat.setSelection(limpo.length());
                }
            }
        });

        // ✅ Botão SALVAR
        btnSalvar.setOnClickListener(v -> {
            String nome = edtNome.getText().toString().trim();
            String mat = edtMat.getText().toString().trim();

            if (nome.isEmpty() || mat.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mat.length() != 4) {
                Toast.makeText(this, "A matrícula deve ter exatamente 4 dígitos.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (dbHelper.existeMatricula(mat)) {
                Toast.makeText(this, "Já existe um usuário com essa matrícula!", Toast.LENGTH_LONG).show();
                return;
            }

            dbHelper.salvarUsuario(new Usuario(nome, mat));
            txtDadosSalvos.setText("Usuário salvo: " + nome);
            Toast.makeText(this, "Usuário salvo com sucesso!", Toast.LENGTH_SHORT).show();

            edtNome.setText("");
            edtMat.setText("");
        });

        // ✅ Botão UPLOAD (.xlsx)
        btnUpload.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            startActivityForResult(Intent.createChooser(intent, "Selecione o arquivo Excel (.xlsx)"), PICK_FILE_REQUEST);
        });

        // ✅ Botão EXIBIR
        btnExibir.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListaUsuariosActivity.class);
            startActivity(intent);
        });
    }

    // ✅ IMPORTAÇÃO EXCEL
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri == null) return;

            txtDadosSalvos.setText("📂 Lendo arquivo... aguarde...");
            Toast.makeText(this, "Importando usuários, aguarde...", Toast.LENGTH_SHORT).show();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(() -> {
                int countInseridos = 0;
                int countDuplicados = 0;
                int countErros = 0;

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();

                try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
                     Workbook workbook = new XSSFWorkbook(inputStream)) {

                    Sheet sheet = workbook.getSheetAt(0);
                    int totalLinhas = sheet.getLastRowNum();

                    for (int i = 1; i <= totalLinhas; i++) {
                        try {
                            Row row = sheet.getRow(i);
                            if (row == null) continue;

                            String nome = row.getCell(0).getStringCellValue().trim();
                            String matricula = row.getCell(1).getStringCellValue().trim();

                            if (nome.isEmpty() || matricula.isEmpty()) continue;

                            // ✅ aplica regra também na importação
                            matricula = matricula.replaceAll("[^0-9]", "");
                            if (matricula.length() != 4) {
                                countErros++;
                                continue;
                            }

                            if (dbHelper.existeMatricula(matricula)) {
                                countDuplicados++;
                            } else {
                                ContentValues values = new ContentValues();
                                values.put("nome", nome);
                                values.put("matricula", matricula);
                                db.insert("usuarios", null, values);
                                countInseridos++;
                            }

                        } catch (Exception linhaErro) {
                            countErros++;
                            Log.e("IMPORT_USUARIOS", "Erro na linha " + i + ": " + linhaErro.getMessage());
                        }
                    }

                    db.setTransactionSuccessful();

                    final int totalInseridos = countInseridos;
                    final int totalDuplicados = countDuplicados;
                    final int totalErros = countErros;

                    handler.post(() -> {
                        txtDadosSalvos.setText("✅ Importação concluída!\n" +
                                "Inseridos: " + totalInseridos +
                                "\nDuplicados: " + totalDuplicados +
                                "\nIgnorados (inválidos): " + totalErros);
                        Toast.makeText(this, "Usuários importados com sucesso!", Toast.LENGTH_LONG).show();
                    });

                } catch (Exception e) {
                    handler.post(() -> {
                        txtDadosSalvos.setText("❌ Erro geral: " + e.getMessage());
                        Toast.makeText(this, "Erro ao importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                } finally {
                    db.endTransaction();
                    db.close();
                    executor.shutdown();
                }
            });
        }
    }
}