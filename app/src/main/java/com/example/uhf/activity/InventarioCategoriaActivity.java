package com.example.uhf.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.model.Local;
import com.example.uhf.model.Patrimonio;
import com.example.uhf.model.Usuario;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inventário por categoria.
 *
 * Tudo que a tela precisa está neste arquivo: os enums de status, o modelo
 * de linha, o adapter do RecyclerView e a lógica de leitura.
 * Os únicos arquivos externos são o layout desta activity, os dois layouts
 * de linha (item_grupo_categoria e item_patrimonio) e os dois novos layouts
 * do diálogo de filtro de categoria (dialog_filtro_categorias e
 * item_categoria_filtro).
 *
 * Cores:
 *   cinza    = está na lista do local, ainda não lido
 *   verde    = está na lista do local e foi lido (ou foi trazido de outro local)
 *   amarelo  = foi lido, mas não pertence a este local
 *   vermelho = marcado manualmente como "não identificado" (override do usuário)
 *
 * Alteração manual de status (igual ao InventarioLocalActivity):
 *   Tocar num item ENCONTRADO pede confirmação para marcá-lo como
 *   NAO_ENCONTRADO (vermelho). Tocar num item NAO_ENCONTRADO pede
 *   confirmação para voltar a ENCONTRADO (verde). Tocar num item PENDENTE
 *   marca ENCONTRADO direto, sem diálogo. Itens do grupo de divergentes
 *   continuam usando o fluxo próprio de "aceitar" (trazer para o local).
 *
 * Filtro de categoria de leitura:
 *   Por padrão (nenhuma categoria selecionada) o leitor aceita qualquer tag
 *   deste local, exatamente como antes. Quando o usuário seleciona uma ou
 *   mais categorias no diálogo "Filtrar categorias", o leitor passa a
 *   ignorar completamente qualquer tag que não pertença a uma delas: ela
 *   não soma, não apita e não entra na lista nem no resumo — como se o
 *   leitor nem tivesse visto a tag. Isso é decidido em processarTag().
 */
public class InventarioCategoriaActivity extends AppCompatActivity {

    private static final String TAG = "InventarioCategoria";

    /** Espera após o usuário parar de digitar antes de filtrar. */
    private static final long TEMPO_DEBOUNCE_BUSCA = 250L;

    /** Janela de agrupamento das leituras antes de redesenhar a lista. */
    private static final long JANELA_FLUSH = 250L;

    /** Grupo virtual que reúne as tags lidas fora do local. */
    private static final String GRUPO_DIVERGENTES = "\u26A0 Não pertence a este local";

    private static final int COR_CHIP_ATIVO = Color.parseColor("#005eb8");
    private static final int COR_CHIP_INATIVO = Color.parseColor("#B0BEC5");

    // ═══════════════════════════════════════════════════════════
    //  Tipos auxiliares (antes eram arquivos separados)
    // ═══════════════════════════════════════════════════════════

    public enum StatusItem {
        PENDENTE,      // cinza
        ENCONTRADO,    // verde
        DIVERGENTE,    // amarelo
        NAO_ENCONTRADO // vermelho — marcado manualmente pelo usuário
    }

    public enum FiltroStatus {
        TODOS,
        PENDENTES,
        ENCONTRADOS,
        DIVERGENTES
    }

    /** Descrição/local de origem de uma tag lida que não pertence a este local. */
    private static class InfoDivergente {
        final String codigoExibido;
        final String descricao;
        final String localOrigem;
        final boolean identificada; // true = existe em algum local; false = tag totalmente desconhecida

        InfoDivergente(String codigoExibido, String descricao, String localOrigem, boolean identificada) {
            this.codigoExibido = codigoExibido;
            this.descricao = descricao;
            this.localOrigem = localOrigem;
            this.identificada = identificada;
        }
    }

    /** Uma linha da lista plana: cabeçalho de categoria ou item. */
    public static class Linha {

        static final int TIPO_GRUPO = 0;
        static final int TIPO_ITEM = 1;

        final int tipo;
        final String id;          // "G:<grupo>" ou "I:<chave5>"
        final String grupo;
        final String chave5;
        final String codigo;
        final String descricao;
        final String localTexto;  // linha extra (local de origem / status de aceite) — null quando não se aplica

        int lidosGrupo;
        int totalGrupo;
        boolean expandido;
        StatusItem status;

        private Linha(int tipo, String id, String grupo,
                      String chave5, String codigo, String descricao, String localTexto) {
            this.tipo = tipo;
            this.id = id;
            this.grupo = grupo;
            this.chave5 = chave5;
            this.codigo = codigo;
            this.descricao = descricao;
            this.localTexto = localTexto;
        }

        static Linha grupo(String desc, int lidos, int total, boolean expandido) {
            Linha l = new Linha(TIPO_GRUPO, "G:" + desc, desc, null, null, null, null);
            l.lidosGrupo = lidos;
            l.totalGrupo = total;
            l.expandido = expandido;
            return l;
        }

        static Linha item(String grupo, String chave5, String codigo,
                          String descricao, String localTexto, StatusItem status) {
            Linha l = new Linha(TIPO_ITEM, "I:" + chave5, grupo, chave5, codigo, descricao, localTexto);
            l.status = status;
            return l;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Estado
    // ═══════════════════════════════════════════════════════════

    private RFIDWithUHFUART mReader;
    private BarcodeDecoder barcodeDecoder;

    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D = false;
    private volatile boolean modoRfid = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler buscaHandler = new Handler(Looper.getMainLooper());

    /** Monta a lista, grava histórico e consulta o banco. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Exclusivo do loop RFID, que ocupa a thread o tempo todo. */
    private final ExecutorService rfidExecutor = Executors.newSingleThreadExecutor();

    /** Inicialização e callback do leitor 2D. */
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGen;

    private DBHelper dbHelper;
    private String codigoFilial, codigoLocal, chapaFuncionario;
    private Local localBanco;
    private Usuario userBanco;

    /** descrição da categoria -> patrimônios do local. */
    private final Map<String, List<Patrimonio>> mapaCompleto = new TreeMap<>();

    /** descrição -> descrição normalizada, pré-calculada para a busca. */
    private final Map<String, String> grupoNormalizado = new HashMap<>();

    /** chave5 -> descrição da categoria. */
    private final Map<String, String> indiceGrupoPorChave = new HashMap<>();

    /** chave5 -> patrimônio deste local. */
    private final Map<String, Patrimonio> indicePatrimonioPorChave = new HashMap<>();

    /**
     * chave5 -> patrimônio em QUALQUER local. Montado uma única vez em
     * background, só para descrever a tag divergente (nome + local de
     * origem) sem consultar o banco a cada leitura.
     */
    private final Map<String, Patrimonio> indiceGlobalPorChave = new ConcurrentHashMap<>();

    /** Todas as chaves lidas na sessão, sem filtro nenhum. */
    private final Set<String> lidos = ConcurrentHashMap.newKeySet();

    /** chave5 lida que não existe neste local -> informações do item. */
    private final Map<String, InfoDivergente> divergentes = new ConcurrentHashMap<>();

    /** Divergentes que o usuário tocou para "trazer" para este local. */
    private final Set<String> divergentesAceitas = ConcurrentHashMap.newKeySet();

    /** Chaves5 marcadas manualmente como "não identificado" (override do usuário). */
    private final Set<String> naoEncontradosManual = ConcurrentHashMap.newKeySet();

    /** Categorias abertas. */
    private final Set<String> gruposExpandidos = ConcurrentHashMap.newKeySet();

    /**
     * Categorias que o leitor deve aceitar nesta sessão. Vazio = aceita
     * qualquer categoria do local (comportamento padrão). Quando não-vazio,
     * qualquer tag lida cuja categoria não esteja aqui é totalmente
     * ignorada em processarTag() — não conta, não apita, não aparece.
     */
    private final Set<String> categoriasFiltro = ConcurrentHashMap.newKeySet();

    /**
     * true quando o usuário escolheu explicitamente a opção "Todas as
     * categorias" no diálogo — diferente de categoriasFiltro vazio por
     * padrão (estado inicial, ainda sem nenhuma escolha). É essa distinção
     * que decide se o botão de leitura pode ser usado: nunca há categoria
     * "default" lida — ou o usuário escolheu "Todas as categorias" de
     * propósito, ou escolheu categorias específicas. Sem uma das duas, a
     * leitura fica bloqueada.
     */
    private volatile boolean leituraTodasCategorias = false;

    /** Termo de busca já normalizado. */
    private volatile String queryTexto = "";

    private FiltroStatus filtroStatus = FiltroStatus.TODOS;

    /** Descarta montagens antigas que chegarem atrasadas. */
    private volatile int versaoLista = 0;

    private Runnable buscaRunnable;
    private volatile boolean flushAgendado = false;

    // ── Views ─────────────────────────────────────────────────
    private RecyclerView rvCategorias;
    private InventarioAdapter adapter;

    private TextView tvContador, txtInfoTopo, txtInfoUser, txtBotao,
            txtModoToggle, tvEmptyState, btnLimparFiltro;

    private EditText etBusca;

    private LinearLayout btnFiltroTodos, btnFiltroPendentes,
            btnFiltroEncontrados, btnFiltroDivergentes;

    private TextView txtQtdTodos, txtQtdPendentes,
            txtQtdEncontrados, txtQtdDivergentes;

    private LinearLayout btnLer, btnConcluir, btnDistancia, btnResumo,
            btnHistorico, btnModoToggle, btnLimparTags;

    /** Botão que abre o diálogo de seleção de categorias e a fileira de chips com as categorias ativas. */
    private LinearLayout btnFiltrarCategorias;
    private TextView txtBotaoFiltroCategoria;
    private LinearLayout llChipsFiltroCategoria;

    // ═══════════════════════════════════════════════════════════
    //  Ciclo de vida
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario_categoria);

