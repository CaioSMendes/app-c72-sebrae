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
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.model.Local;
import com.example.uhf.model.Patrimonio;
import com.example.uhf.model.Usuario;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventarioLocalActivity extends AppCompatActivity {

    private static final String TAG = "InventarioLocal";

    private static final int ESTADO_PENDENTE = 0;
    private static final int ESTADO_ENCONTRADO = 1;
    private static final int ESTADO_NAO_ENCONTRADO = 2;

    private static final int FILTRO_TODOS = 0;
    private static final int FILTRO_IDENTIFICADOS = 1;
    private static final int FILTRO_NAO_IDENTIFICADOS = 2;
    private static final int FILTRO_FORA_DO_LOCAL = 3;

    private int filtroAtual = FILTRO_TODOS;
    private String textoBuscaAtual = "";

    private RFIDWithUHFUART mReader;
    private BarcodeDecoder barcodeDecoder;

    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D = false;
    private volatile boolean modoRfid = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGen;

    private DBHelper dbHelper;
    private String codigoFilial;
    private String codigoLocal;
    private String chapaFuncionario;

    private Local localBanco;
    private Usuario userBanco;

    private List<Patrimonio> todosPatrimonios = new ArrayList<>();
    private final List<Patrimonio> listaFiltrada = new ArrayList<>();

    private final Map<String, Integer> estadoPatrimonios = new HashMap<>();

    // Últimos patrimônios lidos ficam no topo.
    private final List<String> ordemLeitura = new ArrayList<>();

    // Tags lidas que não pertencem ao local atual.
    private final LinkedHashMap<String, InfoTagFora> tagsForaDoLocal =
            new LinkedHashMap<>();

    // Tags fora do local aceitas manualmente.
    private final Set<String> tagForaAceitas = new HashSet<>();

    /** chave5 -> patrimônio deste local. Evita varrer todosPatrimonios a cada tag lida. */
    private final Map<String, Patrimonio> indicePatrimonioPorChave = new HashMap<>();

    /**
     * chave5 -> patrimônio em qualquer local. Montado uma única vez em
     * background, só para descrever tags "fora do local" sem bater no
     * banco a cada leitura.
     */
    private final Map<String, Patrimonio> indiceGlobalPorChave = new HashMap<>();

    /** Espera para agrupar leituras em rajada antes de redesenhar a lista. */
    private static final long JANELA_FLUSH = 250L;

    private volatile boolean flushAgendado = false;
    private volatile boolean pendenteScrollIdentificado = false;
    private volatile boolean pendenteScrollForaLocal = false;

    private static class InfoTagFora {
        final String codigoExibido;
        final String descricao;
        final String localOrigem;

        InfoTagFora(
                String codigoExibido,
                String descricao,
                String localOrigem
        ) {
            this.codigoExibido = codigoExibido;
            this.descricao = descricao;
            this.localOrigem = localOrigem;
        }
    }

    private PatrimonioLocalAdapter adapter;
    private RecyclerView recyclerLocal;

    // tvContador removido — id tvContadorLocal não existe no layout
    private TextView txtInfoTopo;
    private TextView txtInfoUser;
    private TextView txtBotao;
    private TextView txtModoToggle;

    private TextView txtQtdTodos;
    private TextView txtQtdIdentificados;
    private TextView txtQtdNaoIdentificados;
    private TextView txtQtdForaDoLocal;

    private LinearLayout btnLer;
    private LinearLayout btnConcluir;
    private LinearLayout btnDistancia;
    private LinearLayout btnResumo;
    private LinearLayout btnHistorico;
    private LinearLayout btnModoToggle;
    private LinearLayout btnLimpar;

    private LinearLayout btnFiltroTodos;
    private LinearLayout btnFiltroIdentificados;
    private LinearLayout btnFiltroNaoIdentificados;
    private LinearLayout btnFiltroForaDoLocal;

    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario_local);

        dbHelper = new DBHelper(this);

        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        vincularViews();
        carregarPatrimonios();
        configurarBusca();
        configurarListeners();
        inicializarRFID();
        inicializarBarcode2D();
        atualizarBotaoModo();
        atualizarVisualFiltro();
    }

    private void vincularViews() {
        recyclerLocal = findViewById(R.id.listViewLocal);

        // LINHA REMOVIDA: tvContador = findViewById(R.id.tvContadorLocal);
        // O id tvContadorLocal não existe no layout XML.

        txtInfoTopo = findViewById(R.id.txtInfoTopoLocal);
        txtInfoUser = findViewById(R.id.txtInfoUserLocal);
        txtBotao = findViewById(R.id.txtBotaoLocal);
        txtModoToggle = findViewById(R.id.txtModoToggleLocal);

        btnLer = findViewById(R.id.btnLerLocal);
        btnConcluir = findViewById(R.id.btnConcluirLocal);
        btnDistancia = findViewById(R.id.btnDistanciaLocal);
        btnResumo = findViewById(R.id.btnResumoLocal);
        btnHistorico = findViewById(R.id.btnHistoricoLocal);
        btnModoToggle = findViewById(R.id.btnModoToggleLocal);
        btnLimpar = findViewById(R.id.btnLimparLocal);

        btnFiltroTodos = findViewById(R.id.btnFiltroTodos);
        btnFiltroIdentificados = findViewById(R.id.btnFiltroIdentificados);
        btnFiltroForaDoLocal = findViewById(R.id.btnFiltroForaDoLocal);
        btnFiltroNaoIdentificados = findViewById(R.id.btnFiltroNaoIdentificados);

        txtQtdTodos = findViewById(R.id.txtQtdTodos);
        txtQtdIdentificados = findViewById(R.id.txtQtdIdentificados);
        txtQtdForaDoLocal = findViewById(R.id.txtQtdForaDoLocal);
        txtQtdNaoIdentificados = findViewById(R.id.txtQtdNaoIdentificados);

        etBusca = findViewById(R.id.etBuscaLocal);

        txtInfoTopo.setText(
                localBanco != null && userBanco != null
                        ? localBanco.getLocalNome() + " | " + userBanco.getNome()
                        : "Dados não encontrados."
        );

        txtInfoUser.setText(
                codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario
        );
    }

    private void carregarPatrimonios() {
        todosPatrimonios = dbHelper.listarPatrimoniosPorLocal(codigoLocal);

        estadoPatrimonios.clear();
        ordemLeitura.clear();
        tagsForaDoLocal.clear();
        tagForaAceitas.clear();
        indicePatrimonioPorChave.clear();

        for (Patrimonio patrimonio : todosPatrimonios) {
            estadoPatrimonios.put(
                    patrimonio.getCodigoBarra(),
                    ESTADO_PENDENTE
            );

            String chave5 = obterChave5(patrimonio.getCodigoBarra());

            if (chave5 != null) {
                indicePatrimonioPorChave.put(chave5, patrimonio);
            }
        }

        listaFiltrada.clear();
        listaFiltrada.addAll(todosPatrimonios);

        adapter = new PatrimonioLocalAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerLocal.setLayoutManager(layoutManager);
        recyclerLocal.setNestedScrollingEnabled(true);
        recyclerLocal.setHasFixedSize(true);
        recyclerLocal.setItemViewCacheSize(20);
        recyclerLocal.setItemAnimator(null);
        recyclerLocal.setAdapter(adapter);

        filtrar("");

        // Índice de TODOS os patrimônios (qualquer local), montado uma única
        // vez em background — usado só para descrever tags "fora do local"
        // sem consultar o banco inteiro a cada leitura.
        carregarIndiceGlobal();
    }

    private void carregarIndiceGlobal() {
        executor.execute(() -> {
            Map<String, Patrimonio> indice = new HashMap<>();

            for (Patrimonio patrimonio : dbHelper.listarPatrimonios()) {
                String chave5 = obterChave5(patrimonio.getCodigoBarra());

                if (chave5 != null && !indice.containsKey(chave5)) {
                    indice.put(chave5, patrimonio);
                }
            }

            // Mesma thread única do executor que processa as leituras,
            // então não há corrida com processarCodigo().
            indiceGlobalPorChave.clear();
            indiceGlobalPorChave.putAll(indice);
        });
    }

    private String obterChave5(String codigoBarra) {
        if (codigoBarra == null) {
            return null;
        }

        String normalizado = normalizarCodigo(codigoBarra);
        return normalizado.length() < 5 ? null : normalizado.substring(0, 5);
    }

    private void configurarBusca() {
        etBusca.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtrar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void configurarListeners() {
        btnLer.setOnClickListener(v -> alternarLeitura());
        btnModoToggle.setOnClickListener(v -> trocarModo());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnHistorico.setOnClickListener(v ->
                startActivity(new Intent(this, HistoricoActivity.class))
        );
        btnLimpar.setOnClickListener(v -> confirmarLimpeza());

        btnFiltroTodos.setOnClickListener(v -> selecionarFiltro(FILTRO_TODOS));
        btnFiltroIdentificados.setOnClickListener(v -> selecionarFiltro(FILTRO_IDENTIFICADOS));
        btnFiltroNaoIdentificados.setOnClickListener(v -> selecionarFiltro(FILTRO_NAO_IDENTIFICADOS));
        btnFiltroForaDoLocal.setOnClickListener(v -> selecionarFiltro(FILTRO_FORA_DO_LOCAL));

        btnConcluir.setOnClickListener(v -> {
            List<Patrimonio> lidosList = new ArrayList<>();

            for (Patrimonio patrimonio : todosPatrimonios) {
                if (getEstado(patrimonio) == ESTADO_ENCONTRADO) {
                    lidosList.add(patrimonio);
                }
            }

            ConcluirHelper.executarPatrimonios(
                    this,
                    executor,
                    codigoFilial,
                    codigoLocal,
                    chapaFuncionario,
                    "LOCAL",
                    lidosList
            );
        });
    }

    private void selecionarFiltro(int filtro) {
        filtroAtual = filtro;
        atualizarVisualFiltro();
        filtrar(etBusca.getText().toString());
    }

    private void atualizarVisualFiltro() {
        int azul    = Color.parseColor("#005EB8");
        int verde   = Color.parseColor("#2E7D32");
        int vermelho = Color.parseColor("#C62828");
        int amarelo = Color.parseColor("#F57F17");
        int cinza   = Color.parseColor("#E0E0E0");

        btnFiltroTodos.setBackgroundTintList(ColorStateList.valueOf(
                filtroAtual == FILTRO_TODOS ? azul : cinza));

        btnFiltroIdentificados.setBackgroundTintList(ColorStateList.valueOf(
                filtroAtual == FILTRO_IDENTIFICADOS ? verde : cinza));

        btnFiltroNaoIdentificados.setBackgroundTintList(ColorStateList.valueOf(
                filtroAtual == FILTRO_NAO_IDENTIFICADOS ? vermelho : cinza));

        btnFiltroForaDoLocal.setBackgroundTintList(ColorStateList.valueOf(
                filtroAtual == FILTRO_FORA_DO_LOCAL ? amarelo : cinza));
    }

    private void filtrar(String query) {
        textoBuscaAtual = query == null ? "" : query.trim().toLowerCase();

        listaFiltrada.clear();

        if (filtroAtual != FILTRO_FORA_DO_LOCAL) {
            for (Patrimonio patrimonio : todosPatrimonios) {
                if (!passaNaBuscaPatrimonio(patrimonio)) continue;

                int estado = getEstado(patrimonio);

                boolean passouNoFiltro =
                        filtroAtual == FILTRO_TODOS
                                || (filtroAtual == FILTRO_IDENTIFICADOS && estado == ESTADO_ENCONTRADO)
                                || (filtroAtual == FILTRO_NAO_IDENTIFICADOS && estado != ESTADO_ENCONTRADO);

                if (passouNoFiltro) {
                    listaFiltrada.add(patrimonio);
                }
            }
        }

        if (adapter != null) {
            adapter.atualizarLista();
        }

        atualizarContador();
    }

    private boolean passaNaBuscaPatrimonio(Patrimonio patrimonio) {
        if (textoBuscaAtual.isEmpty()) return true;

        return (patrimonio.getDescricao() != null
                && patrimonio.getDescricao().toLowerCase().contains(textoBuscaAtual))
                || (patrimonio.getCodigoBarra() != null
                && patrimonio.getCodigoBarra().toLowerCase().contains(textoBuscaAtual))
                || (patrimonio.getPatrimonio() != null
                && patrimonio.getPatrimonio().toLowerCase().contains(textoBuscaAtual));
    }

    private void confirmarLimpeza() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Limpar leituras")
                .setMessage("Deseja apagar todas as tags lidas e reiniciar o inventário?")
                .setPositiveButton("Limpar", (dialog, which) -> limparTags())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void limparTags() {
        pararLeituraRFID();
        pararLeitura2D();

        for (String codigoBarra : estadoPatrimonios.keySet()) {
            estadoPatrimonios.put(codigoBarra, ESTADO_PENDENTE);
        }

        ordemLeitura.clear();
        tagsForaDoLocal.clear();
        tagForaAceitas.clear();

        mainHandler.post(() -> {
            filtrar(etBusca.getText().toString());
            Toast.makeText(this, "Leituras limpas!", Toast.LENGTH_SHORT).show();
        });
    }

    private void trocarModo() {
        if (modoRfid) {
            pararLeituraRFID();
        } else {
            pararLeitura2D();
        }

        modoRfid = !modoRfid;
        atualizarBotaoModo();

        Toast.makeText(this,
                "Modo: " + (modoRfid ? "RFID" : "Código de Barras"),
                Toast.LENGTH_SHORT).show();
    }

    private void atualizarBotaoModo() {
        if (modoRfid) {
            btnModoToggle.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#005EB8")));
            txtModoToggle.setText("Modo: RFID");
        } else {
            btnModoToggle.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#388E3C")));
            txtModoToggle.setText("Modo: Cód. Barras");
        }
    }

    private void alternarLeitura() {
        if (modoRfid) {
            if (isReadingRFID) pararLeituraRFID(); else iniciarLeituraRFID();
        } else {
            if (isReading2D) pararLeitura2D(); else iniciarLeitura2D();
        }
    }

    private void inicializarRFID() {
        executor.execute(() -> {
            try {
                mReader = RFIDWithUHFUART.getInstance();
                if (mReader != null && mReader.init(this)) {
                    mainHandler.post(() ->
                            Toast.makeText(this, "Leitor RFID pronto", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao inicializar RFID", e);
            }
        });
    }

    private void iniciarLeituraRFID() {
        if (isReadingRFID || mReader == null) return;

        isReadingRFID = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));

        executor.execute(() -> {
            try {
                mReader.startInventoryTag();
                loopRFID();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar RFID", e);
                pararLeituraRFID();
            }
        });
    }

    private void loopRFID() {
        while (isReadingRFID) {
            try {
                UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                if (tagInfo != null) {
                    String norm = normalizarCodigo(tagInfo.getEPC());
                    if (norm != null && norm.length() >= 5) {
                        String chave5 = norm.substring(0, 5);
                        String codigoMontado = "040" + chave5;
                        processarCodigo(codigoMontado, chave5);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro no loop RFID", e);
            }
        }
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        mainHandler.post(() -> txtBotao.setText("Iniciar Leitura"));
        executor.execute(() -> {
            try {
                if (mReader != null) mReader.stopInventory();
            } catch (Exception ignored) {}
        });
    }

    private void inicializarBarcode2D() {
        barcodeExecutor.execute(() -> {
            try {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
                if (barcodeDecoder.open(this)) {
                    barcodeDecoder.setDecodeCallback(entity -> {
                        if (entity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) return;

                        String norm = normalizarCodigo(entity.getBarcodeData());
                        if (norm == null || norm.length() < 5) return;

                        String chave5 = norm.substring(0, 5);
                        String codigoMontado = "040" + chave5;

                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                        executor.execute(() -> processarCodigo(codigoMontado, chave5));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao inicializar código de barras", e);
            }
        });
    }

    private void iniciarLeitura2D() {
        if (isReading2D || barcodeDecoder == null) return;

        isReading2D = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));

        try {
            barcodeDecoder.startScan();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar leitura 2D", e);
            isReading2D = false;
        }
    }

    private void pararLeitura2D() {
        if (barcodeDecoder == null) return;

        isReading2D = false;
        mainHandler.post(() -> txtBotao.setText("Iniciar Leitura"));

        try {
            barcodeDecoder.stopScan();
        } catch (Exception ignored) {}
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) return "";

        String epc = valor.trim();
        if (epc.equalsIgnoreCase("null") || epc.isEmpty()) return "";

        if (epc.startsWith("040") && epc.length() > 3) {
            epc = epc.substring(3);
        } else if (epc.startsWith("40") && epc.length() > 2) {
            epc = epc.substring(2);
        }

        epc = epc.replaceFirst("^0+", "");
        return epc.isEmpty() ? "" : epc;
    }

    /**
     * Chamado pelas threads dos leitores, sempre na thread única do
     * executor. Usa índices O(1) em vez de varrer todosPatrimonios, e
     * nunca consulta o banco aqui — o índice global já foi montado uma
     * vez em carregarIndiceGlobal(). O redesenho da tela é agrupado por
     * agendarFlush(), em vez de acontecer a cada tag.
     */
    private void processarCodigo(String codigoMontado, String chave5) {
        if (tagsForaDoLocal.containsKey(chave5)) {
            return;
        }

        Patrimonio encontrado = indicePatrimonioPorChave.get(chave5);

        if (encontrado != null) {
            if (getEstado(encontrado) == ESTADO_ENCONTRADO) {
                return;
            }

            String codigoBarraFinal = encontrado.getCodigoBarra();
            estadoPatrimonios.put(codigoBarraFinal, ESTADO_ENCONTRADO);
            ordemLeitura.remove(codigoBarraFinal);
            ordemLeitura.add(0, codigoBarraFinal);

            dbHelper.salvarHistoricoComTipo(codigoFilial, codigoLocal,
                    chapaFuncionario, codigoBarraFinal, "LOCAL");

            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80);

            pendenteScrollIdentificado = true;
            agendarFlush();

        } else {
            Patrimonio global = indiceGlobalPorChave.get(chave5);

            String descricaoGlobal = global != null && global.getDescricao() != null
                    ? global.getDescricao() : "";

            String localOrigem = "";

            if (global != null) {
                localOrigem = global.getNomeLocal() != null ? global.getNomeLocal() : "";

                if (localOrigem.isEmpty() && global.getCodLocal() != null) {
                    localOrigem = global.getCodLocal();
                }
            }

            tagsForaDoLocal.put(chave5,
                    new InfoTagFora(codigoMontado, descricaoGlobal, localOrigem));

            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 150);

            pendenteScrollForaLocal = true;
            agendarFlush();
        }
    }

    /**
     * Agrupa várias leituras num único redesenho da lista. Sem isso, uma
     * rajada de tags dispara um filtrar() + notifyDataSetChanged() completo
     * para cada tag individual.
     */
    private void agendarFlush() {
        if (flushAgendado) {
            return;
        }

        flushAgendado = true;

        mainHandler.postDelayed(() -> {
            flushAgendado = false;

            filtrar(etBusca.getText().toString());

            if (filtroAtual != FILTRO_FORA_DO_LOCAL && pendenteScrollIdentificado) {
                recyclerLocal.scrollToPosition(0);
            } else if (filtroAtual == FILTRO_FORA_DO_LOCAL && pendenteScrollForaLocal) {
                recyclerLocal.scrollToPosition(0);
            }

            pendenteScrollIdentificado = false;
            pendenteScrollForaLocal = false;
        }, JANELA_FLUSH);
    }

    private int getEstado(Patrimonio patrimonio) {
        Integer estado = estadoPatrimonios.get(patrimonio.getCodigoBarra());
        return estado != null ? estado : ESTADO_PENDENTE;
    }

    private void atualizarContador() {
        int identificados = 0;
        int naoIdentificados = 0;

        for (Patrimonio patrimonio : todosPatrimonios) {
            if (getEstado(patrimonio) == ESTADO_ENCONTRADO) {
                identificados++;
            } else {
                naoIdentificados++;
            }
        }

        int foraDoLocal = tagsForaDoLocal.size();
        int total = todosPatrimonios.size();

        txtQtdTodos.setText(String.valueOf(total));
        txtQtdIdentificados.setText(String.valueOf(identificados));
        txtQtdForaDoLocal.setText(String.valueOf(foraDoLocal));
        txtQtdNaoIdentificados.setText(String.valueOf(naoIdentificados));
    }

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    executor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power)) {
                                mainHandler.post(() ->
                                        Toast.makeText(this,
                                                "Potência: " + power + " dBm",
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
        if (ordemLeitura.isEmpty() && tagsForaDoLocal.isEmpty()) {
            Toast.makeText(this, "Nenhum item lido ainda!", Toast.LENGTH_SHORT).show();
            return;
        }

        String nomeLocalAtual = localBanco != null
                ? localBanco.getLocalNome()
                : codigoLocal;

        ArrayList<String> tagsLidas = new ArrayList<>();
        ArrayList<String> descricoesLidas = new ArrayList<>();

        // TRUE = pertence ao local
        // FALSE = está fora do local
        ArrayList<Boolean> itensPertencemAoLocal = new ArrayList<>();

        // =========================================================
        // ITENS IDENTIFICADOS / PERTENCENTES AO LOCAL
        // =========================================================
        for (String codigoBarra : ordemLeitura) {

            for (Patrimonio p : todosPatrimonios) {

                if (codigoBarra.equals(p.getCodigoBarra())) {

                    tagsLidas.add(codigoBarra);

                    String descricao = p.getDescricao() != null
                            && !p.getDescricao().trim().isEmpty()
                            ? p.getDescricao()
                            : "DESCONHECIDO";

                    descricoesLidas.add(
                            descricao + "\n" +
                                    "Local: " + nomeLocalAtual
                    );

                    // Este item pertence ao local atual
                    itensPertencemAoLocal.add(true);

                    break;
                }
            }
        }

        // =========================================================
        // TAGS FORA DO LOCAL
        // =========================================================
        for (InfoTagFora info : tagsForaDoLocal.values()) {

            tagsLidas.add(info.codigoExibido);

            String descricao = info.descricao != null
                    && !info.descricao.trim().isEmpty()
                    ? info.descricao
                    : "DESCONHECIDO";

            String local = info.localOrigem != null
                    && !info.localOrigem.trim().isEmpty()
                    ? info.localOrigem
                    : "Local desconhecido";

            descricoesLidas.add(
                    descricao + "\n" +
                            "Local: " + local
            );

            // Este item NÃO pertence ao local atual
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

        // Envia a informação de pertencimento para o ResumoActivity
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

        // Total de itens que pertencem ao local
        intent.putExtra(
                "totalIdentificados",
                ordemLeitura.size()
        );

        // Total de itens fora do local
        intent.putExtra(
                "totalForaDoLocal",
                tagsForaDoLocal.size()
        );

        startActivity(intent);
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == 293 && event.getAction() == KeyEvent.ACTION_DOWN) {
            alternarLeitura();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        pararLeituraRFID();
        pararLeitura2D();

        mainHandler.removeCallbacksAndMessages(null);

        executor.shutdown();
        barcodeExecutor.shutdown();

        try {
            if (barcodeDecoder != null) barcodeDecoder.close();
        } catch (Exception ignored) {}

        if (toneGen != null) toneGen.release();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────────────

    private class PatrimonioLocalAdapter
            extends RecyclerView.Adapter<PatrimonioLocalAdapter.VH> {

        private final List<Object> itensExibidos = new ArrayList<>();

        class VH extends RecyclerView.ViewHolder {
            TextView txtCodigo;
            TextView txtDescricao;
            TextView txtLocal;
            ImageView imgStatus;

            VH(View itemView) {
                super(itemView);
                txtCodigo = itemView.findViewById(R.id.txtItemCodigo);
                txtDescricao = itemView.findViewById(R.id.txtItemDescricao);
                txtLocal = itemView.findViewById(R.id.txtItemLocal);
                imgStatus = itemView.findViewById(R.id.imgPatrimonio);
            }
        }

        public void atualizarLista() {
            itensExibidos.clear();

            if (filtroAtual == FILTRO_FORA_DO_LOCAL) {
                List<String> tagsFora = new ArrayList<>(tagsForaDoLocal.keySet());
                Collections.reverse(tagsFora);
                for (String chave5 : tagsFora) {
                    InfoTagFora info = tagsForaDoLocal.get(chave5);
                    if (info != null && passaNaBuscaTagFora(info)) {
                        itensExibidos.add(chave5);
                    }
                }
                notifyDataSetChanged();
                return;
            }

            for (String codigoBarra : ordemLeitura) {
                for (Patrimonio patrimonio : listaFiltrada) {
                    if (codigoBarra.equals(patrimonio.getCodigoBarra())) {
                        itensExibidos.add(patrimonio);
                        break;
                    }
                }
            }

            for (Patrimonio patrimonio : listaFiltrada) {
                if (!itensExibidos.contains(patrimonio)) {
                    itensExibidos.add(patrimonio);
                }
            }

            notifyDataSetChanged();
        }

        private boolean passaNaBuscaTagFora(InfoTagFora info) {
            if (textoBuscaAtual.isEmpty()) return true;
            return info.codigoExibido.toLowerCase().contains(textoBuscaAtual)
                    || (!info.descricao.isEmpty()
                    && info.descricao.toLowerCase().contains(textoBuscaAtual))
                    || (!info.localOrigem.isEmpty()
                    && info.localOrigem.toLowerCase().contains(textoBuscaAtual));
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_patrimonio, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Object item = itensExibidos.get(position);
            if (item instanceof Patrimonio) {
                bindPatrimonio(holder, (Patrimonio) item);
            } else {
                bindTagForaDoLocal(holder, (String) item);
            }
        }

        private void bindPatrimonio(@NonNull VH holder, Patrimonio patrimonio) {
            int estado = getEstado(patrimonio);

            holder.txtCodigo.setText(patrimonio.getCodigoBarra());
            holder.txtDescricao.setText(patrimonio.getDescricao());
            holder.txtLocal.setVisibility(View.GONE);

            if (estado == ESTADO_ENCONTRADO) {
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.txtCodigo.setTextColor(Color.parseColor("#1B5E20"));
                holder.txtDescricao.setTextColor(Color.parseColor("#2E7D32"));
                holder.imgStatus.clearColorFilter();
                holder.imgStatus.setImageResource(R.drawable.ic_ativo_pat);

                holder.itemView.setOnClickListener(v -> {

                    new android.app.AlertDialog.Builder(InventarioLocalActivity.this)
                            .setTitle("Alterar status")
                            .setMessage(
                                    "Deseja marcar este patrimônio como não identificado?\n\n" +
                                            patrimonio.getCodigoBarra() +
                                            "\n" +
                                            (patrimonio.getDescricao() != null
                                                    ? patrimonio.getDescricao()
                                                    : "")
                            )
                            .setPositiveButton("Sim", (dialog, which) -> {

                                estadoPatrimonios.put(
                                        patrimonio.getCodigoBarra(),
                                        ESTADO_NAO_ENCONTRADO
                                );

                                ordemLeitura.remove(patrimonio.getCodigoBarra());

                                filtrar(etBusca.getText().toString());
                                atualizarContador();

                                Toast.makeText(
                                        InventarioLocalActivity.this,
                                        "Marcado como não identificado",
                                        Toast.LENGTH_SHORT
                                ).show();
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                });

            } else if (estado == ESTADO_NAO_ENCONTRADO) {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFEBEE"));
                holder.txtCodigo.setTextColor(Color.parseColor("#B71C1C"));
                holder.txtDescricao.setTextColor(Color.parseColor("#C62828"));
                holder.imgStatus.clearColorFilter();
                holder.imgStatus.setImageResource(R.drawable.ic_desconhecido);

                holder.itemView.setOnClickListener(v -> {

                    new android.app.AlertDialog.Builder(InventarioLocalActivity.this)
                            .setTitle("Alterar status")
                            .setMessage(
                                    "Deseja marcar este patrimônio como identificado?\n\n" +
                                            patrimonio.getCodigoBarra() +
                                            "\n" +
                                            (patrimonio.getDescricao() != null
                                                    ? patrimonio.getDescricao()
                                                    : "")
                            )
                            .setPositiveButton("Sim", (dialog, which) -> {

                                estadoPatrimonios.put(
                                        patrimonio.getCodigoBarra(),
                                        ESTADO_ENCONTRADO
                                );

                                ordemLeitura.remove(patrimonio.getCodigoBarra());
                                ordemLeitura.add(0, patrimonio.getCodigoBarra());

                                filtrar(etBusca.getText().toString());
                                atualizarContador();
                                recyclerLocal.scrollToPosition(0);

                                Toast.makeText(
                                        InventarioLocalActivity.this,
                                        "Marcado como identificado",
                                        Toast.LENGTH_SHORT
                                ).show();
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                });

            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"));
                holder.txtCodigo.setTextColor(Color.parseColor("#9E9E9E"));
                holder.txtDescricao.setTextColor(Color.parseColor("#757575"));
                holder.imgStatus.clearColorFilter();
                holder.imgStatus.setImageResource(R.drawable.ic_loading);

                holder.itemView.setOnClickListener(v -> {
                    estadoPatrimonios.put(patrimonio.getCodigoBarra(), ESTADO_ENCONTRADO);
                    ordemLeitura.remove(patrimonio.getCodigoBarra());
                    ordemLeitura.add(0, patrimonio.getCodigoBarra());
                    filtrar(etBusca.getText().toString());
                    atualizarContador();
                    recyclerLocal.scrollToPosition(0);
                    Toast.makeText(InventarioLocalActivity.this,
                            "Marcado como identificado", Toast.LENGTH_SHORT).show();
                });
            }
        }

        private void bindTagForaDoLocal(@NonNull VH holder, String chave5) {
            InfoTagFora info = tagsForaDoLocal.get(chave5);
            if (info == null) return;

            boolean aceita = tagForaAceitas.contains(chave5);
            holder.txtCodigo.setText(info.codigoExibido);

            holder.txtDescricao.setText(
                    !info.descricao.isEmpty() ? info.descricao : "Tag desconhecida");

            String textoLocal;

            if (aceita) {
                textoLocal = !info.localOrigem.isEmpty()
                        ? "\u2713 Entrada aceita — origem: " + info.localOrigem
                        : "\u2713 Entrada aceita";
            } else {
                textoLocal = !info.localOrigem.isEmpty()
                        ? info.localOrigem
                        : "Local de origem desconhecido — toque para aceitar";
            }

            holder.txtLocal.setText(textoLocal);
            holder.txtLocal.setVisibility(View.VISIBLE);

            if (aceita) {
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.txtCodigo.setTextColor(Color.parseColor("#1B5E20"));
                holder.txtDescricao.setTextColor(Color.parseColor("#2E7D32"));
                holder.txtLocal.setTextColor(Color.parseColor("#2E7D32"));
                holder.imgStatus.clearColorFilter();
                holder.imgStatus.setImageResource(R.drawable.ic_ativo_pat);

                holder.itemView.setOnClickListener(v -> {
                    tagForaAceitas.remove(chave5);
                    filtrar(etBusca.getText().toString());
                    Toast.makeText(InventarioLocalActivity.this,
                            "Entrada removida", Toast.LENGTH_SHORT).show();
                });

            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFFDE7"));
                holder.txtCodigo.setTextColor(Color.parseColor("#F57F17"));
                holder.txtDescricao.setTextColor(Color.parseColor("#E65100"));
                holder.txtLocal.setTextColor(Color.parseColor("#E65100"));
                holder.imgStatus.clearColorFilter();
                holder.imgStatus.setImageResource(R.drawable.ic_desconhecido);

                holder.itemView.setOnClickListener(v -> {
                    tagForaAceitas.add(chave5);
                    dbHelper.salvarHistoricoComTipo(codigoFilial, codigoLocal,
                            chapaFuncionario, info.codigoExibido, "LOCAL_ENTRADA");
                    filtrar(etBusca.getText().toString());
                    Toast.makeText(InventarioLocalActivity.this,
                            "Tag aceita como entrada", Toast.LENGTH_SHORT).show();
                });
            }
        }

        @Override
        public int getItemCount() {
            return itensExibidos.size();
        }
    }
}