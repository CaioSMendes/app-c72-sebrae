package com.example.uhf.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;

public class PatrimonioActivity extends Activity {

    private EditText edtPat, edtDesc, edtCod;
    private TextView txtStatus;
    private LinearLayout btnSalvar, btnExibir, btnUpload;
    private DBHelper db;
    private static final int PICK_FILE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrimonio);

        db = new DBHelper(this);

        edtPat = findViewById(R.id.edtPat);
        edtDesc = findViewById(R.id.edtDesc);
        edtCod = findViewById(R.id.edtCod);
        txtStatus = findViewById(R.id.txtDadosSalvos);
        btnSalvar = findViewById(R.id.btnSalvar);
        btnExibir = findViewById(R.id.btnExibir);
        btnUpload = findViewById(R.id.btnUpload);

        btnSalvar.setOnClickListener(v -> salvarPatrimonio());
        btnExibir.setOnClickListener(v -> abrirLista());
        btnUpload.setOnClickListener(v -> importarPlanilha());
    }

    private void salvarPatrimonio() {
        String pat = edtPat.getText().toString().trim();
        String desc = edtDesc.getText().toString().trim();
        String cod = edtCod.getText().toString().trim();

        if (pat.isEmpty() || desc.isEmpty() || cod.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sucesso = db.inserirPatrimonio(pat, desc, cod);

        if (sucesso) {
            txtStatus.setText("Ativo salvo com sucesso!");
            edtPat.setText("");
            edtDesc.setText("");
            edtCod.setText("");
        } else {
            txtStatus.setText("Erro: código de barra já existe!");
        }
    }

    private void abrirLista() {
        startActivity(new Intent(this, ListaPatrimoniosActivity.class));
    }

    private void importarPlanilha() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        startActivityForResult(Intent.createChooser(intent, "Selecione o arquivo Excel (.xlsx)"), PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                Workbook workbook = new XSSFWorkbook(inputStream);
                Sheet sheet = workbook.getSheetAt(0);

                int countSalvos = 0, countDuplicados = 0;

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) {
                        String patrimonio = row.getCell(0).getStringCellValue().trim();
                        String descricao = row.getCell(1).getStringCellValue().trim();
                        String codigo = row.getCell(2).getStringCellValue().trim();

                        if (!db.inserirPatrimonio(patrimonio, descricao, codigo)) {
                            countDuplicados++;
                        } else {
                            countSalvos++;
                        }
                    }
                }

                workbook.close();
                inputStream.close();
                txtStatus.setText("Importação concluída!\nSalvos: " + countSalvos + "\nDuplicados: " + countDuplicados);
                Toast.makeText(this, "Importação concluída!", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                e.printStackTrace();
                txtStatus.setText("Erro ao importar arquivo: " + e.getMessage());
            }
        }
    }
}