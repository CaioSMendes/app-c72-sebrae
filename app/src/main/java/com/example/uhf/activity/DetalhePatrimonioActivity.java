package com.example.uhf.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Patrimonio;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetalhePatrimonioActivity extends AppCompatActivity {

    // Campos básicos (já existiam)
    private TextView   txtPatrimonio, txtDescricao, txtCodigo;
    private ImageView  imgPatrimonio;
    private LinearLayout btnEdit, btnTrash;

    // Campos novos da API
    private TextView txtDataAquisicao;
    private TextView txtValorAquisicao;
    private TextView txtNomeLocal;
    private TextView txtCodLocal;

    private DBHelper   db;
    private Patrimonio patrimonio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhe_patrimonio);

        // Bind campos básicos
        txtPatrimonio = findViewById(R.id.txtPatrimonio);
        txtDescricao  = findViewById(R.id.txtDescricao);
        txtCodigo     = findViewById(R.id.txtCodigoBarra);
        imgPatrimonio = findViewById(R.id.imgPatrimonio);
        btnEdit       = findViewById(R.id.btnEdit);
        btnTrash      = findViewById(R.id.btnTrash);

        // Bind campos novos
        txtDataAquisicao  = findViewById(R.id.txtDataAquisicao);
        txtValorAquisicao = findViewById(R.id.txtValorAquisicao);
        txtNomeLocal      = findViewById(R.id.txtNomeLocal);
        txtCodLocal       = findViewById(R.id.txtCodLocal);

        db = new DBHelper(this);

        int id = getIntent().getIntExtra("id", -1);
        if (id != -1) {
            patrimonio = db.buscarPatrimonioPorId(id);
            mostrarDetalhes();
        }

        btnEdit.setOnClickListener(v -> abrirDialogEditar());
        btnTrash.setOnClickListener(v -> confirmarExcluir());
    }

    // -------------------------------------------------------------------------
    // Exibir todos os campos
    // -------------------------------------------------------------------------

    private void mostrarDetalhes() {
        if (patrimonio == null) return;

        imgPatrimonio.setImageResource(R.drawable.ic_ativo_pat);

        // Campos básicos
        txtPatrimonio.setText(patrimonio.getPatrimonio());
        txtDescricao.setText(patrimonio.getDescricao());
        txtCodigo.setText(patrimonio.getCodigoBarra());

        // Data de Aquisição — formata ISO → dd/MM/yyyy
        String data = patrimonio.getDataAquisicao();
        txtDataAquisicao.setText(
                (data != null && !data.isEmpty()) ? formatarData(data) : "—"
        );

        // Valor de Aquisição — formata "20555.55" → "R$ 20.555,55"
        String valor = patrimonio.getValorAquisicao();
        if (valor != null && !valor.isEmpty() && !valor.equals("0.00")) {
            txtValorAquisicao.setText(formatarValor(valor));
        } else {
            txtValorAquisicao.setText("—");
        }

        // Local
        String nomeLocal = patrimonio.getNomeLocal();
        String codLocal  = patrimonio.getCodLocal();

        txtNomeLocal.setText(
                (nomeLocal != null && !nomeLocal.isEmpty()) ? nomeLocal : "—"
        );
        txtCodLocal.setText(
                (codLocal != null && !codLocal.isEmpty()) ? "Cód: " + codLocal : ""
        );
    }

    // -------------------------------------------------------------------------
    // Dialog de edição (campos básicos — data/valor/local vêm da API)
    // -------------------------------------------------------------------------

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
            String novoPat  = inputPat.getText().toString().trim();
            String novaDesc = inputDesc.getText().toString().trim();
            String novoCod  = inputCod.getText().toString().trim();

            if (novoPat.isEmpty() || novaDesc.isEmpty() || novoCod.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean sucesso = db.atualizarPatrimonio(
                    patrimonio.getId(), novoPat, novaDesc, novoCod);

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

    // -------------------------------------------------------------------------
    // Confirmar exclusão
    // -------------------------------------------------------------------------

    private void confirmarExcluir() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir Patrimônio")
                .setMessage("Tem certeza que deseja excluir este registro?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    boolean sucesso = db.excluirPatrimonio(patrimonio.getId());
                    if (sucesso) {
                        Toast.makeText(this, "Patrimônio excluído!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Erro ao excluir!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Não", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Helpers de formatação
    // -------------------------------------------------------------------------

    /** "2010-05-06T00:00:00-03:00" → "06/05/2010" */
    private String formatarData(String isoDate) {
        try {
            // Remove offset de timezone para simplificar o parse
            String semOffset = isoDate
                    .replaceAll("([+-]\\d{2}:\\d{2})$", "")
                    .replace("Z", "");
            SimpleDateFormat entrada = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = entrada.parse(semOffset);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
        } catch (ParseException e) {
            // Se não conseguir parsear, retorna o valor original sem o offset
            return isoDate.contains("T") ? isoDate.substring(0, 10) : isoDate;
        }
    }

    /** "20555.55" → "R$ 20.555,55" */
    private String formatarValor(String valor) {
        try {
            double v = Double.parseDouble(valor);
            return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(v);
        } catch (NumberFormatException e) {
            return valor;
        }
    }
}