        dbHelper = new DBHelper(this);

        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        vincularViews();
        configurarRecycler();
        configurarBusca();
        configurarChips();
        configurarListeners();

        atualizarChipsFiltroAtivo();

        carregarEAgrupar();
        carregarIndiceGlobal();

        inicializarRFID();
        inicializarBarcode2D();
        atualizarBotaoModo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pararLeituraRFID();
        pararLeitura2D();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (buscaRunnable != null) {
            buscaHandler.removeCallbacks(buscaRunnable);
        }

        mainHandler.removeCallbacksAndMessages(null);

        isReadingRFID = false;
        isReading2D = false;

        executor.shutdownNow();
        rfidExecutor.shutdownNow();
        barcodeExecutor.shutdownNow();

        try {
            if (barcodeDecoder != null) {
                barcodeDecoder.close();
            }
        } catch (Exception ignored) {
        }

        if (toneGen != null) {
            toneGen.release();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Views
    // ═══════════════════════════════════════════════════════════

    private void vincularViews() {
        rvCategorias = findViewById(R.id.rvCategorias);

        tvContador = findViewById(R.id.tvContadorCategoria);
        txtInfoTopo = findViewById(R.id.txtInfoTopoCategoria);
        txtInfoUser = findViewById(R.id.txtInfoUserCategoria);
        txtBotao = findViewById(R.id.txtBotaoCategoria);
        txtModoToggle = findViewById(R.id.txtModoToggleCategoria);
        tvEmptyState = findViewById(R.id.tvEmptyStateCategoria);

        etBusca = findViewById(R.id.etBuscaCategoria);
        btnLimparFiltro = findViewById(R.id.btnLimparFiltro);

        btnFiltroTodos = findViewById(R.id.btnFiltroTodos);
        btnFiltroPendentes = findViewById(R.id.btnFiltroPendentes);
        btnFiltroEncontrados = findViewById(R.id.btnFiltroEncontrados);
        btnFiltroDivergentes = findViewById(R.id.btnFiltroDivergentes);

        txtQtdTodos = findViewById(R.id.txtQtdTodos);
        txtQtdPendentes = findViewById(R.id.txtQtdPendentes);
        txtQtdEncontrados = findViewById(R.id.txtQtdEncontrados);
        txtQtdDivergentes = findViewById(R.id.txtQtdDivergentes);

        btnLer = findViewById(R.id.btnLerCategoria);
        btnConcluir = findViewById(R.id.btnConcluirCategoria);
        btnDistancia = findViewById(R.id.btnDistanciaCategoria);
        btnResumo = findViewById(R.id.btnResumoCategoria);
        btnHistorico = findViewById(R.id.btnHistoricoCategoria);
        btnModoToggle = findViewById(R.id.btnModoToggleCategoria);
        btnLimparTags = findViewById(R.id.btnLimparCategoria);

        btnFiltrarCategorias = findViewById(R.id.btnFiltrarCategoriasCategoria);
        txtBotaoFiltroCategoria = findViewById(R.id.txtBotaoFiltroCategoria);
        llChipsFiltroCategoria = findViewById(R.id.llChipsFiltroCategoria);

        txtInfoTopo.setText(
                localBanco != null && userBanco != null
                        ? localBanco.getLocalNome() + " | " + userBanco.getNome()
                        : "Dados não encontrados."
        );

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);
    }

    private void configurarRecycler() {
        adapter = new InventarioAdapter(
                grupo -> {
                    if (!gruposExpandidos.remove(grupo)) {
                        gruposExpandidos.add(grupo);
                    }
                    reconstruirLista();
                },
                this::aceitarDivergente,
                this::alternarStatusManual
        );

        rvCategorias.setLayoutManager(new LinearLayoutManager(this));
        rvCategorias.setAdapter(adapter);

        // Sem animação: evita piscar durante leitura em rajada.
        rvCategorias.setItemAnimator(null);
        rvCategorias.setHasFixedSize(true);
    }

    // ═══════════════════════════════════════════════════════════
    //  Carregamento e índice
    // ═══════════════════════════════════════════════════════════

    private void carregarEAgrupar() {
        executor.execute(() -> {
            List<Patrimonio> todos = dbHelper.listarPatrimoniosPorLocal(codigoLocal);

            Map<String, List<Patrimonio>> mapa = new TreeMap<>();
            Map<String, String> normalizados = new HashMap<>();
            Map<String, String> idxGrupo = new HashMap<>();
            Map<String, Patrimonio> idxPatrimonio = new HashMap<>();

            int colisoes = 0;

            for (Patrimonio p : todos) {
                String descricao = p.getDescricao();

                if (descricao == null || descricao.trim().isEmpty()) {
                    descricao = "Sem descrição";
                }

                List<Patrimonio> lista = mapa.get(descricao);

                if (lista == null) {
                    lista = new ArrayList<>();
                    mapa.put(descricao, lista);
                    normalizados.put(descricao, normalizarTexto(descricao));
                }

                lista.add(p);

                String chave5 = obterChave5DoPatrimonio(p);

                if (chave5 != null) {
                    if (idxPatrimonio.containsKey(chave5)) {
                        colisoes++;
                        Log.w(TAG, "Chave5 duplicada: " + chave5 + " — " + p.getCodigoBarra());
                    }
                    idxGrupo.put(chave5, descricao);
                    idxPatrimonio.put(chave5, p);
                }
            }

            final int totalColisoes = colisoes;

            mainHandler.post(() -> {
                mapaCompleto.clear();
                mapaCompleto.putAll(mapa);

                grupoNormalizado.clear();
                grupoNormalizado.putAll(normalizados);

                indiceGrupoPorChave.clear();
                indiceGrupoPorChave.putAll(idxGrupo);

                indicePatrimonioPorChave.clear();
                indicePatrimonioPorChave.putAll(idxPatrimonio);

                if (totalColisoes > 0) {
                    Toast.makeText(
                            this,
                            "Atenção: " + totalColisoes
                                    + " patrimônio(s) com os mesmos 5 dígitos iniciais.",
                            Toast.LENGTH_LONG
                    ).show();
                }

                reconstruirLista();
            });
        });
    }

    /**
     * Índice de TODOS os patrimônios (qualquer local), montado uma única vez
     * em background. Usado só para descrever tags divergentes sem consultar
     * o banco inteiro a cada leitura.
     */
    private void carregarIndiceGlobal() {
        executor.execute(() -> {
            Map<String, Patrimonio> indice = new HashMap<>();

            for (Patrimonio p : dbHelper.listarPatrimonios()) {
                String chave5 = obterChave5DoPatrimonio(p);

                if (chave5 != null && !indice.containsKey(chave5)) {
                    indice.put(chave5, p);
                }
            }

            indiceGlobalPorChave.clear();
            indiceGlobalPorChave.putAll(indice);
        });
    }

    private String obterChave5DoPatrimonio(Patrimonio p) {
        if (p == null || p.getCodigoBarra() == null) {
            return null;
        }

        String normalizado = normalizarCodigo(p.getCodigoBarra());

        return normalizado.length() < 5 ? null : normalizado.substring(0, 5);
    }

    // ═══════════════════════════════════════════════════════════
    //  Busca com debounce
    // ═══════════════════════════════════════════════════════════

