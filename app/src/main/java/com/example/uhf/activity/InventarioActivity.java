package com.example.uhf.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class InventarioActivity extends AppCompatActivity {

    private EditText editCodigoFilial, editCodigoLocal, editChapaFuncionario;
    private LinearLayout buttonPesquisar;
    private DBHelper dbHelper;

    private String tipoLeitura;    // "RFID" ou "CODBARRA"
    private String tipoInventario; // "LIVRE" | "LOCAL" | "CATEGORIA"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario);

        editCodigoFilial     = findViewById(R.id.editCodigoFilial);
        editCodigoLocal      = findViewById(R.id.editCodigoLocal);
        editChapaFuncionario = findViewById(R.id.editChapaFuncionario);
        buttonPesquisar      = findViewById(R.id.buttonPesquisar);

        dbHelper = new DBHelper(this);

        tipoLeitura    = getIntent().getStringExtra("tipoLeitura");
        tipoInventario = getIntent().getStringExtra("tipoInventario");
        if (tipoInventario == null) tipoInventario = "LIVRE"; // fallback seguro

        buttonPesquisar.setOnClickListener(v -> verificarCampos());
    }

    private void verificarCampos() {
        String codigoFilial     = editCodigoFilial.getText().toString().trim();
        String codigoLocal      = editCodigoLocal.getText().toString().trim();
        String chapaFuncionario = editChapaFuncionario.getText().toString().trim();

        // ── Validações de preenchimento ───────────────────────────────────
        if (codigoFilial.isEmpty() || codigoLocal.isEmpty() || chapaFuncionario.isEmpty()) {
            mostrarAlerta("Preencha todos os campos!");
            return;
        }

        if (codigoFilial.length() > 3) {
            mostrarAlerta("O código da filial deve ter no máximo 3 dígitos.");
            return;
        }

        // ── Validações no banco ───────────────────────────────────────────
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursorLocal = db.rawQuery(
                "SELECT * FROM locais WHERE codigo_local = ?",
                new String[]{ codigoLocal });
        boolean localExiste = cursorLocal.moveToFirst();
        cursorLocal.close();

        Cursor cursorUsuario = db.rawQuery(
                "SELECT * FROM usuarios WHERE matricula = ?",
                new String[]{ chapaFuncionario });
        boolean funcionarioExiste = cursorUsuario.moveToFirst();
        cursorUsuario.close();

        db.close();

        if (!localExiste) {
            mostrarAlerta("Código Local não encontrado!");
            return;
        }

        if (!funcionarioExiste) {
            mostrarAlerta("Funcionário não encontrado!");
            return;
        }

        // ── Roteamento ────────────────────────────────────────────────────
        Intent intent;

        if ("CODBARRA".equals(tipoLeitura)) {
            // Código de barras sempre vai para InventarioCodBarraActivity
            intent = new Intent(this, InventarioCodBarraActivity.class);

        } else {
            // RFID → escolhe o modo de inventário
            switch (tipoInventario) {
                case "LOCAL":
                    intent = new Intent(this, InventarioLocalActivity.class);
                    break;
                case "CATEGORIA":
                    intent = new Intent(this, InventarioCategoriaActivity.class);
                    break;
                default: // "LIVRE" → comportamento original
                    intent = new Intent(this, InventarioLivreActivity.class);
                    break;
            }
        }

        // ── Repassa os campos para a Activity de destino ──────────────────
        intent.putExtra("codigoFilial",     codigoFilial);
        intent.putExtra("codigoLocal",      codigoLocal);
        intent.putExtra("chapaFuncionario", chapaFuncionario);
        intent.putExtra("tipoLeitura",      tipoLeitura);
        intent.putExtra("tipoInventario",   tipoInventario);

        startActivity(intent);
    }

    private void mostrarAlerta(String mensagem) {
        new AlertDialog.Builder(this)
                .setTitle("Atenção")
                .setMessage(mensagem)
                .setPositiveButton("OK", null)
                .show();
    }
}