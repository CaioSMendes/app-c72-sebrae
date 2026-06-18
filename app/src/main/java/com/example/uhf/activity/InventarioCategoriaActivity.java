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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.example.uhf.R;
import com.example.uhf.model.Local;
import com.example.uhf.model.Patrimonio;
import com.example.uhf.model.Usuario;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventarioCategoriaActivity extends AppCompatActivity {

    private static final String TAG = "InventarioCategoria";

    // ── Leitores ──────────────────────────────────────────────
    private RFIDWithUHFUART mReader;
    private BarcodeDecoder  barcodeDecoder;

    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D   = false;
    private volatile boolean modoRfid      = true;

    private final Handler        mainHandler     = new Handler(Looper.getMainLooper());
    private final ExecutorService executor        = Executors.newSingleThreadExecutor();
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();
    private ToneGenerator toneGen;

    // ── Dados ─────────────────────────────────────────────────
    private DBHelper dbHelper;
    private String codigoFilial, codigoLocal, chapaFuncionario;
    private Local   localBanco;
    private Usuario userBanco;

    // mapaCompleto: descrição → lista de patrimônios (todos do local)
    private final Map<String, List<Patrimonio>> mapaCompleto = new TreeMap<>();
    // lidos: sufixos de 5 dígitos já lidos
    private final Set<String> lidos           = new HashSet<>();
    private final Set<String> gruposExpandidos = new HashSet<>();

    // Termo de pesquisa atual
    private String queryTexto = "";

    // ── Views ─────────────────────────────────────────────────
    private LinearLayout containerGrupos;
    private NestedScrollView nestedScroll;
    private TextView     tvContador, txtInfoTopo, txtInfoUser, txtBotao, txtModoToggle;
    private EditText     etBusca;
    private TextView     btnLimparFiltro;
    private LinearLayout btnLer, btnConcluir, btnDistancia, btnResumo,
            btnHistorico, btnModoToggle, btnLimparTags;
    private TextView     tvEmptyState;

    // ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario_categoria);

        dbHelper         = new DBHelper(this);
        codigoFilial     = getIntent().getStringExtra("codigoFilial");
        codigoLocal      = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");
        localBanco       = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco        = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);
        toneGen          = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        vincularViews();
        carregarEAgrupar();
        configurarBusca();
        configurarListeners();
        inicializarRFID();
        inicializarBarcode2D();
        atualizarBotaoModo();
    }

    // ── Views ─────────────────────────────────────────────────
    private void vincularViews() {
        containerGrupos  = findViewById(R.id.containerGrupos);
        nestedScroll     = findViewById(R.id.nestedScrollCategoria);
        tvContador       = findViewById(R.id.tvContadorCategoria);
        txtInfoTopo      = findViewById(R.id.txtInfoTopoCategoria);
        txtInfoUser      = findViewById(R.id.txtInfoUserCategoria);
        txtBotao         = findViewById(R.id.txtBotaoCategoria);
        etBusca          = findViewById(R.id.etBuscaCategoria);
        btnLimparFiltro  = findViewById(R.id.btnLimparFiltro);
        btnLer           = findViewById(R.id.btnLerCategoria);
        btnConcluir      = findViewById(R.id.btnConcluirCategoria);
        btnDistancia     = findViewById(R.id.btnDistanciaCategoria);
        btnResumo        = findViewById(R.id.btnResumoCategoria);
        btnHistorico     = findViewById(R.id.btnHistoricoCategoria);
        btnModoToggle    = findViewById(R.id.btnModoToggleCategoria);
        txtModoToggle    = findViewById(R.id.txtModoToggleCategoria);
        btnLimparTags    = findViewById(R.id.btnLimparCategoria);
        tvEmptyState     = findViewById(R.id.tvEmptyStateCategoria);

        txtInfoTopo.setText(localBanco != null && userBanco != null
                ? localBanco.getLocalNome() + " | " + userBanco.getNome()
                : "Dados não encontrados.");
        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);
    }

    // ── Dados ─────────────────────────────────────────────────
    private void carregarEAgrupar() {
        List<Patrimonio> todos = dbHelper.listarPatrimoniosPorLocal(codigoLocal);
        mapaCompleto.clear();
        for (Patrimonio p : todos) {
            String desc = (p.getDescricao() != null && !p.getDescricao().isEmpty())
                    ? p.getDescricao() : "Sem descrição";
            mapaCompleto.computeIfAbsent(desc, k -> new ArrayList<>()).add(p);
        }
        // Não expande nenhum grupo por padrão — usuário pesquisa primeiro
        gruposExpandidos.clear();
        renderizarGrupos();
    }

    // ── Busca ─────────────────────────────────────────────────
    private void configurarBusca() {
        etBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                queryTexto = s.toString().toLowerCase().trim();
                btnLimparFiltro.setVisibility(queryTexto.isEmpty() ? View.GONE : View.VISIBLE);
                // Ao pesquisar, expande automaticamente todos os grupos filtrados
                if (!queryTexto.isEmpty()) {
                    for (String desc : mapaCompleto.keySet())
                        if (desc.toLowerCase().contains(queryTexto))
                            gruposExpandidos.add(desc);
                }
                renderizarGrupos();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnLimparFiltro.setOnClickListener(v -> {
            etBusca.setText("");
            etBusca.clearFocus();
            gruposExpandidos.clear();
        });

        // Fecha o teclado ao pressionar Enter/Buscar — termo fica salvo na barra
        etBusca.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etBusca.getWindowToken(), 0);
                etBusca.clearFocus();
                return true;
            }
            return false;
        });
    }

    // ── Renderização ──────────────────────────────────────────
    private void renderizarGrupos() {
        containerGrupos.removeAllViews();

        // Sem termo → não exibe nada
        if (queryTexto.isEmpty()) {
            if (tvEmptyState != null) {
                tvEmptyState.setText("Digite um termo para buscar categorias.");
                tvEmptyState.setVisibility(View.VISIBLE);
            }
            atualizarContador();
            return;
        }

        int gruposVisiveis = 0;

        for (Map.Entry<String, List<Patrimonio>> entry : mapaCompleto.entrySet()) {
            String desc           = entry.getKey();
            List<Patrimonio> itens = entry.getValue();

            // Só mostra grupos cuja descrição bate com o termo
            if (!desc.toLowerCase().contains(queryTexto)) continue;

            gruposVisiveis++;

            // ── Cabeçalho do grupo ─────────────────────────
            View cabecalho = LayoutInflater.from(this)
                    .inflate(R.layout.item_grupo_categoria, containerGrupos, false);
            TextView  txtDesc  = cabecalho.findViewById(R.id.txtGrupoDescricao);
            TextView  txtContG = cabecalho.findViewById(R.id.txtGrupoContador);
            ImageView imgSeta  = cabecalho.findViewById(R.id.imgSetaGrupo);

            int lidosGrupo = contarLidosGrupo(itens);
            int totalGrupo = itens.size();
            boolean completo = lidosGrupo == totalGrupo && totalGrupo > 0;

            // Destaca o termo pesquisado na descrição
            txtDesc.setText(destacarTermo(desc, queryTexto));

            txtContG.setText(lidosGrupo + "/" + totalGrupo);
            if (completo) {
                cabecalho.setBackgroundColor(Color.parseColor("#E8F5E9"));
                txtDesc.setTextColor(Color.parseColor("#1B5E20"));
                txtContG.setTextColor(Color.WHITE);
                txtContG.setBackgroundResource(R.drawable.bg_badge_verde);
            } else {
                cabecalho.setBackgroundColor(Color.parseColor("#EFEFF4"));
                txtDesc.setTextColor(Color.parseColor("#212121"));
                txtContG.setTextColor(Color.WHITE);
                txtContG.setBackgroundResource(R.drawable.bg_badge_azul);
            }

            boolean expandido = gruposExpandidos.contains(desc);
            imgSeta.setRotation(expandido ? 180f : 0f);
            containerGrupos.addView(cabecalho);

            // ── Filhos ────────────────────────────────────
            LinearLayout containerFilhos = new LinearLayout(this);
            containerFilhos.setOrientation(LinearLayout.VERTICAL);
            containerFilhos.setVisibility(expandido ? View.VISIBLE : View.GONE);

            for (Patrimonio p : itens) {
                View      itemView    = LayoutInflater.from(this)
                        .inflate(R.layout.item_patrimonio, containerFilhos, false);
                TextView  txtCodigo   = itemView.findViewById(R.id.txtItemCodigo);
                TextView  txtDescItem = itemView.findViewById(R.id.txtItemDescricao);
                ImageView imgStatus   = itemView.findViewById(R.id.imgPatrimonio);

                txtCodigo.setText(p.getCodigoBarra());
                txtDescItem.setText(destacarTermo(p.getDescricao(), queryTexto));
                aplicarCorItem(itemView, txtCodigo, txtDescItem, imgStatus, estaLido(p));
                containerFilhos.addView(itemView);
            }
            containerGrupos.addView(containerFilhos);

            final String       descFinal   = desc;
            final LinearLayout filhosFinal = containerFilhos;
            cabecalho.setOnClickListener(v -> {
                boolean era = gruposExpandidos.contains(descFinal);
                if (era) { gruposExpandidos.remove(descFinal); filhosFinal.setVisibility(View.GONE);    imgSeta.setRotation(0f); }
                else     { gruposExpandidos.add(descFinal);    filhosFinal.setVisibility(View.VISIBLE); imgSeta.setRotation(180f); }
            });
        }

        // Empty state — só chega aqui se queryTexto não está vazio
        if (tvEmptyState != null) {
            if (gruposVisiveis == 0) {
                tvEmptyState.setText("Nenhum item encontrado para \"" + queryTexto + "\".");
                tvEmptyState.setVisibility(View.VISIBLE);
            } else {
                tvEmptyState.setVisibility(View.GONE);
            }
        }

        atualizarContador();
    }

    // Retorna SpannableString com o termo destacado em amarelo
    private SpannableString destacarTermo(String texto, String termo) {
        if (texto == null) texto = "";
        SpannableString ss = new SpannableString(texto);
        if (termo == null || termo.isEmpty()) return ss;
        String textoLower = texto.toLowerCase();
        int inicio = textoLower.indexOf(termo);
        while (inicio >= 0) {
            ss.setSpan(new BackgroundColorSpan(Color.parseColor("#FFF176")),
                    inicio, inicio + termo.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            inicio = textoLower.indexOf(termo, inicio + termo.length());
        }
        return ss;
    }

    private void aplicarCorItem(View root, TextView cod, TextView desc,
                                ImageView img, boolean lido) {
        if (lido) {
            root.setBackgroundColor(Color.parseColor("#E8F5E9"));
            cod.setTextColor(Color.parseColor("#1B5E20"));
            desc.setTextColor(Color.parseColor("#2E7D32"));
            img.setColorFilter(Color.parseColor("#2E7D32"));
        } else {
            root.setBackgroundColor(Color.WHITE);
            cod.setTextColor(Color.parseColor("#9E9E9E"));
            desc.setTextColor(Color.parseColor("#BDBDBD"));
            img.setColorFilter(Color.parseColor("#BDBDBD"));
        }
    }

    // ── Listeners ─────────────────────────────────────────────
    private void configurarListeners() {
        btnLer.setOnClickListener(v -> alternarLeitura());
        btnModoToggle.setOnClickListener(v -> trocarModo());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnHistorico.setOnClickListener(v ->
                startActivity(new Intent(this, HistoricoActivity.class)));

        btnConcluir.setOnClickListener(v -> {
            List<Patrimonio> lidosList = new ArrayList<>();
            for (List<Patrimonio> grupo : mapaCompleto.values())
                for (Patrimonio p : grupo) if (estaLido(p)) lidosList.add(p);
            ConcluirHelper.executarPatrimonios(
                    this, executor,
                    codigoFilial, codigoLocal, chapaFuncionario,
                    "CATEGORIA", lidosList
            );
        });

        btnLimparTags.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Limpar leituras")
                        .setMessage("Deseja apagar todas as tags lidas desta categoria?")
                        .setPositiveButton("Limpar", (d, w) -> {
                            lidos.clear();
                            renderizarGrupos();
                            Toast.makeText(this, "Leituras limpas!", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    // ── Modo ──────────────────────────────────────────────────
    private void trocarModo() {
        if (modoRfid) pararLeituraRFID(); else pararLeitura2D();
        modoRfid = !modoRfid;
        atualizarBotaoModo();
        Toast.makeText(this, "Modo: " + (modoRfid ? "RFID" : "Código de Barras"),
                Toast.LENGTH_SHORT).show();
    }

    private void atualizarBotaoModo() {
        if (modoRfid) {
            btnModoToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#005eb8")));
            txtModoToggle.setText("Modo: RFID");
        } else {
            btnModoToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#388E3C")));
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

    // ── RFID ──────────────────────────────────────────────────
    private void inicializarRFID() {
        executor.execute(() -> {
            try {
                mReader = RFIDWithUHFUART.getInstance();
                if (mReader != null && mReader.init(this))
                    mainHandler.post(() -> Toast.makeText(this, "Leitor RFID pronto", Toast.LENGTH_SHORT).show());
            } catch (Exception e) { Log.e(TAG, "Init RFID", e); }
        });
    }

    private void iniciarLeituraRFID() {
        if (isReadingRFID) return;
        isReadingRFID = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));
        executor.execute(() -> {
            try { mReader.startInventoryTag(); loopRFID(); }
            catch (Exception e) { pararLeituraRFID(); }
        });
    }

    private void loopRFID() {
        executor.execute(new Runnable() {
            @Override public void run() {
                if (!isReadingRFID) return;
                try {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                    if (tagInfo != null) {
                        String norm = normalizarCodigo(tagInfo.getEPC());
                        if (norm != null && norm.length() >= 5) {
                            String chave5        = norm.substring(0, 5);
                            String codigoMontado = "040" + chave5;
                            Log.d(TAG, "EPC norm=" + norm + " | montado=" + codigoMontado);
                            processarTag(codigoMontado, chave5);
                        }
                    }
                } catch (Exception e) { Log.e(TAG, "Loop RFID", e); }
                if (isReadingRFID) mainHandler.postDelayed(this, 80);
            }
        });
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        mainHandler.post(() -> txtBotao.setText("Iniciar Leitura"));
        executor.execute(() -> { try { if (mReader != null) mReader.stopInventory(); } catch (Exception ignored) {} });
    }

    // ── Barcode 2D ────────────────────────────────────────────
    private void inicializarBarcode2D() {
        barcodeExecutor.execute(() -> {
            try {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
                if (barcodeDecoder.open(this)) {
                    barcodeDecoder.setDecodeCallback(entity -> {
                        if (entity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) return;
                        String raw  = entity.getBarcodeData();
                        String norm = normalizarCodigo(raw);
                        if (norm == null || norm.length() < 5) return;
                        String chave5        = norm.substring(0, 5);
                        String codigoMontado = "040" + chave5;
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                        executor.execute(() -> processarTag(codigoMontado, chave5));
                    });
                }
            } catch (Exception e) { Log.e(TAG, "Init Barcode", e); }
        });
    }

    private void iniciarLeitura2D() {
        if (isReading2D || barcodeDecoder == null) return;
        isReading2D = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));
        try { barcodeDecoder.startScan(); }
        catch (Exception e) { Log.e(TAG, "Start 2D", e); isReading2D = false; }
    }

    private void pararLeitura2D() {
        if (barcodeDecoder == null) return;
        isReading2D = false;
        mainHandler.post(() -> txtBotao.setText("Iniciar Leitura"));
        try { barcodeDecoder.stopScan(); } catch (Exception ignored) {}
    }

    // ── Normalização ──────────────────────────────────────────
    private String normalizarCodigo(String valor) {
        if (valor == null) return "";
        String epc = valor.trim();
        if (epc.equalsIgnoreCase("null") || epc.isEmpty()) return "";
        if (epc.startsWith("040") && epc.length() > 3) epc = epc.substring(3);
        else if (epc.startsWith("40") && epc.length() > 2) epc = epc.substring(2);
        epc = epc.replaceFirst("^0+", "");
        return epc.isEmpty() ? "" : epc;
    }

    // ── Processamento ─────────────────────────────────────────
    private void processarTag(String codigoMontado, String chave5) {
        // Dedup por chave5
        if (lidos.contains(chave5)) return;

        String grupoEncontrado = null;
        for (Map.Entry<String, List<Patrimonio>> entry : mapaCompleto.entrySet()) {
            for (Patrimonio p : entry.getValue()) {
                if (matchCodigoBarra(p.getCodigoBarra(), chave5)) {
                    grupoEncontrado = entry.getKey();
                    Log.i(TAG, "Match: " + codigoMontado + " → " + p.getCodigoBarra()
                            + " grupo=" + grupoEncontrado);
                    break;
                }
            }
            if (grupoEncontrado != null) break;
        }
        if (grupoEncontrado == null) return;

        // Se há filtro ativo, só aceita tags cujo grupo bate com o termo pesquisado
        if (!queryTexto.isEmpty() && !grupoEncontrado.toLowerCase().contains(queryTexto)) {
            Log.d(TAG, "Tag ignorada — grupo [" + grupoEncontrado + "] não bate com filtro [" + queryTexto + "]");
            return;
        }

        lidos.add(chave5);
        dbHelper.salvarHistoricoComTipo(codigoFilial, codigoLocal, chapaFuncionario,
                codigoMontado, "CATEGORIA");

        final boolean completo = grupoTodosLidos(grupoEncontrado);
        final String  gFinal   = grupoEncontrado;
        // Auto-expande o grupo quando um item é lido
        gruposExpandidos.add(gFinal);

        mainHandler.post(() -> {
            renderizarGrupos();
            if (completo) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200);
                mainHandler.postDelayed(() -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200), 300);
                Toast.makeText(this, "✓ \"" + gFinal + "\" completo!", Toast.LENGTH_SHORT).show();
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            }
        });
    }

    private boolean matchCodigoBarra(String codigoBarra, String chave5) {
        if (codigoBarra == null || codigoBarra.isEmpty()) return false;
        String normCB = normalizarCodigo(codigoBarra);
        if (normCB.length() < 5) return false;
        return chave5.equals(normCB.substring(0, 5));
    }

    private boolean estaLido(Patrimonio p) {
        String cb = p.getCodigoBarra();
        if (cb == null) return false;
        String normCB = normalizarCodigo(cb);
        if (normCB.length() < 5) return false;
        return lidos.contains(normCB.substring(0, 5));
    }

    private boolean grupoTodosLidos(String desc) {
        List<Patrimonio> itens = mapaCompleto.get(desc);
        if (itens == null || itens.isEmpty()) return false;
        for (Patrimonio p : itens) if (!estaLido(p)) return false;
        return true;
    }

    private int contarLidosGrupo(List<Patrimonio> itens) {
        int c = 0;
        for (Patrimonio p : itens) if (estaLido(p)) c++;
        return c;
    }

    private void atualizarContador() {
        int total = 0, lidosCount = 0;
        for (List<Patrimonio> itens : mapaCompleto.values()) {
            total += itens.size();
            for (Patrimonio p : itens) if (estaLido(p)) lidosCount++;
        }
        tvContador.setText("Lidos: " + lidosCount + " / " + total);
        tvContador.setTextColor(lidosCount == total && total > 0
                ? Color.parseColor("#2E7D32") : Color.parseColor("#333333"));
    }

    // ── Distância ─────────────────────────────────────────────
    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    executor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power))
                                mainHandler.post(() -> Toast.makeText(this,
                                        "Potência: " + power + " dBm", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) { Log.e(TAG, "Potência", e); }
                    });
                }).show();
    }

    // ── Resumo ────────────────────────────────────────────────
    private void abrirResumo() {
        List<Patrimonio> lidosList = new ArrayList<>();
        for (List<Patrimonio> grupo : mapaCompleto.values())
            for (Patrimonio p : grupo) if (estaLido(p)) lidosList.add(p);
        if (lidosList.isEmpty()) {
            Toast.makeText(this, "Nenhum item lido ainda!", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Patrimonio p : lidosList)
            sb.append("• ").append(p.getCodigoBarra()).append(" — ").append(p.getDescricao()).append("\n");
        new android.app.AlertDialog.Builder(this)
                .setTitle("Resumo — " + lidosList.size() + " item(s) lido(s)")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null).show();
    }

    // ── Gatilho físico ────────────────────────────────────────
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
        executor.shutdown();
        barcodeExecutor.shutdown();
        try { if (barcodeDecoder != null) barcodeDecoder.close(); } catch (Exception ignored) {}
        if (toneGen != null) toneGen.release();
    }
}