    private void configurarBusca() {
        etBusca.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                String termo = normalizarTexto(s.toString());

                btnLimparFiltro.setVisibility(termo.isEmpty() ? View.GONE : View.VISIBLE);

                if (buscaRunnable != null) {
                    buscaHandler.removeCallbacks(buscaRunnable);
                }

                buscaRunnable = () -> {
                    queryTexto = termo;

                    if (!termo.isEmpty()) {
                        expandirGruposQueCasam(termo);
                    }

                    reconstruirLista();
                };

                buscaHandler.postDelayed(buscaRunnable, TEMPO_DEBOUNCE_BUSCA);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnLimparFiltro.setOnClickListener(v -> {
            if (buscaRunnable != null) {
                buscaHandler.removeCallbacks(buscaRunnable);
            }

            queryTexto = "";
            gruposExpandidos.clear();

            etBusca.setText("");
            etBusca.clearFocus();

            reconstruirLista();
        });

        etBusca.setOnEditorActionListener((v, actionId, event) -> {
            boolean buscar = actionId == EditorInfo.IME_ACTION_SEARCH;

            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (!buscar && !enter) {
                return false;
            }

            if (buscaRunnable != null) {
                buscaHandler.removeCallbacks(buscaRunnable);
            }

            queryTexto = normalizarTexto(etBusca.getText().toString());

            if (!queryTexto.isEmpty()) {
                expandirGruposQueCasam(queryTexto);
            }

            reconstruirLista();

            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.hideSoftInputFromWindow(etBusca.getWindowToken(), 0);
            }

            etBusca.clearFocus();
            return true;
        });
    }

