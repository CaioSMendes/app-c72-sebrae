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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.model.Local;
import com.example.uhf.model.Patrimonio;
import com.example.uhf.model.Usuario;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventarioLocalActivity extends AppCompatActivity {

    private static final String TAG = "InventarioLocal";

    // ── Estados de item ───────────────────────────────────────
    private static final int ESTADO_PENDENTE       = 0; // cinza
    private static final int ESTADO_ENCONTRADO     = 1; // verde
    private static final int ESTADO_NAO_ENCONTRADO = 2; // vermelho

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

    private List<Patrimonio> todosPatrimonios = new ArrayList<>();
    private List<Patrimonio> listaFiltrada    = new ArrayList<>();

    // codigoBarra → estado
    private final Map<String, Integer> estadoPatrimonios = new HashMap<>();

    // Ordem de leitura dos patrimônios do local (codigoBarra, mais recente primeiro)
    private final List<String> ordemLeitura = new ArrayList<>();

    // Tags lidas que NÃO estão no cadastro deste local
    // chave = chave5; valor = objeto com info completa
    private final java.util.LinkedHashMap<String, InfoTagFora> tagsForaDoLocal = new java.util.LinkedHashMap<>();
    // Chaves aceitas pelo usuário como "entrada"
    private final Set<String> tagForaAceitas = new HashSet<>();

    /** Info de uma tag lida que não pertence a este local */
    private static class InfoTagFora {
        final String codigoExibido; // "04012345"
        final String descricao;     // descrição do patrimônio no banco (ou vazio se desconhecido)
        final String localOrigem;   // nome do local onde este patrimônio pertence (ou vazio)
        InfoTagFora(String codigoExibido, String descricao, String localOrigem) {
            this.codigoExibido = codigoExibido;
            this.descricao     = descricao;
            this.localOrigem   = localOrigem;
        }
    }

    // ── Views ─────────────────────────────────────────────────
    private PatrimonioLocalAdapter adapter;
    private ListView  listView;
    private TextView  tvContador, txtInfoTopo, txtInfoUser, txtBotao, txtModoToggle;
    private LinearLayout btnLer, btnConcluir, btnDistancia, btnResumo, btnHistorico, btnModoToggle, btnLimpar;
    private EditText etBusca;

    // ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario_local);

        dbHelper         = new DBHelper(this);
        codigoFilial     = getIntent().getStringExtra("codigoFilial");
        codigoLocal      = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");
        localBanco       = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco        = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);
        toneGen          = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        vincularViews();
        carregarPatrimonios();
        configurarBusca();
        configurarListeners();
        inicializarRFID();
        inicializarBarcode2D();
        atualizarBotaoModo();
    }

    // ── Views ─────────────────────────────────────────────────
    private void vincularViews() {
        listView      = findViewById(R.id.listViewLocal);
        tvContador    = findViewById(R.id.tvContadorLocal);
        txtInfoTopo   = findViewById(R.id.txtInfoTopoLocal);
        txtInfoUser   = findViewById(R.id.txtInfoUserLocal);
        txtBotao      = findViewById(R.id.txtBotaoLocal);
        btnLer        = findViewById(R.id.btnLerLocal);
        btnConcluir   = findViewById(R.id.btnConcluirLocal);
        btnDistancia  = findViewById(R.id.btnDistanciaLocal);
        btnResumo     = findViewById(R.id.btnResumoLocal);
        btnHistorico  = findViewById(R.id.btnHistoricoLocal);
        etBusca       = findViewById(R.id.etBuscaLocal);
        btnModoToggle = findViewById(R.id.btnModoToggleLocal);
        txtModoToggle = findViewById(R.id.txtModoToggleLocal);
        btnLimpar     = findViewById(R.id.btnLimparLocal);

        txtInfoTopo.setText(localBanco != null && userBanco != null
                ? localBanco.getLocalNome() + " | " + userBanco.getNome()
                : "Dados não encontrados.");
        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);
    }

    // ── Dados ─────────────────────────────────────────────────
    private void carregarPatrimonios() {
        todosPatrimonios = dbHelper.listarPatrimoniosPorLocal(codigoLocal);
        estadoPatrimonios.clear();
        ordemLeitura.clear();
        for (Patrimonio p : todosPatrimonios)
            estadoPatrimonios.put(p.getCodigoBarra(), ESTADO_PENDENTE);
        listaFiltrada.clear();
        listaFiltrada.addAll(todosPatrimonios);
        adapter = new PatrimonioLocalAdapter();
        listView.setAdapter(adapter);
        atualizarContador();
    }

    private void configurarBusca() {
        etBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filtrar(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filtrar(String query) {
        listaFiltrada.clear();
        if (query == null || query.trim().isEmpty()) {
            listaFiltrada.addAll(todosPatrimonios);
        } else {
            String q = query.toLowerCase().trim();
            for (Patrimonio p : todosPatrimonios) {
                if (p.getDescricao().toLowerCase().contains(q)
                        || p.getCodigoBarra().toLowerCase().contains(q)
                        || p.getPatrimonio().toLowerCase().contains(q))
                    listaFiltrada.add(p);
            }
        }
        adapter.notifyDataSetChanged();
        atualizarContador();
    }

    // ── Listeners ─────────────────────────────────────────────
    private void configurarListeners() {
        btnLer.setOnClickListener(v -> alternarLeitura());
        btnModoToggle.setOnClickListener(v -> trocarModo());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnResumo.setOnClickListener(v -> abrirResumo());

        btnConcluir.setOnClickListener(v -> {
            List<Patrimonio> lidosList = new ArrayList<>();
            for (Patrimonio p : todosPatrimonios)
                if (getEstado(p) == ESTADO_ENCONTRADO) lidosList.add(p);
            ConcluirHelper.executarPatrimonios(
                    this, executor,
                    codigoFilial, codigoLocal, chapaFuncionario,
                    "LOCAL", lidosList
            );
        });

        btnHistorico.setOnClickListener(v ->
                startActivity(new Intent(this, HistoricoActivity.class)));

        btnLimpar.setOnClickListener(v -> confirmarLimpeza());
    }

    private void confirmarLimpeza() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Limpar leituras")
                .setMessage("Deseja apagar todas as tags lidas e reiniciar o inventário?")
                .setPositiveButton("Limpar", (d, w) -> limparTags())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void limparTags() {
        pararLeituraRFID();
        pararLeitura2D();
        // Reseta todos os estados para PENDENTE
        for (String cb : estadoPatrimonios.keySet())
            estadoPatrimonios.put(cb, ESTADO_PENDENTE);
        ordemLeitura.clear();
        tagsForaDoLocal.clear();
        tagForaAceitas.clear();
        mainHandler.post(() -> {
            adapter.notifyDataSetChanged();
            atualizarContador();
            Toast.makeText(this, "Leituras limpas!", Toast.LENGTH_SHORT).show();
        });
    }

    // ── Modo ──────────────────────────────────────────────────
    private void trocarModo() {
        if (modoRfid) pararLeituraRFID(); else pararLeitura2D();
        modoRfid = !modoRfid;
        atualizarBotaoModo();
        Toast.makeText(this,
                "Modo: " + (modoRfid ? "RFID" : "Código de Barras"),
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
                        String epcBruto = tagInfo.getEPC();
                        // Mesma normalização do InventarioLivre que funciona:
                        // normaliza → pega os 5 primeiros dígitos → monta "040XXXXX"
                        String norm = normalizarCodigo(epcBruto);
                        if (norm != null && norm.length() >= 5) {
                            String chave5   = norm.substring(0, 5);       // "12345"
                            String codigoMontado = "040" + chave5;        // "04012345"
                            Log.d(TAG, "EPC bruto=" + epcBruto + " | norm=" + norm + " | montado=" + codigoMontado);
                            processarCodigo(codigoMontado, chave5);
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
                        String raw = entity.getBarcodeData();
                        String norm = normalizarCodigo(raw);
                        if (norm == null || norm.length() < 5) return;
                        String chave5        = norm.substring(0, 5);
                        String codigoMontado = "040" + chave5;
                        Log.d(TAG, "Barcode bruto=" + raw + " | montado=" + codigoMontado);
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                        executor.execute(() -> processarCodigo(codigoMontado, chave5));
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

    // ── Normalização — idêntica ao InventarioLivre ────────────
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
    /**
     * codigoMontado = "040" + 5 dígitos  (ex: "04012345")
     * chave5        = os 5 dígitos        (ex: "12345")  — chave de dedup
     */
    private void processarCodigo(String codigoMontado, String chave5) {
        // Dedup: já processado?
        if (tagsForaDoLocal.containsKey(chave5)) return;
        for (Patrimonio p : todosPatrimonios) {
            if (getEstado(p) != ESTADO_PENDENTE
                    && matchCodigoBarra(p.getCodigoBarra(), chave5)) return;
        }

        // Tenta encontrar no cadastro deste local
        Patrimonio encontrado = null;
        for (Patrimonio p : todosPatrimonios) {
            if (matchCodigoBarra(p.getCodigoBarra(), chave5)) {
                encontrado = p;
                Log.i(TAG, "Match: " + codigoMontado + " → " + p.getCodigoBarra());
                break;
            }
        }

        if (encontrado != null) {
            // ✅ Pertence ao local
            final String cbFinal = encontrado.getCodigoBarra();
            estadoPatrimonios.put(cbFinal, ESTADO_ENCONTRADO);
            // Insere no topo da ordem de leitura (remove se já existia, reinserindo no topo)
            ordemLeitura.remove(cbFinal);
            ordemLeitura.add(0, cbFinal);
            dbHelper.salvarHistoricoComTipo(codigoFilial, codigoLocal, chapaFuncionario, cbFinal, "LOCAL");
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            mainHandler.post(() -> { adapter.notifyDataSetChanged(); atualizarContador(); });
        } else {
            // ⚠️ Não pertence ao local — busca info no banco global
            Log.w(TAG, "Fora do local: " + codigoMontado);
            String descGlobal  = "";
            String localOrigem = "";
            // Varre todos os patrimônios do banco para achar pela chave5
            List<Patrimonio> todos = dbHelper.listarPatrimonios();
            for (Patrimonio p : todos) {
                if (matchCodigoBarra(p.getCodigoBarra(), chave5)) {
                    descGlobal  = p.getDescricao()  != null ? p.getDescricao()  : "";
                    localOrigem = p.getNomeLocal()   != null ? p.getNomeLocal()  : "";
                    if (localOrigem.isEmpty() && p.getCodLocal() != null)
                        localOrigem = p.getCodLocal();
                    break;
                }
            }
            tagsForaDoLocal.put(chave5, new InfoTagFora(codigoMontado, descGlobal, localOrigem));
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 150);
            mainHandler.post(() -> { adapter.notifyDataSetChanged(); atualizarContador(); });
        }
    }

    /**
     * Verifica se o codigoBarra do banco corresponde à chave de 5 dígitos lida.
     * codigoBarra típico: "04012345" → parte útil = "12345"
     */
    private boolean matchCodigoBarra(String codigoBarra, String chave5) {
        if (codigoBarra == null || codigoBarra.isEmpty()) return false;
        // Normaliza o codigoBarra do banco da mesma forma
        String normCB = normalizarCodigo(codigoBarra);
        if (normCB.length() < 5) return false;
        String chave5CB = normCB.substring(0, 5);
        return chave5.equals(chave5CB);
    }

    // ── Estado helpers ────────────────────────────────────────
    private int getEstado(Patrimonio p) {
        Integer e = estadoPatrimonios.get(p.getCodigoBarra());
        return e != null ? e : ESTADO_PENDENTE;
    }

    // ── Contador ──────────────────────────────────────────────
    private void atualizarContador() {
        int total = todosPatrimonios.size(), encontrados = 0;
        for (Patrimonio p : todosPatrimonios)
            if (getEstado(p) == ESTADO_ENCONTRADO) encontrados++;
        int foraCount = tagsForaDoLocal.size();
        String txt = "Lidos: " + encontrados + " / " + total;
        if (foraCount > 0) txt += "  ⚠ " + foraCount + " fora";
        tvContador.setText(txt);
        tvContador.setTextColor(encontrados == total && total > 0
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
        StringBuilder sb = new StringBuilder();
        int enc = 0;
        for (Patrimonio p : todosPatrimonios) {
            if (getEstado(p) == ESTADO_ENCONTRADO) {
                sb.append("✓ ").append(p.getCodigoBarra()).append(" — ").append(p.getDescricao()).append("\n");
                enc++;
            }
        }
        for (Patrimonio p : todosPatrimonios) {
            if (getEstado(p) == ESTADO_NAO_ENCONTRADO)
                sb.append("✗ ").append(p.getCodigoBarra()).append(" — ").append(p.getDescricao()).append("\n");
        }
        if (!tagsForaDoLocal.isEmpty()) {
            sb.append("\n⚠ Fora do local:\n");
            for (InfoTagFora info : tagsForaDoLocal.values()) {
                sb.append("  ").append(info.codigoExibido);
                if (!info.descricao.isEmpty())
                    sb.append(" — ").append(info.descricao);
                if (!info.localOrigem.isEmpty())
                    sb.append(" (").append(info.localOrigem).append(")");
                sb.append("\n");
            }
        }
        if (enc == 0 && tagsForaDoLocal.isEmpty()) {
            Toast.makeText(this, "Nenhum item lido ainda!", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Resumo — " + enc + " encontrado(s)")
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

    // ── Adapter: lidos no topo (ordem de leitura), pendentes depois, fora ao final ──
    private class PatrimonioLocalAdapter extends ArrayAdapter<Patrimonio> {

        PatrimonioLocalAdapter() { super(InventarioLocalActivity.this, 0, listaFiltrada); }

        /** Monta a lista ordenada: lidos (por ordemLeitura) + pendentes + fora do local */
        private List<Object> buildOrdered() {
            List<Object> result = new ArrayList<>();

            // 1. Lidos na ordem de leitura (mais recente primeiro)
            for (String cb : ordemLeitura) {
                for (Patrimonio p : listaFiltrada) {
                    if (p.getCodigoBarra().equals(cb)) { result.add(p); break; }
                }
            }

            // 2. Não encontrados (vermelho) — após os lidos
            for (Patrimonio p : listaFiltrada) {
                if (getEstado(p) == ESTADO_NAO_ENCONTRADO && !ordemLeitura.contains(p.getCodigoBarra()))
                    result.add(p);
            }

            // 3. Pendentes (cinza)
            for (Patrimonio p : listaFiltrada) {
                if (getEstado(p) == ESTADO_PENDENTE) result.add(p);
            }

            // 4. Tags fora do local (amarelas/verdes) — mais recentes primeiro
            List<String> chavesForaRev = new ArrayList<>(tagsForaDoLocal.keySet());
            java.util.Collections.reverse(chavesForaRev);
            for (String chave5 : chavesForaRev) result.add(chave5);

            return result;
        }

        @Override
        public int getCount() { return buildOrdered().size(); }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_patrimonio, parent, false);

            TextView  txtCodigo    = convertView.findViewById(R.id.txtItemCodigo);
            TextView  txtDescricao = convertView.findViewById(R.id.txtItemDescricao);
            ImageView imgStatus    = convertView.findViewById(R.id.imgPatrimonio);

            Object item = buildOrdered().get(position);

            if (item instanceof Patrimonio) {
                // ── Patrimônio do local ────────────────────────
                Patrimonio p     = (Patrimonio) item;
                int        estado = getEstado(p);

                txtCodigo.setText(p.getCodigoBarra());
                txtDescricao.setText(p.getDescricao());

                switch (estado) {
                    case ESTADO_ENCONTRADO:
                        convertView.setBackgroundColor(Color.parseColor("#E8F5E9"));
                        txtCodigo.setTextColor(Color.parseColor("#1B5E20"));
                        txtDescricao.setTextColor(Color.parseColor("#2E7D32"));
                        imgStatus.setColorFilter(Color.parseColor("#2E7D32"));
                        convertView.setOnClickListener(v -> {
                            estadoPatrimonios.put(p.getCodigoBarra(), ESTADO_NAO_ENCONTRADO);
                            // Remove da ordemLeitura para não ficar no topo como "não encontrado"
                            ordemLeitura.remove(p.getCodigoBarra());
                            adapter.notifyDataSetChanged();
                            atualizarContador();
                            Toast.makeText(InventarioLocalActivity.this,
                                    "Marcado como não encontrado", Toast.LENGTH_SHORT).show();
                        });
                        break;

                    case ESTADO_NAO_ENCONTRADO:
                        convertView.setBackgroundColor(Color.parseColor("#FFEBEE"));
                        txtCodigo.setTextColor(Color.parseColor("#B71C1C"));
                        txtDescricao.setTextColor(Color.parseColor("#C62828"));
                        imgStatus.setColorFilter(Color.parseColor("#C62828"));
                        convertView.setOnClickListener(v -> {
                            estadoPatrimonios.put(p.getCodigoBarra(), ESTADO_ENCONTRADO);
                            ordemLeitura.remove(p.getCodigoBarra());
                            ordemLeitura.add(0, p.getCodigoBarra());
                            adapter.notifyDataSetChanged();
                            atualizarContador();
                            Toast.makeText(InventarioLocalActivity.this,
                                    "Marcado como encontrado", Toast.LENGTH_SHORT).show();
                        });
                        break;

                    default: // PENDENTE — cinza, sem clique
                        convertView.setBackgroundColor(Color.parseColor("#F5F5F5"));
                        txtCodigo.setTextColor(Color.parseColor("#9E9E9E"));
                        txtDescricao.setTextColor(Color.parseColor("#BDBDBD"));
                        imgStatus.setColorFilter(Color.parseColor("#BDBDBD"));
                        convertView.setOnClickListener(null);
                        break;
                }

            } else {
                // ── Tag fora do local (String = chave5) ───────
                String chave5        = (String) item;
                InfoTagFora info     = tagsForaDoLocal.get(chave5);
                boolean aceita       = tagForaAceitas.contains(chave5);

                txtCodigo.setText(info.codigoExibido);

                if (aceita) {
                    // Descrição + local de origem, marcada como aceita
                    String labelAceita = "✓ Entrada aceita";
                    if (!info.descricao.isEmpty())
                        labelAceita += " — " + info.descricao;
                    if (!info.localOrigem.isEmpty())
                        labelAceita += " (orig: " + info.localOrigem + ")";
                    txtDescricao.setText(labelAceita);
                    convertView.setBackgroundColor(Color.parseColor("#E8F5E9"));
                    txtCodigo.setTextColor(Color.parseColor("#1B5E20"));
                    txtDescricao.setTextColor(Color.parseColor("#2E7D32"));
                    imgStatus.setColorFilter(Color.parseColor("#2E7D32"));
                    convertView.setOnClickListener(v -> {
                        tagForaAceitas.remove(chave5);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(InventarioLocalActivity.this,
                                "Entrada removida", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Mostra descrição e local de origem se encontrado no banco
                    String labelFora;
                    if (!info.descricao.isEmpty()) {
                        labelFora = "⚠ " + info.descricao;
                        if (!info.localOrigem.isEmpty())
                            labelFora += " | Local: " + info.localOrigem;
                        labelFora += " — toque para aceitar";
                    } else {
                        labelFora = "⚠ Tag desconhecida — toque para aceitar entrada";
                    }
                    txtDescricao.setText(labelFora);
                    convertView.setBackgroundColor(Color.parseColor("#FFFDE7"));
                    txtCodigo.setTextColor(Color.parseColor("#F57F17"));
                    txtDescricao.setTextColor(Color.parseColor("#E65100"));
                    imgStatus.setColorFilter(Color.parseColor("#F57F17"));
                    convertView.setOnClickListener(v -> {
                        tagForaAceitas.add(chave5);
                        dbHelper.salvarHistoricoComTipo(codigoFilial, codigoLocal,
                                chapaFuncionario, info.codigoExibido, "LOCAL_ENTRADA");
                        adapter.notifyDataSetChanged();
                        Toast.makeText(InventarioLocalActivity.this,
                                "Tag aceita como entrada", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            return convertView;
        }
    }
}