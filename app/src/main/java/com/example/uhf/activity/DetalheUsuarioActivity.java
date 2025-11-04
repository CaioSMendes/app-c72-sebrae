package com.example.uhf.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.uhf.R;

public class DetalheUsuarioActivity extends AppCompatActivity {

    private TextView txtNome, txtMatricula;
    private ImageView imgUsuario;
    private LinearLayout btnEdit, btnTrash;
    private DBHelper dbHelper;
    private String nomeOriginal, matriculaOriginal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhe_usuario);

        dbHelper = new DBHelper(this);

        // Referências
        txtNome = findViewById(R.id.txtNome);
        txtMatricula = findViewById(R.id.txtMatricula);
        imgUsuario = findViewById(R.id.imgUsuario);
        btnEdit = findViewById(R.id.btnEdit);
        btnTrash = findViewById(R.id.btnTrash);

        // Recebe dados do usuário
        nomeOriginal = getIntent().getStringExtra("nome");
        matriculaOriginal = getIntent().getStringExtra("matricula");
        int imagemRes = getIntent().getIntExtra("imagem", R.drawable.ic_user);

        // Exibe na tela
        txtNome.setText(nomeOriginal);
        txtMatricula.setText("Matrícula: " + matriculaOriginal);
        imgUsuario.setImageResource(imagemRes);

        // === Botão EDITAR ===
        btnEdit.setOnClickListener(v -> abrirDialogEdicao());

        // === Botão EXCLUIR ===
        btnTrash.setOnClickListener(v -> confirmarExclusao());
    }

    private void abrirDialogEdicao() {
        // Cria campos de entrada
        final EditText edtNome = new EditText(this);
        final EditText edtMatricula = new EditText(this);

        edtNome.setHint("Nome");
        edtMatricula.setHint("Matrícula");
        edtNome.setText(nomeOriginal);
        edtMatricula.setText(matriculaOriginal);

        // Layout vertical
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);
        layout.addView(edtNome);
        layout.addView(edtMatricula);

        // Diálogo
        new AlertDialog.Builder(this)
                .setTitle("Editar Usuário")
                .setView(layout)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String novoNome = edtNome.getText().toString().trim();
                    String novaMatricula = edtMatricula.getText().toString().trim();

                    if (novoNome.isEmpty() || novaMatricula.isEmpty()) {
                        Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean atualizado = dbHelper.atualizarUsuario(nomeOriginal, matriculaOriginal, novoNome, novaMatricula);

                    if (atualizado) {
                        nomeOriginal = novoNome;
                        matriculaOriginal = novaMatricula;
                        txtNome.setText(novoNome);
                        txtMatricula.setText("Matrícula: " + novaMatricula);
                        Toast.makeText(this, "Usuário atualizado com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Erro ao atualizar usuário", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarExclusao() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir Usuário")
                .setMessage("Tem certeza que deseja excluir este usuário?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    boolean excluido = dbHelper.excluirUsuario(nomeOriginal, matriculaOriginal);
                    if (excluido) {
                        Toast.makeText(this, "Usuário excluído com sucesso", Toast.LENGTH_SHORT).show();
                        finish(); // volta para a lista
                    } else {
                        Toast.makeText(this, "Erro ao excluir usuário", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
