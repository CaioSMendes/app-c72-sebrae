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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario);

        editCodigoFilial = findViewById(R.id.editCodigoFilial);
        editCodigoLocal = findViewById(R.id.editCodigoLocal);
        editChapaFuncionario = findViewById(R.id.editChapaFuncionario);
        buttonPesquisar = findViewById(R.id.buttonPesquisar);

        dbHelper = new DBHelper(this); // Usa o DBHelper do mesmo pacote

        buttonPesquisar.setOnClickListener(v -> verificarCampos());
    }

    private void verificarCampos() {
        String codigoFilial = editCodigoFilial.getText().toString().trim();
        String codigoLocal = editCodigoLocal.getText().toString().trim();
        String chapaFuncionario = editChapaFuncionario.getText().toString().trim();

        if (codigoFilial.isEmpty() || codigoLocal.isEmpty() || chapaFuncionario.isEmpty()) {
            mostrarAlerta("Preencha todos os campos!");
            return;
        }

        if (codigoFilial.length() > 3) {
            mostrarAlerta("O código da filial deve ter no máximo 3 dígitos.");
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Verifica se o local existe
        Cursor cursorLocal = db.rawQuery(
                "SELECT * FROM locais WHERE codigo_local = ?",
                new String[]{codigoLocal}
        );
        boolean localExiste = cursorLocal.moveToFirst();
        cursorLocal.close();

        // Verifica se o funcionário existe
        Cursor cursorUsuario = db.rawQuery(
                "SELECT * FROM usuarios WHERE matricula = ?",
                new String[]{chapaFuncionario}
        );
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

        // Ambos existem → ir para próxima Activity
        Intent intent = new Intent(this, ConsultaTagActivity.class);
        intent.putExtra("codigoFilial", codigoFilial);
        intent.putExtra("codigoLocal", codigoLocal);
        intent.putExtra("chapaFuncionario", chapaFuncionario);
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