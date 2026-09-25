package com.example.uhf.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.model.Local;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tela de entrada do inventário: filial, local e funcionário responsável.
 *
 * Local e funcionário são escolhidos por um diálogo de busca + lista
 * (SelecaoAdapter, no fim do arquivo) em vez de AutoCompleteTextView —
 * mesmo padrão visual do diálogo de filtro de categorias da tela de
 * inventário por categoria. É seleção única: tocar num item já seleciona
 * e fecha o diálogo, sem precisar de botão "Aplicar".
 */
public class InventarioActivity extends AppCompatActivity {

    private EditText editCodigoFilial;
    private EditText editCodigoLocal;
    private LinearLayout btnModoNomeLocal, btnModoCodigoLocal;
    private LinearLayout rowBuscaNomeLocal, rowBuscaCodigoLocal;
    private LinearLayout rowSelecionarLocal;
    private TextView txtSelecionarLocal;
    private LinearLayout rowSelecionarFuncionario;
    private TextView txtSelecionarFuncionario;
    private LinearLayout buttonPesquisar;
    private LinearLayout cardLocal;
    private TextView txtCodigoLocalSelecionado;
    private ImageView btnLimparLocal;
    private LinearLayout cardMatricula;
    private TextView txtMatriculaSelecionada;
    private ImageView btnLimparFuncionario;
    private DBHelper dbHelper;

    private String tipoLeitura;
    private String tipoInventario;

    /** true = buscando o local por nome (diálogo); false = digitando o código direto. */
    private boolean modoNomeLocal = true;

    /** Código do local escolhido no diálogo. Null se nenhum foi selecionado. */
    private String codigoLocalSelecionado = null;

    /** Matrícula do funcionário escolhido no diálogo. Null se nenhum foi selecionado. */
    private String matriculaSelecionada = null;

    private static final String HINT_LOCAL = "Toque para selecionar o local";
    private static final String HINT_FUNCIONARIO = "Toque para selecionar o responsável";

    // -------------------------------------------------------------------------
    // Modelos simples nome → código/matrícula
    // -------------------------------------------------------------------------
    private static class LocalItem {
        final String nome;
        final String codigo;

        LocalItem(String nome, String codigo) {
            this.nome   = nome;
            this.codigo = codigo;
        }
    }

    private static class FuncionarioItem {
        final String nome;
        final String matricula;

        FuncionarioItem(String nome, String matricula) {
            this.nome      = nome;
            this.matricula = matricula;
        }
    }

    // -------------------------------------------------------------------------
    // onCreate
    // -------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario);

        editCodigoFilial             = findViewById(R.id.editCodigoFilial);
        editCodigoLocal              = findViewById(R.id.editCodigoLocal);
        btnModoNomeLocal             = findViewById(R.id.btnModoNomeLocal);
        btnModoCodigoLocal           = findViewById(R.id.btnModoCodigoLocal);
        rowBuscaNomeLocal            = findViewById(R.id.rowBuscaNomeLocal);
        rowBuscaCodigoLocal          = findViewById(R.id.rowBuscaCodigoLocal);
        rowSelecionarLocal           = findViewById(R.id.rowSelecionarLocal);
        txtSelecionarLocal           = findViewById(R.id.txtSelecionarLocal);
        rowSelecionarFuncionario     = findViewById(R.id.rowSelecionarFuncionario);
        txtSelecionarFuncionario     = findViewById(R.id.txtSelecionarFuncionario);
        buttonPesquisar              = findViewById(R.id.buttonPesquisar);
        cardLocal                    = findViewById(R.id.cardLocal);
        txtCodigoLocalSelecionado    = findViewById(R.id.txtCodigoLocalSelecionado);
        btnLimparLocal               = findViewById(R.id.btnLimparLocal);
        cardMatricula                = findViewById(R.id.cardMatricula);
        txtMatriculaSelecionada      = findViewById(R.id.txtMatriculaSelecionada);
        btnLimparFuncionario         = findViewById(R.id.btnLimparFuncionario);

        dbHelper = new DBHelper(this);

