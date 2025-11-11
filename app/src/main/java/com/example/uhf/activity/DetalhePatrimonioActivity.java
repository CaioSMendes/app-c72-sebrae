package com.example.uhf.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.uhf.model.Patrimonio;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class DetalhePatrimonioActivity extends AppCompatActivity {

    private TextView txtPatrimonio, txtDescricao, txtCodigo;
    private ImageView imgPatrimonio;
    private LinearLayout btnEdit, btnTrash;

    private DBHelper db;
    private com.example.uhf.model.Patrimonio patrimonio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhe_patrimonio);

        txtPatrimonio = findViewById(R.id.txtPatrimonio);
        txtDescricao = findViewById(R.id.txtDescricao);
        txtCodigo = findViewById(R.id.txtCodigoBarra);
        imgPatrimonio = findViewById(R.id.imgPatrimonio);

        btnEdit = findViewById(R.id.btnEdit);
        btnTrash = findViewById(R.id.btnTrash);

        db = new DBHelper(this);

        int id = getIntent().getIntExtra("id", -1);
        if (id != -1) {
            patrimonio = db.buscarPatrimonioPorId(id);
            mostrarDetalhes();
        }

        btnEdit.setOnClickListener(v -> abrirDialogEditar());
        btnTrash.setOnClickListener(v -> confirmarExcluir());
    }

    private void mostrarDetalhes() {
        if (patrimonio != null) {
            txtPatrimonio.setText(patrimonio.getPatrimonio());
            txtDescricao.setText(patrimonio.getDescricao());
            txtCodigo.setText(patrimonio.getCodigoBarra());
            imgPatrimonio.setImageResource(R.drawable.ic_ativo_pat); // imagem padrão
        }
    }

    private void abrirDialogEditar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Patrimônio");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputPat = new EditText(this);
        inputPat.setHint("Patrimônio");
        inputPat.setText(patrimonio.getPatrimonio());
        layout.addView(inputPat);

        final EditText inputDesc = new EditText(this);
        inputDesc.setHint("Descrição");
        inputDesc.setText(patrimonio.getDescricao());
        layout.addView(inputDesc);

        final EditText inputCod = new EditText(this);
        inputCod.setHint("Código de Barra");
        inputCod.setText(patrimonio.getCodigoBarra());
        layout.addView(inputCod);

        builder.setView(layout);

        builder.setPositiveButton("Salvar", (dialog, which) -> {
            String novoPat = inputPat.getText().toString().trim();
            String novaDesc = inputDesc.getText().toString().trim();
            String novoCod = inputCod.getText().toString().trim();

            if (novoPat.isEmpty() || novaDesc.isEmpty() || novoCod.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean sucesso = db.atualizarPatrimonio(patrimonio.getId(), novoPat, novaDesc, novoCod);
            if (sucesso) {
                patrimonio.setPatrimonio(novoPat);
                patrimonio.setDescricao(novaDesc);
                patrimonio.setCodigoBarra(novoCod);
                mostrarDetalhes();
                Toast.makeText(this, "Atualizado com sucesso!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Erro ao atualizar!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void confirmarExcluir() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Excluir Patrimônio");
        builder.setMessage("Tem certeza que deseja excluir este registro?");
        builder.setPositiveButton("Sim", (dialog, which) -> {
            boolean sucesso = db.excluirPatrimonio(patrimonio.getId());
            if (sucesso) {
                Toast.makeText(this, "Patrimônio excluído!", Toast.LENGTH_SHORT).show();
                finish(); // fecha a activity e volta para lista
            } else {
                Toast.makeText(this, "Erro ao excluir!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Não", null);
        builder.show();
    }
}