    private void expandirGruposQueCasam(String termo) {
        for (Map.Entry<String, String> e : grupoNormalizado.entrySet()) {
            if (casaTodasPalavras(e.getValue(), termo)) {
                gruposExpandidos.add(e.getKey());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtros com contador
    // ═══════════════════════════════════════════════════════════

    private void configurarChips() {
        btnFiltroTodos.setOnClickListener(v -> aplicarFiltro(FiltroStatus.TODOS));
        btnFiltroPendentes.setOnClickListener(v -> aplicarFiltro(FiltroStatus.PENDENTES));
        btnFiltroEncontrados.setOnClickListener(v -> aplicarFiltro(FiltroStatus.ENCONTRADOS));
        btnFiltroDivergentes.setOnClickListener(v -> aplicarFiltro(FiltroStatus.DIVERGENTES));

        pintarChips();
    }

    private void aplicarFiltro(FiltroStatus novo) {
        filtroStatus = novo;
        pintarChips();
        reconstruirLista();
    }

    private void pintarChips() {
        pintarChip(btnFiltroTodos, filtroStatus == FiltroStatus.TODOS);
        pintarChip(btnFiltroPendentes, filtroStatus == FiltroStatus.PENDENTES);
        pintarChip(btnFiltroEncontrados, filtroStatus == FiltroStatus.ENCONTRADOS);
        pintarChip(btnFiltroDivergentes, filtroStatus == FiltroStatus.DIVERGENTES);
    }

    private void pintarChip(View chip, boolean ativo) {
        chip.setBackgroundTintList(
                ColorStateList.valueOf(ativo ? COR_CHIP_ATIVO : COR_CHIP_INATIVO)
        );
    }

    /** Recalcula os números exibidos em cada botão de filtro. */
    private void atualizarContadoresChips() {
        int total = 0;
        int encontrados = 0;

        for (Map.Entry<String, List<Patrimonio>> entrada : mapaCompleto.entrySet()) {
            if (filtroCategoriaAtivo() && !categoriasFiltro.contains(entrada.getKey())) {
                continue;
            }

            for (Patrimonio patrimonio : entrada.getValue()) {
                total++;

                if (estaLido(patrimonio)) {
                    encontrados++;
                }
            }
        }

        int pendentes = total - encontrados;

        txtQtdTodos.setText(String.valueOf(total));
        txtQtdPendentes.setText(String.valueOf(pendentes));
        txtQtdEncontrados.setText(String.valueOf(encontrados));
        txtQtdDivergentes.setText(String.valueOf(divergentes.size()));
    }

    // ═══════════════════════════════════════════════════════════
    //  Filtro de categorias de leitura (o que o leitor aceita)
    // ═══════════════════════════════════════════════════════════

    /** true quando o usuário restringiu a leitura a um subconjunto de categorias. */
    private boolean filtroCategoriaAtivo() {
        return !categoriasFiltro.isEmpty();
    }

    /**
     * true quando existe uma escolha válida pra leitura: ou o usuário
     * marcou "Todas as categorias", ou escolheu ao menos uma categoria
     * específica. Nunca é true por padrão — é o que trava o botão de
     * leitura antes da primeira escolha.
     */
    private boolean selecaoDeLeituraValida() {
        return leituraTodasCategorias || !categoriasFiltro.isEmpty();
    }

    /** Ajusta a aparência do botão de leitura conforme há ou não uma escolha válida. */
    private void atualizarEstadoBotaoLeitura() {
        boolean valido = selecaoDeLeituraValida();

        btnLer.setAlpha(valido ? 1f : 0.5f);
        btnLer.setBackgroundTintList(
                ColorStateList.valueOf(valido ? COR_CHIP_ATIVO : COR_CHIP_INATIVO));
    }

    /** Monta um chip removível para a fileira de categorias ativas. */
    private TextView criarChip(String texto, Runnable aoRemover) {
        TextView chip = new TextView(this);
        chip.setText(texto + "  \u2715");
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(12f);
        chip.setBackgroundResource(R.drawable.bg_badge_azul);
        chip.setPadding(24, 12, 24, 12);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(12);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> aoRemover.run());
        return chip;
    }

    /**
     * Redesenha os chips com a escolha atual de leitura e o texto do botão
     * que abre o diálogo, e atualiza o botão de leitura junto — os três
     * sempre refletem o mesmo estado. Chamado sempre que categoriasFiltro
     * ou leituraTodasCategorias mudam.
     */
    private void atualizarChipsFiltroAtivo() {
        llChipsFiltroCategoria.removeAllViews();
        atualizarEstadoBotaoLeitura();

        if (leituraTodasCategorias) {
            llChipsFiltroCategoria.setVisibility(View.VISIBLE);
            txtBotaoFiltroCategoria.setText("Categorias: Todas as categorias");

            llChipsFiltroCategoria.addView(criarChip("Todas as categorias", () -> {
                leituraTodasCategorias = false;
                atualizarChipsFiltroAtivo();
                reconstruirLista();
                atualizarContadoresChips();
            }));
            return;
        }

        if (!filtroCategoriaAtivo()) {
            llChipsFiltroCategoria.setVisibility(View.GONE);
            txtBotaoFiltroCategoria.setText("Selecionar categorias (obrigatório)");
            return;
        }

        llChipsFiltroCategoria.setVisibility(View.VISIBLE);
        txtBotaoFiltroCategoria.setText(
                "Filtro ativo: " + categoriasFiltro.size() + " categoria(s)");

        List<String> ordenadas = new ArrayList<>(categoriasFiltro);
        Collections.sort(ordenadas, String.CASE_INSENSITIVE_ORDER);

        for (String categoria : ordenadas) {
            llChipsFiltroCategoria.addView(criarChip(categoria, () -> {
                categoriasFiltro.remove(categoria);
                atualizarChipsFiltroAtivo();
                reconstruirLista();
                atualizarContadoresChips();
            }));
        }
    }

    /**
     * Abre o diálogo com busca + lista de categorias marcáveis. A seleção
     * só é aplicada de fato quando o usuário toca "Aplicar" — cancelar
     * descarta qualquer alteração feita no diálogo.
     */
    private void abrirDialogFiltroCategorias() {
        List<String> categorias = new ArrayList<>(mapaCompleto.keySet());
        Collections.sort(categorias, String.CASE_INSENSITIVE_ORDER);

        if (categorias.isEmpty()) {
            Toast.makeText(this, "Categorias ainda carregando, tente novamente em instantes.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_filtro_categorias, null);

        EditText etBuscaCategoria = view.findViewById(R.id.etBuscaFiltroCategoria);
        RecyclerView rvCategoriasFiltro = view.findViewById(R.id.rvFiltroCategorias);
        TextView txtQtdSelecionadas = view.findViewById(R.id.txtQtdSelecionadasFiltro);
        TextView btnLimparSelecao = view.findViewById(R.id.btnLimparSelecaoFiltro);

        int totalItensLocal = 0;
        for (List<Patrimonio> itens : mapaCompleto.values()) {
            totalItensLocal += itens.size();
        }

        // Cópias de trabalho: só viram estado de fato ao Aplicar.
        Set<String> selecaoTemporaria = new HashSet<>(categoriasFiltro);
        AtomicBoolean todasSelecionadaRef = new AtomicBoolean(leituraTodasCategorias);

        final Runnable[] atualizarLegendaRef = new Runnable[1];

        FiltroCategoriaAdapter adapterFiltro = new FiltroCategoriaAdapter(
                categorias, mapaCompleto, selecaoTemporaria, todasSelecionadaRef, totalItensLocal,
                () -> atualizarLegendaRef[0].run());

        atualizarLegendaRef[0] = () -> {
            if (todasSelecionadaRef.get()) {
                txtQtdSelecionadas.setText("Todas as categorias selecionadas");
            } else if (selecaoTemporaria.isEmpty()) {
                txtQtdSelecionadas.setText(
                        "Nada selecionado ainda — escolha \"Todas as categorias\" ou específicas");
            } else {
                txtQtdSelecionadas.setText(selecaoTemporaria.size() + " categoria(s) selecionada(s)");
            }
        };

        atualizarLegendaRef[0].run();

        rvCategoriasFiltro.setLayoutManager(new LinearLayoutManager(this));
        rvCategoriasFiltro.setAdapter(adapterFiltro);

        etBuscaCategoria.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapterFiltro.filtrar(normalizarTexto(s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnLimparSelecao.setOnClickListener(v -> {
            selecaoTemporaria.clear();
            todasSelecionadaRef.set(false);
            adapterFiltro.notifyDataSetChanged();
            atualizarLegendaRef[0].run();
        });

        new AlertDialog.Builder(this)
                .setTitle("Filtrar categorias de leitura")
                .setView(view)
                .setPositiveButton("Aplicar", (d, w) -> {
                    boolean todas = todasSelecionadaRef.get();

                    if (!todas && selecaoTemporaria.isEmpty()) {
                        // Nada escolhido: não vira uma seleção válida — o
                        // botão de leitura continua bloqueado até o usuário
                        // realmente decidir algo aqui.
                        Toast.makeText(this,
                                "Escolha \"Todas as categorias\" ou pelo menos uma categoria específica.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    leituraTodasCategorias = todas;
                    categoriasFiltro.clear();

                    if (!todas) {
                        categoriasFiltro.addAll(selecaoTemporaria);
                    }

                    atualizarChipsFiltroAtivo();
                    reconstruirLista();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════
    //  Montagem da lista — ponto único de atualização da tela
    // ═══════════════════════════════════════════════════════════

    private void reconstruirLista() {
        final int versao = ++versaoLista;
        final String termo = queryTexto;
        final FiltroStatus fs = filtroStatus;
        final Set<String> expandidos = new HashSet<>(gruposExpandidos);
        final boolean filtroCategoriaAtivo = filtroCategoriaAtivo();
        final Set<String> categoriasAtivas = new HashSet<>(categoriasFiltro);

        executor.execute(() -> {
            List<Linha> linhas = new ArrayList<>();

            // 1) Divergentes primeiro — é o que precisa ser resolvido.
            // Enquanto o filtro de categoria está ativo, divergentes nunca
            // são registradas (processarTag ignora a tag antes disso), então
            // este bloco fica naturalmente vazio nesse modo.
            boolean mostraDivergentes = !divergentes.isEmpty()
                    && (fs == FiltroStatus.TODOS || fs == FiltroStatus.DIVERGENTES);

            if (mostraDivergentes) {
                boolean exp = expandidos.contains(GRUPO_DIVERGENTES);

                int aceitas = 0;
                for (String chave5 : divergentes.keySet()) {
                    if (divergentesAceitas.contains(chave5)) {
                        aceitas++;
                    }
                }

                linhas.add(Linha.grupo(
                        GRUPO_DIVERGENTES, aceitas, divergentes.size(), exp));

                if (exp) {
                    for (String chave5 : new TreeSet<>(divergentes.keySet())) {
                        InfoDivergente info = divergentes.get(chave5);

                        if (info == null) {
                            continue;
                        }

                        boolean aceita = divergentesAceitas.contains(chave5);

                        String localTexto;

                        if (aceita) {
                            localTexto = "\u2713 Trazido para este local";
                        } else if (info.localOrigem.isEmpty()) {
                            localTexto = "Local de origem desconhecido — toque para trazer";
                        } else {
                            localTexto = info.localOrigem;
                        }

                        linhas.add(Linha.item(
                                GRUPO_DIVERGENTES,
                                chave5,
                                info.codigoExibido,
                                info.descricao,
                                localTexto,
                                aceita ? StatusItem.ENCONTRADO : StatusItem.DIVERGENTE
                        ));
                    }
                }
            }

            // 2) Categorias do local.
            if (fs != FiltroStatus.DIVERGENTES) {
                for (Map.Entry<String, List<Patrimonio>> entrada : mapaCompleto.entrySet()) {
                    String grupo = entrada.getKey();

                    // Categoria fora do filtro de leitura: nem entra na lista.
                    if (filtroCategoriaAtivo && !categoriasAtivas.contains(grupo)) {
                        continue;
                    }

                    if (!termo.isEmpty()
                            && !casaTodasPalavras(grupoNormalizado.get(grupo), termo)) {
                        continue;
                    }

                    List<Patrimonio> itens = entrada.getValue();

                    int lidosGrupo = 0;
                    List<Patrimonio> visiveis = new ArrayList<>(itens.size());

                    for (Patrimonio p : itens) {
                        boolean lido = estaLido(p);

                        if (lido) {
                            lidosGrupo++;
                        }

                        if (fs == FiltroStatus.PENDENTES && lido) continue;
                        if (fs == FiltroStatus.ENCONTRADOS && !lido) continue;

                        visiveis.add(p);
                    }

                    // No modo filtrado, categoria sem item visível some da lista.
                    if (visiveis.isEmpty() && fs != FiltroStatus.TODOS) {
                        continue;
                    }

                    boolean exp = expandidos.contains(grupo);

                    linhas.add(Linha.grupo(grupo, lidosGrupo, itens.size(), exp));

                    if (exp) {
                        for (Patrimonio p : visiveis) {
                            String chave5 = obterChave5DoPatrimonio(p);

                            linhas.add(Linha.item(
                                    grupo,
                                    chave5 != null ? chave5 : p.getCodigoBarra(),
                                    p.getCodigoBarra(),
                                    p.getDescricao(),
                                    null,
                                    obterStatusItem(chave5)
                            ));
                        }
                    }
                }
            }

            mainHandler.post(() -> {
                if (versao != versaoLista) {
                    return;
                }

                adapter.setTermoDestaque(termo);
                adapter.submitList(linhas);

                atualizarContador();
                atualizarContadoresChips();
                atualizarEmptyState(linhas.isEmpty());
            });
        });
    }

    private void atualizarEmptyState(boolean vazio) {
        if (tvEmptyState == null) {
            return;
        }

        if (!vazio) {
            tvEmptyState.setVisibility(View.GONE);
            return;
        }

        if (!queryTexto.isEmpty()) {
            tvEmptyState.setText("Nenhuma categoria encontrada para \""
                    + etBusca.getText().toString().trim() + "\".");
        } else if (filtroCategoriaAtivo()) {
            tvEmptyState.setText("Nenhum item das categorias filtradas neste local.");
        } else if (filtroStatus == FiltroStatus.DIVERGENTES) {
            tvEmptyState.setText("Nenhuma leitura fora do local até agora.");
        } else if (filtroStatus == FiltroStatus.ENCONTRADOS) {
            tvEmptyState.setText("Nenhum item lido ainda.");
        } else if (filtroStatus == FiltroStatus.PENDENTES) {
            tvEmptyState.setText("Todos os itens deste local foram lidos.");
        } else {
            tvEmptyState.setText("Nenhum patrimônio cadastrado neste local.");
        }

        tvEmptyState.setVisibility(View.VISIBLE);
    }

    // ═══════════════════════════════════════════════════════════
    //  Processamento de tags
    // ═══════════════════════════════════════════════════════════

    /**
     * Chamado pelas threads dos leitores. Registra a leitura sempre que a
     * tag passa pelo filtro de categoria, independente de filtro de tela
     * (busca/chips de status), e agenda o redesenho.
     */
    private void processarTag(String codigoMontado, String chave5) {
        String grupo = indiceGrupoPorChave.get(chave5);

        // Filtro de categoria ativo: o leitor "não enxerga" tags fora das
        // categorias selecionadas — nem soma, nem apita, nem entra na lista
        // nem no resumo. Isso inclui tags de outros locais (grupo == null),
        // já que elas por definição não pertencem a nenhuma categoria
        // selecionada deste local.
        if (filtroCategoriaAtivo() && (grupo == null || !categoriasFiltro.contains(grupo))) {
            return;
        }

        // Uma leitura de RFID/2D sempre confirma o item como encontrado,
        // então desfaz qualquer marcação manual de "não identificado".
        naoEncontradosManual.remove(chave5);

        // add devolve false se a chave já estava no Set.
        if (!lidos.add(chave5)) {
            return;
        }

        boolean pertence = grupo != null;

        if (pertence) {
            gruposExpandidos.add(grupo);
        } else {
            Patrimonio global = indiceGlobalPorChave.get(chave5);

            String descricao = global != null && global.getDescricao() != null
                    ? global.getDescricao() : "Tag desconhecida";

            String localOrigem = "";

            if (global != null) {
                localOrigem = global.getNomeLocal() != null ? global.getNomeLocal() : "";

                if (localOrigem.isEmpty() && global.getCodLocal() != null) {
                    localOrigem = global.getCodLocal();
                }
            }

            divergentes.put(chave5, new InfoDivergente(
                    codigoMontado, descricao, localOrigem, global != null));
        }

        dbHelper.salvarHistoricoComTipo(
                codigoFilial, codigoLocal, chapaFuncionario, codigoMontado, "CATEGORIA");

        final boolean grupoCompleto = pertence && grupoTodosLidos(grupo);
        final String grupoFinal = grupo;

        mainHandler.post(() -> {
            if (!pertence) {
                toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 250);
            } else if (grupoCompleto) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200);
                mainHandler.postDelayed(
                        () -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200), 300);

                Toast.makeText(this, "\u2713 \"" + grupoFinal + "\" completo!",
                        Toast.LENGTH_SHORT).show();
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            }
        });

        agendarFlush();
    }

    /**
     * Agrupa várias leituras num único redesenho. Sem isso, uma rajada de
     * 30 tags por segundo dispara 30 reconstruções de lista.
     */
    private void agendarFlush() {
        if (flushAgendado) {
            return;
        }

        flushAgendado = true;

        mainHandler.postDelayed(() -> {
            flushAgendado = false;
            reconstruirLista();
        }, JANELA_FLUSH);
    }

    /**
     * Chamado ao tocar numa tag divergente. Primeiro toque "traz" a tag
     * para este local, mudando o status para verde; um segundo toque
     * desfaz a aceitação, voltando para amarelo.
     */
    private void aceitarDivergente(String chave5) {
        InfoDivergente info = divergentes.get(chave5);

        if (info == null) {
            return;
        }

        if (divergentesAceitas.remove(chave5)) {
            reconstruirLista();
            return;
        }

        divergentesAceitas.add(chave5);

        executor.execute(() -> dbHelper.salvarHistoricoComTipo(
                codigoFilial, codigoLocal, chapaFuncionario,
                info.codigoExibido, "CATEGORIA_ENTRADA"));

        Toast.makeText(this, "Trazido para este local!", Toast.LENGTH_SHORT).show();

        reconstruirLista();
    }

    /**
     * Status atual de um item deste local (grupo != divergentes), levando
     * em conta a marcação manual de "não identificado". Uma leitura real
     * (RFID/2D) sempre tem prioridade e limpa essa marcação — ver
     * processarTag().
     */
    private StatusItem obterStatusItem(String chave5) {
        if (chave5 != null && lidos.contains(chave5)) {
            return StatusItem.ENCONTRADO;
        }

        if (chave5 != null && naoEncontradosManual.contains(chave5)) {
            return StatusItem.NAO_ENCONTRADO;
        }

        return StatusItem.PENDENTE;
    }

    /**
     * Alterna o status de um item ao toque, igual ao InventarioLocalActivity:
     *   ENCONTRADO      -> confirma -> NAO_ENCONTRADO
     *   NAO_ENCONTRADO  -> confirma -> ENCONTRADO
     *   PENDENTE        -> ENCONTRADO direto, sem diálogo
     * Usado só para itens do local (grupos normais) — divergentes continuam
     * usando aceitarDivergente().
     */
    private void alternarStatusManual(String chave5) {
        Patrimonio patrimonio = indicePatrimonioPorChave.get(chave5);

        if (patrimonio == null) {
            return;
        }

        boolean encontrado = lidos.contains(chave5);
        boolean marcadoNaoEncontrado = naoEncontradosManual.contains(chave5);

        String descricao = patrimonio.getDescricao() != null
                ? patrimonio.getDescricao() : "";

        if (encontrado) {
            new AlertDialog.Builder(this)
                    .setTitle("Alterar status")
                    .setMessage(
                            "Deseja marcar este patrimônio como não identificado?\n\n"
                                    + patrimonio.getCodigoBarra() + "\n" + descricao)
                    .setPositiveButton("Sim", (d, w) -> {
                        lidos.remove(chave5);
                        naoEncontradosManual.add(chave5);

                        reconstruirLista();

                        Toast.makeText(this, "Marcado como não identificado",
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();

        } else if (marcadoNaoEncontrado) {
            new AlertDialog.Builder(this)
                    .setTitle("Alterar status")
                    .setMessage(
                            "Deseja marcar este patrimônio como identificado?\n\n"
                                    + patrimonio.getCodigoBarra() + "\n" + descricao)
                    .setPositiveButton("Sim", (d, w) -> {
                        naoEncontradosManual.remove(chave5);
                        lidos.add(chave5);

                        String grupo = indiceGrupoPorChave.get(chave5);
                        if (grupo != null) {
                            gruposExpandidos.add(grupo);
                        }

                        reconstruirLista();

                        Toast.makeText(this, "Marcado como identificado",
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();

        } else {
            // PENDENTE — marca direto, sem diálogo.
            lidos.add(chave5);

            String grupo = indiceGrupoPorChave.get(chave5);
            if (grupo != null) {
                gruposExpandidos.add(grupo);
            }

            reconstruirLista();

            Toast.makeText(this, "Marcado como identificado",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean estaLido(Patrimonio patrimonio) {
        String chave5 = obterChave5DoPatrimonio(patrimonio);
        return chave5 != null && lidos.contains(chave5);
    }

    private boolean grupoTodosLidos(String descricaoGrupo) {
        List<Patrimonio> itens = mapaCompleto.get(descricaoGrupo);

        if (itens == null || itens.isEmpty()) {
            return false;
        }

        for (Patrimonio patrimonio : itens) {
            if (!estaLido(patrimonio)) {
                return false;
            }
        }

        return true;
    }

    private void atualizarContador() {
        int total = 0;
        int totalLidos = 0;

        for (Map.Entry<String, List<Patrimonio>> entrada : mapaCompleto.entrySet()) {
            if (filtroCategoriaAtivo() && !categoriasFiltro.contains(entrada.getKey())) {
                continue;
            }

            for (Patrimonio patrimonio : entrada.getValue()) {
                total++;

                if (estaLido(patrimonio)) {
                    totalLidos++;
                }
            }
        }

        StringBuilder texto = new StringBuilder();
        texto.append("Lidos: ").append(totalLidos).append(" / ").append(total);

        if (!divergentes.isEmpty()) {
            texto.append("  \u2022  Fora do local: ").append(divergentes.size());
        }

        if (filtroCategoriaAtivo()) {
            texto.append("  \u2022  ").append(categoriasFiltro.size()).append(" categoria(s) filtrada(s)");
        }

        tvContador.setText(texto.toString());

        tvContador.setTextColor(
                total > 0 && totalLidos == total
                        ? Color.parseColor("#2E7D32")
                        : Color.parseColor("#333333")
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  Listeners dos botões
    // ═══════════════════════════════════════════════════════════

    private void configurarListeners() {
        btnLer.setOnClickListener(v -> alternarLeitura());
        btnModoToggle.setOnClickListener(v -> trocarModo());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnFiltrarCategorias.setOnClickListener(v -> abrirDialogFiltroCategorias());

        btnHistorico.setOnClickListener(v ->
                startActivity(new Intent(this, HistoricoActivity.class)));

        btnConcluir.setOnClickListener(v -> {
            List<Patrimonio> lidosList = coletarLidosDoLocal();

            if (!divergentes.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Leituras fora do local")
                        .setMessage(divergentes.size()
                                + " tag(s) lida(s) não pertencem a este local.\n\n"
                                + "Deseja concluir mesmo assim?")
                        .setPositiveButton("Concluir", (d, w) -> concluir(lidosList))
                        .setNegativeButton("Revisar", (d, w) ->
                                aplicarFiltro(FiltroStatus.DIVERGENTES))
                        .show();
                return;
            }

            concluir(lidosList);
        });

        btnLimparTags.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Limpar leituras")
                        .setMessage("Deseja apagar todas as tags lidas desta sessão?")
                        .setPositiveButton("Limpar", (dialog, which) -> {
                            lidos.clear();
                            divergentes.clear();
                            divergentesAceitas.clear();
                            naoEncontradosManual.clear();
                            reconstruirLista();

                            Toast.makeText(this, "Leituras limpas!",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show());
    }

    private void concluir(List<Patrimonio> lidosList) {
        ConcluirHelper.executarPatrimonios(
                this, executor, codigoFilial, codigoLocal,
                chapaFuncionario, "CATEGORIA", lidosList);
    }

    /**
     * Patrimônios lidos deste local. Quando o filtro de categoria está
     * ativo, só considera as categorias filtradas — o que na prática já é
     * automático (categorias fora do filtro nunca chegam a "lidos" em
     * processarTag), mas a checagem explícita evita surpresas se o filtro
     * mudar no meio de uma sessão que já tinha leituras.
     */
    private List<Patrimonio> coletarLidosDoLocal() {
        List<Patrimonio> resultado = new ArrayList<>();

        for (Map.Entry<String, List<Patrimonio>> entrada : mapaCompleto.entrySet()) {
            if (filtroCategoriaAtivo() && !categoriasFiltro.contains(entrada.getKey())) {
                continue;
            }

            for (Patrimonio patrimonio : entrada.getValue()) {
                if (estaLido(patrimonio)) {
                    resultado.add(patrimonio);
                }
            }
        }

        return resultado;
    }

    // ═══════════════════════════════════════════════════════════
    //  Modo RFID / Código de Barras
    // ═══════════════════════════════════════════════════════════

    private void trocarModo() {
        if (modoRfid) {
            pararLeituraRFID();
        } else {
            pararLeitura2D();
        }

        modoRfid = !modoRfid;

        atualizarBotaoModo();

        Toast.makeText(this, "Modo: " + (modoRfid ? "RFID" : "Código de Barras"),
                Toast.LENGTH_SHORT).show();
    }

    private void atualizarBotaoModo() {
        if (modoRfid) {
            btnModoToggle.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#005eb8")));
            txtModoToggle.setText("Modo: RFID");
        } else {
            btnModoToggle.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#388E3C")));
            txtModoToggle.setText("Modo: Cód. Barras");
        }
    }

    /**
     * Chamado pelo botão de leitura e pelo gatilho físico. Nunca inicia a
     * leitura sem uma escolha de categoria válida — nem "Todas as
     * categorias" por padrão, nem nenhuma categoria específica: o usuário
     * precisa ter confirmado uma das duas no diálogo de filtro.
     */
    private void alternarLeitura() {
        if (!selecaoDeLeituraValida()) {
            new AlertDialog.Builder(this)
                    .setTitle("Selecione uma categoria")
                    .setMessage("Escolha \"Todas as categorias\" ou uma categoria específica antes de iniciar a leitura.")
                    .setPositiveButton("Selecionar agora", (d, w) -> abrirDialogFiltroCategorias())
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        if (modoRfid) {
            if (isReadingRFID) {
                pararLeituraRFID();
            } else {
                iniciarLeituraRFID();
            }
        } else {
            if (isReading2D) {
                pararLeitura2D();
            } else {
                iniciarLeitura2D();
            }
        }
    }

    // ── RFID ──────────────────────────────────────────────────

    private void inicializarRFID() {
        rfidExecutor.execute(() -> {
            try {
                mReader = RFIDWithUHFUART.getInstance();

                if (mReader != null && mReader.init(this)) {
                    mainHandler.post(() -> Toast.makeText(
                            this, "Leitor RFID pronto", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar RFID", e);
            }
        });
    }

    /**
     * O loop inteiro roda na thread do rfidExecutor. A versão antiga
     * reagendava com mainHandler.postDelayed, o que jogava o
     * readTagFromBuffer() para a UI thread — principal causa do travamento.
     */
    private void iniciarLeituraRFID() {
        if (isReadingRFID || mReader == null) {
            return;
        }

        isReadingRFID = true;
        txtBotao.setText("Parar Leitura");

        rfidExecutor.execute(() -> {
            try {
                mReader.startInventoryTag();

                while (isReadingRFID) {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();

                    if (tagInfo == null) {
                        Thread.sleep(30);
                        continue;
                    }

                    String normalizado = normalizarCodigo(tagInfo.getEPC());

                    if (normalizado.length() >= 5) {
                        String chave5 = normalizado.substring(0, 5);
                        processarTag("040" + chave5, chave5);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "Erro no loop RFID", e);
            } finally {
                try {
                    if (mReader != null) {
                        mReader.stopInventory();
                    }
                } catch (Exception ignored) {
                }

                isReadingRFID = false;
                mainHandler.post(() -> txtBotao.setText("Iniciar Leitura"));
            }
        });
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        txtBotao.setText("Iniciar Leitura");
    }

    // ── Código de barras 2D ───────────────────────────────────

    private void inicializarBarcode2D() {
        barcodeExecutor.execute(() -> {
            try {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();

                if (barcodeDecoder.open(this)) {
                    barcodeDecoder.setDecodeCallback(entity -> {
                        if (entity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) {
                            return;
                        }

                        String normalizado = normalizarCodigo(entity.getBarcodeData());

                        if (normalizado.length() < 5) {
                            return;
                        }

                        String chave5 = normalizado.substring(0, 5);

                        barcodeExecutor.execute(() ->
                                processarTag("040" + chave5, chave5));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar leitor 2D", e);
            }
        });
    }

    private void iniciarLeitura2D() {
        if (isReading2D || barcodeDecoder == null) {
            return;
        }

        isReading2D = true;
        txtBotao.setText("Parar Leitura");

        try {
            barcodeDecoder.startScan();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar leitura 2D", e);
            isReading2D = false;
            txtBotao.setText("Iniciar Leitura");
        }
    }

    private void pararLeitura2D() {
        if (barcodeDecoder == null) {
            return;
        }

        isReading2D = false;
        txtBotao.setText("Iniciar Leitura");

        try {
            barcodeDecoder.stopScan();
        } catch (Exception ignored) {
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Distância e resumo
    // ═══════════════════════════════════════════════════════════

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};

        new AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    final int power = which == 0 ? 10 : (which == 1 ? 20 : 30);

                    rfidExecutor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power)) {
                                mainHandler.post(() -> Toast.makeText(
                                        this, "Potência: " + power + " dBm",
                                        Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao ajustar potência", e);
                        }
                    });
                })
                .show();
    }

    private void abrirResumo() {
        String termoAtual = queryTexto; // já normalizado pela busca

        List<Patrimonio> lidosList = coletarLidosDoLocal();
        Map<String, InfoDivergente> divergentesParaResumo = divergentes;

        if (!termoAtual.isEmpty()) {
            // Só entra no resumo o que pertence à categoria pesquisada.
            List<Patrimonio> lidosFiltrados = new ArrayList<>();
            for (Patrimonio p : lidosList) {
                String chave5 = obterChave5DoPatrimonio(p);
                String grupo = chave5 != null ? indiceGrupoPorChave.get(chave5) : null;

                if (grupo != null && casaTodasPalavras(grupoNormalizado.get(grupo), termoAtual)) {
                    lidosFiltrados.add(p);
                }
            }
            lidosList = lidosFiltrados;

            Map<String, InfoDivergente> divFiltrados = new HashMap<>();
            for (Map.Entry<String, InfoDivergente> e : divergentes.entrySet()) {
                InfoDivergente info = e.getValue();

                if (!info.identificada) {
                    continue;
                }

                String descNormalizada = normalizarTexto(info.descricao);
                if (casaTodasPalavras(descNormalizada, termoAtual)) {
                    divFiltrados.put(e.getKey(), info);
                }
            }
            divergentesParaResumo = divFiltrados;
        }

        if (lidosList.isEmpty() && divergentesParaResumo.isEmpty()) {
            Toast.makeText(
                    this,
                    termoAtual.isEmpty()
                            ? "Nenhum item lido ainda!"
                            : "Nenhum item lido para \"" + etBusca.getText().toString().trim() + "\"",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String nomeLocalAtual = localBanco != null
                ? localBanco.getLocalNome()
                : codigoLocal;

        ArrayList<String> tagsLidas = new ArrayList<>();
        ArrayList<String> descricoesLidas = new ArrayList<>();
        ArrayList<Boolean> itensPertencemAoLocal = new ArrayList<>();

        // =========================================================
        // ITENS IDENTIFICADOS / PERTENCENTES AO LOCAL
        // =========================================================
        for (Patrimonio patrimonio : lidosList) {

            tagsLidas.add(patrimonio.getCodigoBarra());

            String descricao = patrimonio.getDescricao() != null
                    && !patrimonio.getDescricao().trim().isEmpty()
                    ? patrimonio.getDescricao()
                    : "DESCONHECIDO";

            // Descrição completa na primeira linha
            // Local completo na segunda linha
            descricoesLidas.add(
                    descricao + "\n" +
                            "Local: " + nomeLocalAtual
            );

            // TRUE = pertence ao local atual
            itensPertencemAoLocal.add(true);
        }

        // =========================================================
        // TAGS LIDAS QUE NÃO PERTENCEM A ESTE LOCAL
        // =========================================================
        for (InfoDivergente info : divergentesParaResumo.values()) {

            tagsLidas.add(info.codigoExibido);

            String descricao = info.descricao != null
                    && !info.descricao.trim().isEmpty()
                    ? info.descricao
                    : "DESCONHECIDO";

            String local = info.localOrigem != null
                    && !info.localOrigem.trim().isEmpty()
                    ? info.localOrigem
                    : "Local desconhecido";

            // Descrição completa na primeira linha
            // Local de origem completo na segunda linha
            descricoesLidas.add(
                    descricao + "\n" +
                            "Local: " + local
            );

            // FALSE = NÃO pertence ao local atual
            itensPertencemAoLocal.add(false);
        }

        // =========================================================
        // ABRIR RESUMO
        // =========================================================
        Intent intent = new Intent(this, ResumoActivity.class);

        intent.putStringArrayListExtra(
                "tags",
                tagsLidas
        );

        intent.putStringArrayListExtra(
                "descricoes",
                descricoesLidas
        );

        // IMPORTANTE:
        // Informa ao ResumoActivity quais itens pertencem
        // ao local e quais são divergentes.
        intent.putExtra(
                "itensPertencemAoLocal",
                itensPertencemAoLocal
        );

        intent.putExtra(
                "codigoFilial",
                codigoFilial
        );

        intent.putExtra(
                "codigoLocal",
                codigoLocal
        );

        intent.putExtra(
                "chapaFuncionario",
                chapaFuncionario
        );

        intent.putExtra(
                "nomeUsuario",
                userBanco != null
                        ? userBanco.getNome()
                        : ""
        );

        intent.putExtra(
                "nomeLocal",
                nomeLocalAtual
        );

        // =========================================================
        // QUANTIDADES
        // =========================================================

        // Total geral
        intent.putExtra(
                "totalItensLidos",
                tagsLidas.size()
        );

        // Itens que pertencem ao local
        intent.putExtra(
                "totalIdentificados",
                lidosList.size()
        );

        // Itens que não pertencem ao local
        intent.putExtra(
                "totalDivergentes",
                divergentesParaResumo.size()
        );

        startActivity(intent);
    }
    // ═══════════════════════════════════════════════════════════
    //  Gatilho físico
    // ═══════════════════════════════════════════════════════════

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == 293 && event.getAction() == KeyEvent.ACTION_DOWN) {
            alternarLeitura();
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    // ═══════════════════════════════════════════════════════════
    //  Utilitários de texto e código
    // ═══════════════════════════════════════════════════════════

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }

        String codigo = valor.trim();

        if (codigo.equalsIgnoreCase("null") || codigo.isEmpty()) {
            return "";
        }

        if (codigo.startsWith("040") && codigo.length() > 3) {
            codigo = codigo.substring(3);
        } else if (codigo.startsWith("40") && codigo.length() > 2) {
            codigo = codigo.substring(2);
        }

        return codigo.replaceFirst("^0+", "");
    }

    /** Remove acentos e joga para minúsculo: "Cadeira Giratória" -> "cadeira giratoria". */
    static String normalizarTexto(String texto) {
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
     * "cadeira giratoria" casa com "Cadeira Giratória Preta" e com "Giratória Cadeira".
     */
    static boolean casaTodasPalavras(String alvoNormalizado, String termoNormalizado) {
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

    /** Pinta de amarelo cada ocorrência das palavras do termo dentro do texto. */
    static SpannableString destacar(String texto, String termoNormalizado) {
        if (texto == null) {
            texto = "";
        }

        SpannableString spannable = new SpannableString(texto);

        if (termoNormalizado == null || termoNormalizado.isEmpty()) {
            return spannable;
        }

        String base = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        // Comprimento diferente significa caractere fora do padrão: pula o destaque.
        if (base.length() != texto.length()) {
            return spannable;
        }

        for (String palavra : termoNormalizado.split("\\s+")) {
            if (palavra.isEmpty()) {
                continue;
            }

            int inicio = base.indexOf(palavra);

            while (inicio >= 0) {
                spannable.setSpan(
                        new BackgroundColorSpan(Color.parseColor("#FFF176")),
                        inicio,
                        inicio + palavra.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                inicio = base.indexOf(palavra, inicio + palavra.length());
            }
        }

        return spannable;
    }

    // ═══════════════════════════════════════════════════════════
    //  Adapter do RecyclerView (lista principal de categorias)
    // ═══════════════════════════════════════════════════════════

    static class InventarioAdapter extends ListAdapter<Linha, RecyclerView.ViewHolder> {

        interface OnGrupoClick {
            void clicou(String descricaoGrupo);
        }

        interface OnDivergenteClick {
            void aceitar(String chave5);
        }

        /** Toque num item do local (não divergente) para alternar o status manualmente. */
        interface OnItemClick {
            void clicou(String chave5);
        }

        private static final int FUNDO_VERDE = 0xFFE8F5E9;
        private static final int TEXTO_VERDE_FORTE = 0xFF1B5E20;
        private static final int TEXTO_VERDE = 0xFF2E7D32;

        private static final int FUNDO_AMARELO = 0xFFFFF8E1;
        private static final int TEXTO_LARANJA_FORTE = 0xFFE65100;
        private static final int TEXTO_AMARELO = 0xFFF57F17;

        private static final int FUNDO_BRANCO = 0xFFFFFFFF;
        private static final int TEXTO_CINZA_FORTE = 0xFF9E9E9E;
        private static final int TEXTO_CINZA = 0xFFBDBDBD;

        private static final int FUNDO_GRUPO_NEUTRO = 0xFFEFEFF4;
        private static final int TEXTO_GRUPO_NEUTRO = 0xFF212121;

        private final OnGrupoClick callback;
        private final OnDivergenteClick divergenteCallback;
        private final OnItemClick itemClickCallback;
        private String termoDestaque = "";

        InventarioAdapter(OnGrupoClick callback,
                          OnDivergenteClick divergenteCallback,
                          OnItemClick itemClickCallback) {
            super(DIFF);
            this.callback = callback;
            this.divergenteCallback = divergenteCallback;
            this.itemClickCallback = itemClickCallback;
            setHasStableIds(true);
        }

        private static final DiffUtil.ItemCallback<Linha> DIFF =
                new DiffUtil.ItemCallback<Linha>() {

                    @Override
                    public boolean areItemsTheSame(@NonNull Linha a, @NonNull Linha b) {
                        return a.id.equals(b.id);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull Linha a, @NonNull Linha b) {
                        if (a.tipo != b.tipo) {
                            return false;
                        }

                        if (a.tipo == Linha.TIPO_GRUPO) {
                            return a.lidosGrupo == b.lidosGrupo
                                    && a.totalGrupo == b.totalGrupo
                                    && a.expandido == b.expandido;
                        }

                        return a.status == b.status
                                && a.descricao.equals(b.descricao)
                                && java.util.Objects.equals(a.localTexto, b.localTexto);
                    }
                };

        void setTermoDestaque(String termo) {
            this.termoDestaque = termo == null ? "" : termo;
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id.hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).tipo;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int tipo) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            return tipo == Linha.TIPO_GRUPO
                    ? new GrupoVH(inflater.inflate(
                    R.layout.item_grupo_categoria, parent, false))
                    : new ItemVH(inflater.inflate(
                    R.layout.item_patrimonio, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Linha linha = getItem(position);

            if (holder instanceof GrupoVH) {
                ((GrupoVH) holder).bind(linha, termoDestaque, callback);
            } else {
                ((ItemVH) holder).bind(linha, termoDestaque, divergenteCallback, itemClickCallback);
            }
        }

        static class GrupoVH extends RecyclerView.ViewHolder {

            final TextView txtDescricao, txtContador;
            final ImageView imgSeta;

            GrupoVH(@NonNull View itemView) {
                super(itemView);
                txtDescricao = itemView.findViewById(R.id.txtGrupoDescricao);
                txtContador = itemView.findViewById(R.id.txtGrupoContador);
                imgSeta = itemView.findViewById(R.id.imgSetaGrupo);
            }

            void bind(Linha linha, String termo, OnGrupoClick callback) {
                txtDescricao.setText(destacar(linha.grupo, termo));
                txtContador.setText(linha.lidosGrupo + "/" + linha.totalGrupo);

                boolean completo = linha.totalGrupo > 0
                        && linha.lidosGrupo == linha.totalGrupo;

                if (completo) {
                    itemView.setBackgroundColor(FUNDO_VERDE);
                    txtDescricao.setTextColor(TEXTO_VERDE_FORTE);
                    txtContador.setBackgroundResource(R.drawable.bg_badge_verde);
                } else {
                    itemView.setBackgroundColor(FUNDO_GRUPO_NEUTRO);
                    txtDescricao.setTextColor(TEXTO_GRUPO_NEUTRO);
                    txtContador.setBackgroundResource(R.drawable.bg_badge_azul);
                }

                txtContador.setTextColor(Color.WHITE);
                imgSeta.setRotation(linha.expandido ? 180f : 0f);

                itemView.setOnClickListener(v -> callback.clicou(linha.grupo));
            }
        }

        static class ItemVH extends RecyclerView.ViewHolder {

            final TextView txtCodigo, txtDescricao, txtLocal;
            final ImageView imgStatus;

            // =========================================================
            // PADRÃO DE CORES
            // =========================================================

            // VERDE — encontrado
            private static final int FUNDO_VERDE = Color.parseColor("#E8F5E9");
            private static final int TEXTO_VERDE_FORTE = Color.parseColor("#1B5E20");
            private static final int TEXTO_VERDE = Color.parseColor("#2E7D32");

            // CINZA — pendente / ainda não lido
            private static final int FUNDO_CINZA = Color.parseColor("#F5F5F5");
            private static final int TEXTO_CINZA_FORTE = Color.parseColor("#9E9E9E");
            private static final int TEXTO_CINZA = Color.parseColor("#757575");

            // AMARELO — divergente / fora do local
            private static final int FUNDO_AMARELO = Color.parseColor("#FFFDE7");
            private static final int TEXTO_LARANJA_FORTE = Color.parseColor("#F57F17");
            private static final int TEXTO_LARANJA = Color.parseColor("#E65100");

            // VERMELHO — marcado manualmente como não identificado
            private static final int FUNDO_VERMELHO = Color.parseColor("#FFEBEE");
            private static final int TEXTO_VERMELHO_FORTE = Color.parseColor("#B71C1C");
            private static final int TEXTO_VERMELHO = Color.parseColor("#C62828");

            ItemVH(@NonNull View itemView) {
                super(itemView);

                txtCodigo = itemView.findViewById(R.id.txtItemCodigo);
                txtDescricao = itemView.findViewById(R.id.txtItemDescricao);
                txtLocal = itemView.findViewById(R.id.txtItemLocal);
                imgStatus = itemView.findViewById(R.id.imgPatrimonio);
            }

            void bind(
                    Linha linha,
                    String termo,
                    OnDivergenteClick divergenteCallback,
                    OnItemClick itemClickCallback
            ) {

                // =====================================================
                // DADOS
                // =====================================================

                txtCodigo.setText(linha.codigo);

                txtDescricao.setText(
                        destacar(linha.descricao, termo)
                );

                // =====================================================
                // TEXTO DO LOCAL
                // =====================================================

                if (linha.localTexto != null
                        && !linha.localTexto.isEmpty()) {

                    txtLocal.setText(linha.localTexto);
                    txtLocal.setVisibility(View.VISIBLE);

                } else {

                    txtLocal.setText("");
                    txtLocal.setVisibility(View.GONE);
                }

                // =====================================================
                // IMPORTANTE
                // O ícone NÃO recebe tint.
                // Cada drawable mantém sua própria cor.
                // =====================================================

                imgStatus.clearColorFilter();

                // =====================================================
                // ENCONTRADO
                // =====================================================

                if (linha.status == StatusItem.ENCONTRADO) {

                    itemView.setBackgroundColor(FUNDO_VERDE);

                    txtCodigo.setTextColor(TEXTO_VERDE_FORTE);
                    txtDescricao.setTextColor(TEXTO_VERDE);
                    txtLocal.setTextColor(TEXTO_VERDE);

                    imgStatus.setImageResource(
                            R.drawable.ic_ativo_pat
                    );

                    if (GRUPO_DIVERGENTES.equals(linha.grupo)) {
                        // Item que veio do bloco de divergentes e foi aceito.
                        itemView.setOnClickListener(
                                v -> divergenteCallback.aceitar(linha.chave5)
                        );
                    } else {
                        // Item do local, encontrado: toque pede confirmação
                        // para marcar como não identificado.
                        itemView.setOnClickListener(
                                v -> itemClickCallback.clicou(linha.chave5)
                        );
                    }

                    return;
                }

                // =====================================================
                // DIVERGENTE
                // =====================================================

                if (linha.status == StatusItem.DIVERGENTE) {

                    itemView.setBackgroundColor(FUNDO_AMARELO);

                    txtCodigo.setTextColor(TEXTO_LARANJA);
                    txtDescricao.setTextColor(TEXTO_LARANJA);
                    txtLocal.setTextColor(TEXTO_LARANJA);

                    imgStatus.setImageResource(
                            R.drawable.ic_desconhecido
                    );

                    // Divergente pode ser tocado para aceitar.
                    itemView.setOnClickListener(
                            v -> divergenteCallback.aceitar(linha.chave5)
                    );

                    return;
                }

                // =====================================================
                // NÃO IDENTIFICADO (marcado manualmente)
                // =====================================================

                if (linha.status == StatusItem.NAO_ENCONTRADO) {

                    itemView.setBackgroundColor(FUNDO_VERMELHO);

                    txtCodigo.setTextColor(TEXTO_VERMELHO_FORTE);
                    txtDescricao.setTextColor(TEXTO_VERMELHO);
                    txtLocal.setTextColor(TEXTO_VERMELHO);

                    imgStatus.setImageResource(
                            R.drawable.ic_desconhecido
                    );

                    // Toque pede confirmação para voltar a identificado.
                    itemView.setOnClickListener(
                            v -> itemClickCallback.clicou(linha.chave5)
                    );

                    return;
                }

                // =====================================================
                // PENDENTE
                // =====================================================

                itemView.setBackgroundColor(FUNDO_CINZA);

                txtCodigo.setTextColor(TEXTO_CINZA_FORTE);
                txtDescricao.setTextColor(TEXTO_CINZA);
                txtLocal.setTextColor(TEXTO_CINZA);

                imgStatus.setImageResource(
                        R.drawable.ic_loading
                );

                // Pendente: toque marca como identificado direto.
                itemView.setOnClickListener(
                        v -> itemClickCallback.clicou(linha.chave5)
                );
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Adapter do diálogo de filtro de categorias
    // ═══════════════════════════════════════════════════════════

    /**
     * Lista marcável de categorias, usada só dentro do diálogo de filtro.
     * Independente do InventarioAdapter principal: aqui não há status de
     * leitura, só nome da categoria + quantidade de itens e um checkbox.
     */
    static class FiltroCategoriaAdapter extends RecyclerView.Adapter<FiltroCategoriaAdapter.VH> {

        interface OnSelecaoMudou {
            void mudou();
        }

        private final List<String> todasCategorias;
        private final Map<String, List<Patrimonio>> mapaCompleto;
        private final Set<String> selecionadas;
        private final AtomicBoolean todasSelecionadaRef;
        private final int totalItensLocal;
        private final OnSelecaoMudou callback;

        private List<String> exibidas;

        FiltroCategoriaAdapter(List<String> todasCategorias,
                               Map<String, List<Patrimonio>> mapaCompleto,
                               Set<String> selecionadas,
                               AtomicBoolean todasSelecionadaRef,
                               int totalItensLocal,
                               OnSelecaoMudou callback) {
            this.todasCategorias = todasCategorias;
            this.mapaCompleto = mapaCompleto;
            this.selecionadas = selecionadas;
            this.todasSelecionadaRef = todasSelecionadaRef;
            this.totalItensLocal = totalItensLocal;
            this.callback = callback;
            this.exibidas = todasCategorias;
        }

        /** Reaplica o texto de busca (já normalizado) e redesenha. */
        void filtrar(String termoNormalizado) {
            if (termoNormalizado == null || termoNormalizado.isEmpty()) {
                exibidas = todasCategorias;
            } else {
                List<String> resultado = new ArrayList<>();

                for (String categoria : todasCategorias) {
                    if (casaTodasPalavras(normalizarTexto(categoria), termoNormalizado)) {
                        resultado.add(categoria);
                    }
                }

                exibidas = resultado;
            }

            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_categoria_filtro, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // Posição 0 é fixa: "Todas as categorias", sempre visível,
            // não entra no filtro de busca e é mutuamente exclusiva com
            // qualquer categoria específica marcada.
            if (position == 0) {
                holder.checkBox.setOnCheckedChangeListener(null);
                holder.txtNome.setText("Todas as categorias");
                holder.txtQtd.setText(totalItensLocal + " item(ns) no total do local");
                holder.checkBox.setChecked(todasSelecionadaRef.get());

                holder.itemView.setOnClickListener(v -> holder.checkBox.toggle());

                holder.checkBox.setOnCheckedChangeListener((btn, marcado) -> {
                    todasSelecionadaRef.set(marcado);

                    if (marcado) {
                        selecionadas.clear();
                    }

                    notifyDataSetChanged();
                    callback.mudou();
                });
                return;
            }

            String categoria = exibidas.get(position - 1);
            List<Patrimonio> itens = mapaCompleto.get(categoria);
            int qtd = itens != null ? itens.size() : 0;

            // Remove o listener antes de setChecked para não disparar o
            // callback de seleção durante o simples bind/reciclagem da view.
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.txtNome.setText(categoria);
            holder.txtQtd.setText(qtd + " item(ns)");
            holder.checkBox.setChecked(!todasSelecionadaRef.get() && selecionadas.contains(categoria));

            holder.itemView.setOnClickListener(v -> holder.checkBox.toggle());

            holder.checkBox.setOnCheckedChangeListener((btn, marcado) -> {
                if (marcado) {
                    // Marcar uma categoria específica desfaz "Todas as
                    // categorias", já que as duas são mutuamente exclusivas.
                    todasSelecionadaRef.set(false);
                    selecionadas.add(categoria);
                    notifyItemChanged(0);
                } else {
                    selecionadas.remove(categoria);
                }
                callback.mudou();
            });
        }

        @Override
        public int getItemCount() {
            return 1 + exibidas.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final CheckBox checkBox;
            final TextView txtNome, txtQtd;

            VH(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.chkCategoriaFiltro);
                txtNome = itemView.findViewById(R.id.txtNomeCategoriaFiltro);
                txtQtd = itemView.findViewById(R.id.txtQtdCategoriaFiltro);
            }
        }
    }
}