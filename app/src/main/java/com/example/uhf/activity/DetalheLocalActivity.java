package com.example.uhf.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Local;

public class DetalheLocalActivity extends AppCompatActivity {

    private TextView txtNomeLocal, txtCodigoLocal, txtCodigoFilial;
    private ImageView imgLocal;
    private LinearLayout btnEdit, btnTrash;
    private DBHelper dbHelper;
    private Local local; // objeto local atual

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhe_local);

        dbHelper = new DBHelper(this);

        // Referências
        txtNomeLocal = findViewById(R.id.txtPatrimonio);
        txtCodigoLocal = findViewById(R.id.txtCodigoBarra);
        txtCodigoFilial = findViewById(R.id.txtDescricao);
        imgLocal = findViewById(R.id.imgPatrimonio);
        btnEdit = findViewById(R.id.btnEdit);
        btnTrash = findViewById(R.id.btnTrash);

        // Recebe ID do Local passado na Intent
        int localId = getIntent().getIntExtra("localId", -1);
        if (localId != -1) {
            local = dbHelper.buscarLocalPorId(localId);
            if (local != null) {
                // Exibe dados
                txtNomeLocal.setText("Local Nome: " + local.getLocalNome());
                txtCodigoLocal.setText("Código Local: " + local.getCodigoLocal());
                txtCodigoFilial.setText("Código Filial: " + local.getCodigoFilial());
            }
        }

        // === Botão EDITAR ===
        btnEdit.setOnClickListener(v -> abrirDialogEdicao());

        // === Botão EXCLUIR ===
        btnTrash.setOnClickListener(v -> confirmarExclusao());
    }

    private void abrirDialogEdicao() {
        // Campos de entrada
        final EditText edtNome = new EditText(this);
        final EditText edtCodigoFilial = new EditText(this);
        final EditText edtCodigoLocal = new EditText(this);

        edtNome.setHint("Nome do Local");
        edtCodigoFilial.setHint("Código Filial");
        edtCodigoLocal.setHint("Código Local");

        // Preenche com valores atuais
        edtNome.setText(local.getLocalNome());
        edtCodigoFilial.setText(local.getCodigoFilial());
        edtCodigoLocal.setText(local.getCodigoLocal());

        // Layout vertical
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);
        layout.addView(edtNome);
        layout.addView(edtCodigoFilial);
        layout.addView(edtCodigoLocal);

        // AlertDialog
        new AlertDialog.Builder(this)
                .setTitle("Editar Local")
                .setView(layout)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String novoNome = edtNome.getText().toString().trim();
                    String novoCodigoFilial = edtCodigoFilial.getText().toString().trim();
                    String novoCodigoLocal = edtCodigoLocal.getText().toString().trim();

                    if (novoNome.isEmpty() || novoCodigoFilial.isEmpty() || novoCodigoLocal.isEmpty()) {
                        Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Atualiza objeto local
                    local.setLocalNome(novoNome);
                    local.setCodigoFilial(novoCodigoFilial);
                    local.setCodigoLocal(novoCodigoLocal);

                    boolean atualizado = dbHelper.atualizarLocal(local.getId(), local);

                    if (atualizado) {
                        txtNomeLocal.setText("Local Nome: " + local.getLocalNome());
                        txtCodigoLocal.setText("Código Local: " + local.getCodigoLocal());
                        txtCodigoFilial.setText("Código Filial: " + local.getCodigoFilial());
                        Toast.makeText(this, "Local atualizado com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Erro ao atualizar local", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarExclusao() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir Local")
                .setMessage("Tem certeza que deseja excluir este local?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    boolean excluido = dbHelper.excluirLocal(local.getId());
                    if (excluido) {
                        Toast.makeText(this, "Local excluído com sucesso", Toast.LENGTH_SHORT).show();
                        finish(); // volta para a lista
                    } else {
                        Toast.makeText(this, "Erro ao excluir local", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
