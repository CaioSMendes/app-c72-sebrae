package com.example.uhf.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Local;

public class DetalheLocalActivity extends AppCompatActivity {

    private TextView txtNome;
    private TextView txtCodigoLocal;
    private TextView txtCodigoFilial;

    private DBHelper dbHelper;
    private Local    local;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhe_local);

        dbHelper = new DBHelper(this);

        bindViews();

        int localId = getIntent().getIntExtra("localId", -1);
        if (localId == -1) {
            Toast.makeText(this, "Local não encontrado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        local = dbHelper.buscarLocalPorId(localId);
        if (local == null) {
            Toast.makeText(this, "Local não encontrado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        preencherCampos();

        findViewById(R.id.btnEdit).setOnClickListener(v -> abrirDialogEdicao());
        findViewById(R.id.btnTrash).setOnClickListener(v -> confirmarExclusao());
    }

    // -------------------------------------------------------------------------
    // Bind
    // -------------------------------------------------------------------------

    private void bindViews() {
        txtNome         = findViewById(R.id.txtPatrimonio);   // reutiliza view existente
        txtCodigoLocal  = findViewById(R.id.txtCodigoBarra);  // reutiliza view existente
        txtCodigoFilial = findViewById(R.id.txtDescricao);    // reutiliza view existente
    }

    // -------------------------------------------------------------------------
    // Preencher tela
    // -------------------------------------------------------------------------

    private void preencherCampos() {
        txtNome.setText(safe(local.getLocalNome()));
        txtCodigoLocal.setText("Código Local: "  + safe(local.getCodigoLocal()));
        txtCodigoFilial.setText("Código Filial: " + safe(local.getCodigoFilial()));
    }

    // -------------------------------------------------------------------------
    // Dialog de edição
    // -------------------------------------------------------------------------

    private void abrirDialogEdicao() {
        final EditText edtNome      = new EditText(this);
        final EditText edtCodFilial = new EditText(this);
        final EditText edtCodLocal  = new EditText(this);

        edtNome.setHint("Nome do Local");
        edtCodFilial.setHint("Código Filial");
        edtCodLocal.setHint("Código Local");

        edtNome.setText(local.getLocalNome());
        edtCodFilial.setText(local.getCodigoFilial());
        edtCodLocal.setText(local.getCodigoLocal());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);
        layout.addView(edtNome);
        layout.addView(edtCodFilial);
        layout.addView(edtCodLocal);

        new AlertDialog.Builder(this)
                .setTitle("Editar Local")
                .setView(layout)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String novoNome      = edtNome.getText().toString().trim();
                    String novoCodFilial = edtCodFilial.getText().toString().trim();
                    String novoCodLocal  = edtCodLocal.getText().toString().trim();

                    if (novoNome.isEmpty() || novoCodFilial.isEmpty() || novoCodLocal.isEmpty()) {
                        Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    local.setLocalNome(novoNome);
                    local.setCodigoFilial(novoCodFilial);
                    local.setCodigoLocal(novoCodLocal);

                    boolean atualizado = dbHelper.atualizarLocal(local.getId(), local);
                    if (atualizado) {
                        preencherCampos();
                        Toast.makeText(this, "Local atualizado com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Erro ao atualizar local", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Excluir
    // -------------------------------------------------------------------------

    private void confirmarExclusao() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir Local")
                .setMessage("Tem certeza que deseja excluir \"" + local.getLocalNome() + "\"?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    boolean ok = dbHelper.excluirLocal(local.getId());
                    if (ok) {
                        Toast.makeText(this, "Local excluído com sucesso.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Erro ao excluir.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Util
    // -------------------------------------------------------------------------

    private String safe(String value) {
        return (value != null && !value.isEmpty()) ? value : "—";
    }
}