        tipoLeitura    = getIntent().getStringExtra("tipoLeitura");
        tipoInventario = getIntent().getStringExtra("tipoInventario");
        if (tipoInventario == null) tipoInventario = "LIVRE";

        rowSelecionarLocal.setOnClickListener(v -> abrirDialogSelecaoLocal());
        rowSelecionarFuncionario.setOnClickListener(v -> abrirDialogSelecaoFuncionario());

        btnModoNomeLocal.setOnClickListener(v -> alternarModoLocal(true));
        btnModoCodigoLocal.setOnClickListener(v -> alternarModoLocal(false));

        btnLimparLocal.setOnClickListener(v -> limparSelecaoLocal());
        btnLimparFuncionario.setOnClickListener(v -> limparSelecaoFuncionario());

        buttonPesquisar.setOnClickListener(v -> verificarCampos());
    }

    /** Alterna entre buscar o local por nome (diálogo) ou digitar o código direto. */
    private void alternarModoLocal(boolean porNome) {
        modoNomeLocal = porNome;

        int azul  = android.graphics.Color.parseColor("#005eb8");
        int cinza = android.graphics.Color.parseColor("#B0BEC5");

        btnModoNomeLocal.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(porNome ? azul : cinza));
        btnModoCodigoLocal.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(porNome ? cinza : azul));

        rowBuscaNomeLocal.setVisibility(porNome ? View.VISIBLE : View.GONE);
        rowBuscaCodigoLocal.setVisibility(porNome ? View.GONE : View.VISIBLE);

        if (porNome) {
            // Saindo do modo código: limpa o que foi digitado lá.
            editCodigoLocal.setText("");
        } else {
            // Saindo do modo nome: limpa a seleção do diálogo.
            limparSelecaoLocal();
        }
    }

    // -------------------------------------------------------------------------
    // Seleção de local — diálogo com busca
    // -------------------------------------------------------------------------

    private void abrirDialogSelecaoLocal() {
        List<LocalItem> todosLocais = carregarLocais();

        if (todosLocais.isEmpty()) {
            mostrarAlerta("Nenhum local cadastrado no banco local.");
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_busca_selecao, null);

        EditText etBusca = view.findViewById(R.id.etBuscaSelecao);
        RecyclerView rvSelecao = view.findViewById(R.id.rvSelecao);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Selecionar local")
                .setView(view)
                .setNegativeButton("Cancelar", null)
                .create();

        SelecaoAdapter<LocalItem> adapter = new SelecaoAdapter<>(
                todosLocais,
                item -> item.nome,
                item -> "Código: " + item.codigo,
                item -> item.nome + " " + item.codigo,
                item -> {
                    codigoLocalSelecionado = item.codigo;
                    txtSelecionarLocal.setText(item.nome);
                    txtSelecionarLocal.setTextColor(
                            android.graphics.Color.parseColor("#212121"));
                    txtCodigoLocalSelecionado.setText(item.codigo);
                    cardLocal.setVisibility(View.VISIBLE);
                    dialog.dismiss();
                });

        rvSelecao.setLayoutManager(new LinearLayoutManager(this));
        rvSelecao.setAdapter(adapter);

        etBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.filtrar(normalizarTexto(s.toString()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    /** Lê todos os locais do banco (tabela "locais": local_nome/codigo_local) e devolve como lista de LocalItem. */
    private List<LocalItem> carregarLocais() {
        List<LocalItem> lista = new ArrayList<>();

        for (Local local : dbHelper.listarLocais()) {
            lista.add(new LocalItem(local.getLocalNome(), local.getCodigoLocal()));
        }

        Collections.sort(lista, (a, b) -> {
            String nomeA = a.nome != null ? a.nome : "";
            String nomeB = b.nome != null ? b.nome : "";
            return nomeA.compareToIgnoreCase(nomeB);
        });

        return lista;
    }

    /** Limpa a seleção e volta o rótulo ao estado inicial. */
    private void limparSelecaoLocal() {
        codigoLocalSelecionado = null;
        txtSelecionarLocal.setText(HINT_LOCAL);
        txtSelecionarLocal.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
        cardLocal.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------------------
    // Seleção de funcionário — diálogo com busca
    // -------------------------------------------------------------------------

    private void abrirDialogSelecaoFuncionario() {
        List<FuncionarioItem> todosFuncionarios = carregarFuncionarios();

        if (todosFuncionarios.isEmpty()) {
            mostrarAlerta("Nenhum funcionário cadastrado no banco local.");
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_busca_selecao, null);

        EditText etBusca = view.findViewById(R.id.etBuscaSelecao);
        RecyclerView rvSelecao = view.findViewById(R.id.rvSelecao);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Selecionar responsável")
                .setView(view)
                .setNegativeButton("Cancelar", null)
                .create();

        SelecaoAdapter<FuncionarioItem> adapter = new SelecaoAdapter<>(
                todosFuncionarios,
                item -> item.nome,
                item -> "Matrícula: " + item.matricula,
                item -> item.nome + " " + item.matricula,
                item -> {
                    matriculaSelecionada = item.matricula;
                    txtSelecionarFuncionario.setText(item.nome);
                    txtSelecionarFuncionario.setTextColor(
                            android.graphics.Color.parseColor("#212121"));
                    txtMatriculaSelecionada.setText(item.matricula);
                    cardMatricula.setVisibility(View.VISIBLE);
                    dialog.dismiss();
                });

        rvSelecao.setLayoutManager(new LinearLayoutManager(this));
        rvSelecao.setAdapter(adapter);

        etBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.filtrar(normalizarTexto(s.toString()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    /** Lê todos os usuários do banco e devolve como lista de FuncionarioItem. */
    private List<FuncionarioItem> carregarFuncionarios() {
        List<FuncionarioItem> lista = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT nome, matricula FROM usuarios ORDER BY nome ASC", null);
        if (c.moveToFirst()) {
            do {
                lista.add(new FuncionarioItem(c.getString(0), c.getString(1)));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return lista;
    }

    /** Limpa a seleção e volta o rótulo ao estado inicial. */
    private void limparSelecaoFuncionario() {
        matriculaSelecionada = null;
        txtSelecionarFuncionario.setText(HINT_FUNCIONARIO);
        txtSelecionarFuncionario.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
        cardMatricula.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------------------
    // Validação e roteamento
    // -------------------------------------------------------------------------
    private void verificarCampos() {
        String codigoFilial = editCodigoFilial.getText().toString().trim();

        // ── Validações de preenchimento ───────────────────────────────────
        if (codigoFilial.isEmpty()) {
            mostrarAlerta("Preencha todos os campos!");
            return;
        }

        if (codigoFilial.length() > 3) {
            mostrarAlerta("O código da filial deve ter no máximo 3 dígitos.");
            return;
        }

        // Garante que um local foi informado, conforme o modo ativo
        String codigoLocal;

        if (modoNomeLocal) {
            if (codigoLocalSelecionado == null || codigoLocalSelecionado.isEmpty()) {
                mostrarAlerta("Selecione um local pelo nome antes de continuar.");
                return;
            }
            codigoLocal = codigoLocalSelecionado;
        } else {
            codigoLocal = editCodigoLocal.getText().toString().trim();
            if (codigoLocal.isEmpty()) {
                mostrarAlerta("Preencha o código do local.");
                return;
            }
        }

        // Garante que um funcionário foi selecionado no diálogo
        if (matriculaSelecionada == null || matriculaSelecionada.isEmpty()) {
            mostrarAlerta("Selecione um funcionário pelo nome antes de continuar.");
            return;
        }

        // ── Validação no banco (obrigatória no modo código; defensiva no modo nome) ──
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursorLocal = db.rawQuery(
                "SELECT * FROM locais WHERE codigo_local = ?",
                new String[]{ codigoLocal });
        boolean localExiste = cursorLocal.moveToFirst();
        cursorLocal.close();

        db.close();

        if (!localExiste) {
            mostrarAlerta("Código Local não encontrado!");
            return;
        }

        // ── Roteamento ────────────────────────────────────────────────────
        Intent intent;

        if ("CODBARRA".equals(tipoLeitura)) {
            intent = new Intent(this, InventarioCodBarraActivity.class);
        } else {
            switch (tipoInventario) {
                case "LOCAL":
                    intent = new Intent(this, InventarioLocalActivity.class);
                    break;
                case "CATEGORIA":
                    intent = new Intent(this, InventarioCategoriaActivity.class);
                    break;
                default: // "LIVRE"
                    intent = new Intent(this, InventarioLivreActivity.class);
                    break;
            }
        }

        // ── Repassa os campos para a Activity de destino ──────────────────
        intent.putExtra("codigoFilial",     codigoFilial);
        intent.putExtra("codigoLocal",      codigoLocal);
        intent.putExtra("chapaFuncionario", matriculaSelecionada); // matrícula do selecionado
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

    // ═══════════════════════════════════════════════════════════
    //  Utilitários de texto (mesmo padrão usado no inventário por categoria)
    // ═══════════════════════════════════════════════════════════

    /** Remove acentos e joga para minúsculo: "José Diretório" -> "jose diretorio". */
    private static String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /**
     * True se todas as palavras do termo aparecerem no alvo, em qualquer ordem.
     */
    private static boolean casaTodasPalavras(String alvoNormalizado, String termoNormalizado) {
        if (alvoNormalizado == null) {
            return false;
        }

        if (termoNormalizado == null || termoNormalizado.isEmpty()) {
            return true;
        }

        for (String palavra : termoNormalizado.split("\\s+")) {
            if (!palavra.isEmpty() && !alvoNormalizado.contains(palavra)) {
                return false;
            }
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  Adapter genérico de seleção única com busca
    // ═══════════════════════════════════════════════════════════

    /** Extrai um texto de exibição (ou de busca) de um item T. */
    interface TextoExtractor<T> {
        String extrair(T item);
    }

    /** Chamado quando o usuário toca um item da lista para selecioná-lo. */
    interface OnItemSelecionado<T> {
        void selecionou(T item);
    }

    /**
     * RecyclerView.Adapter reutilizável para qualquer lista de seleção única
     * com busca por texto: usado aqui tanto para Local quanto Funcionário,
     * passando apenas como extrair título/subtítulo/texto-de-busca de cada
     * item e o que fazer quando um item é tocado.
     */
    static class SelecaoAdapter<T> extends RecyclerView.Adapter<SelecaoAdapter.VH> {

        private final List<T> todos;
        private final TextoExtractor<T> titulo;
        private final TextoExtractor<T> subtitulo;
        private final TextoExtractor<T> textoBusca;
        private final OnItemSelecionado<T> callback;

        private List<T> exibidos;

        SelecaoAdapter(List<T> todos,
                       TextoExtractor<T> titulo,
                       TextoExtractor<T> subtitulo,
                       TextoExtractor<T> textoBusca,
                       OnItemSelecionado<T> callback) {
            this.todos = todos;
            this.titulo = titulo;
            this.subtitulo = subtitulo;
            this.textoBusca = textoBusca;
            this.callback = callback;
            this.exibidos = todos;
        }

        /** Reaplica o texto de busca (já normalizado) e redesenha. */
        void filtrar(String termoNormalizado) {
            if (termoNormalizado == null || termoNormalizado.isEmpty()) {
                exibidos = todos;
            } else {
                List<T> resultado = new ArrayList<>();

                for (T item : todos) {
                    if (casaTodasPalavras(normalizarTexto(textoBusca.extrair(item)), termoNormalizado)) {
                        resultado.add(item);
                    }
                }

                exibidos = resultado;
            }

            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_linha_selecao, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            T item = exibidos.get(position);

            holder.txtTitulo.setText(titulo.extrair(item));
            holder.txtSubtitulo.setText(subtitulo.extrair(item));
            holder.itemView.setOnClickListener(v -> callback.selecionou(item));
        }

        @Override
        public int getItemCount() {
            return exibidos.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView txtTitulo, txtSubtitulo;

            VH(@NonNull View itemView) {
                super(itemView);
                txtTitulo = itemView.findViewById(R.id.txtTituloSelecao);
                txtSubtitulo = itemView.findViewById(R.id.txtSubtituloSelecao);
            }
        }
    }
}