package com.example.uhf.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

        // 👉 Botão SALVAR (salva manualmente)
        btnSalvar.setOnClickListener(v -> {
            String nome = edtNome.getText().toString().trim();
            String mat = edtMat.getText().toString().trim();

            if (nome.isEmpty() || mat.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 🔍 Verifica se matrícula já existe
            if (dbHelper.existeMatricula(mat)) {
                Toast.makeText(this, "Já existe um usuário com essa matrícula!", Toast.LENGTH_LONG).show();
                return;
            }

            // 💾 Salva novo usuário
            dbHelper.salvarUsuario(new Usuario(nome, mat));
            txtDadosSalvos.setText("Usuário salvo: " + nome);
            Toast.makeText(this, "Usuário salvo com sucesso!", Toast.LENGTH_SHORT).show();

            edtNome.setText("");
            edtMat.setText("");
        });

        // 👉 Botão UPLOAD (.xlsx)
        btnUpload.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            startActivityForResult(Intent.createChooser(intent, "Selecione o arquivo Excel (.xlsx)"), PICK_FILE_REQUEST);
        });

        // 👉 Botão EXIBIR (abre lista de usuários)
        btnExibir.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListaUsuariosActivity.class);
            startActivity(intent);
        });
    }

    // 🔁 Resultado da seleção de arquivo Excel
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                Workbook workbook = new XSSFWorkbook(inputStream);
                Sheet sheet = workbook.getSheetAt(0);

                int count = 0, duplicados = 0;

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) {
                        String nome = row.getCell(0).getStringCellValue();
                        String matricula = row.getCell(1).getStringCellValue();

                        if (dbHelper.existeMatricula(matricula)) {
                            duplicados++;
                        } else {
                            dbHelper.salvarUsuario(new Usuario(nome, matricula));
                            count++;
                        }
                    }
                }

                workbook.close();
                txtDadosSalvos.setText("Importação: " + count + " inseridos, " + duplicados + " ignorados (duplicados).");
                Toast.makeText(this, "Importação concluída!", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                e.printStackTrace();
                txtDadosSalvos.setText("Erro ao importar: " + e.getMessage());
                Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}