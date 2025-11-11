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
import java.util.Set;
import java.util.HashSet;
import androidx.annotation.Nullable;

import com.example.uhf.R;
import com.example.uhf.model.Usuario;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

        // Limita matrícula a 4 dígitos numéricos
        edtMat.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
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

        btnSalvar.setOnClickListener(v -> salvarUsuario());
        btnUpload.setOnClickListener(v -> abrirSeletorExcel());
        btnExibir.setOnClickListener(v -> startActivity(new Intent(this, ListaUsuariosActivity.class)));
    }

    private void salvarUsuario() {
        String nome = edtNome.getText().toString().trim();
        String mat = edtMat.getText().toString().trim();

        if (nome.isEmpty() || mat.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mat.length() != 4) {
            Toast.makeText(this, "A matrícula deve ter 4 dígitos.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dbHelper.existeMatricula(mat)) {
            Toast.makeText(this, "Já existe um usuário com esta matrícula!", Toast.LENGTH_LONG).show();
            return;
        }

        dbHelper.salvarUsuario(new Usuario(nome, mat));
        txtDadosSalvos.setText("Usuário salvo: " + nome);
        Toast.makeText(this, "Usuário salvo com sucesso!", Toast.LENGTH_SHORT).show();
        edtNome.setText("");
        edtMat.setText("");
    }

    private void abrirSeletorExcel() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        startActivityForResult(Intent.createChooser(intent, "Selecione o arquivo Excel (.xlsx)"), PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) importarXLSX(fileUri);
        }
    }

    private void importarXLSX(Uri uri) {
        Toast.makeText(this, "📂 Lendo arquivo... aguarde...", Toast.LENGTH_SHORT).show();
        txtDadosSalvos.setText("📂 Lendo arquivo... aguarde...");

        Handler handler = new Handler(Looper.getMainLooper());
        ExecutorService executorMain = Executors.newSingleThreadExecutor();

        executorMain.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

                XSSFSheet sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(0);
                if (header == null) throw new Exception("Cabeçalho inválido!");

                int colNome = -1, colMat = -1;
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    if (header.getCell(c) == null) continue;
                    String title = getCellValue(header.getCell(c));
                    if (title.equalsIgnoreCase("Responsável_Nome")) colNome = c;
                    if (title.equalsIgnoreCase("Chapa Funcionário")) colMat = c;
                }
                if (colNome == -1 || colMat == -1) throw new Exception("Colunas necessárias não encontradas!");

                final int finalColNome = colNome;
                final int finalColMat = colMat;

                int totalLinhas = sheet.getLastRowNum();
                int bloco = 100;

                AtomicInteger countInseridos = new AtomicInteger(0);
                AtomicInteger countDuplicados = new AtomicInteger(0);
                AtomicInteger countErros = new AtomicInteger(0);

                // Carregar todas matrículas existentes antes
                Set<String> matriculasExistentes = dbHelper.getTodasMatriculas();

                ExecutorService executorParalelo = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                for (int inicio = 1; inicio <= totalLinhas; inicio += bloco) {
                    int fim = Math.min(inicio + bloco - 1, totalLinhas);
                    int finalInicio = inicio;
                    int finalFim = fim;

                    executorParalelo.submit(() -> {
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        db.beginTransaction();
                        try {
                            for (int i = finalInicio; i <= finalFim; i++) {
                                try {
                                    Row row = sheet.getRow(i);
                                    if (row == null) continue;

                                    String nome = getCellValue(row.getCell(finalColNome)).trim();
                                    String matRaw = getCellValue(row.getCell(finalColMat)).replaceAll("[^0-9]", "");
                                    if (nome.isEmpty() || matRaw.isEmpty()) {
                                        countErros.incrementAndGet();
                                        continue;
                                    }

                                    String mat = String.format("%04d", Integer.parseInt(matRaw));

                                    synchronized (matriculasExistentes) {
                                        if (matriculasExistentes.contains(mat)) {
                                            countDuplicados.incrementAndGet();
                                            continue;
                                        }
                                        matriculasExistentes.add(mat); // marca como inserida
                                    }

                                    ContentValues values = new ContentValues();
                                    values.put("nome", nome);
                                    values.put("matricula", mat);
                                    db.insert("usuarios", null, values);
                                    countInseridos.incrementAndGet();

                                } catch (Exception eLinha) {
                                    countErros.incrementAndGet();
                                    Log.e("IMPORT_USUARIO", "Erro linha " + i + ": " + eLinha.getMessage(), eLinha);
                                }
                            }
                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                            db.close();
                        }

                        handler.post(() -> txtDadosSalvos.setText(
                                "Processadas: " + finalFim + " / " + totalLinhas +
                                        "\nInseridos: " + countInseridos.get() +
                                        "\nDuplicados: " + countDuplicados.get() +
                                        "\nErros: " + countErros.get()
                        ));
                    });
                }

                executorParalelo.shutdown();
                while (!executorParalelo.isTerminated()) Thread.sleep(100);

                handler.post(() -> Toast.makeText(this, "✅ Importação concluída!", Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e("IMPORT_USUARIO", "Erro geral: " + e.getMessage(), e);
                handler.post(() -> {
                    txtDadosSalvos.setText("❌ Erro: " + e.getMessage());
                    Toast.makeText(this, "Erro ao importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                executorMain.shutdown();
            }
        });
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
}
