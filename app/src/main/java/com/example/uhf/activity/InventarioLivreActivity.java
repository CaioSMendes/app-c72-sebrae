package com.example.uhf.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.adapter.SimpleTagAdapter;
import com.example.uhf.model.Local;
import com.example.uhf.model.Usuario;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventarioLivreActivity extends AppCompatActivity {

    private static final String TAG = "InventarioLivre";

    // ── Leitores ──────────────────────────────────────────────
    private RFIDWithUHFUART mReader;
    private BarcodeDecoder  barcodeDecoder;

    // ── Estados ───────────────────────────────────────────────
    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D   = false;
    private volatile boolean modoRfid      = true;

    private final Handler        mainHandler     = new Handler(Looper.getMainLooper());
    private final ExecutorService rfidExecutor    = Executors.newSingleThreadExecutor();
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGen;

    // ── Dados ─────────────────────────────────────────────────
    private final List<String> listaTags = new ArrayList<>();
    private final List<String> tagsLidas = new ArrayList<>();
    private long ultimoUpdateUI = 0;

    private DBHelper dbHelper;
    private String codigoFilial, codigoLocal, chapaFuncionario;
    private Local   localBanco;
    private Usuario userBanco;

    // ── Views ─────────────────────────────────────────────────
    private SimpleTagAdapter adapter;
    private TextView     tvTagCount, txtBotao, txtInfoTopo, txtInfoUser, txtModoToggle;
    private ListView     listViewTags;
    private LinearLayout btnLerTags, btnLimparTags, btnConcluir, btnResumo,
            btnHistorico, btnDistancia, btnModoToggle;

    // ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventario_livre);

        inicializarComponentes();
        inicializarLeitores();
        configurarListeners();
        atualizarBotaoModo();
    }

    private void inicializarComponentes() {
        dbHelper         = new DBHelper(this);
        codigoFilial     = getIntent().getStringExtra("codigoFilial");
        codigoLocal      = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco  = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        tvTagCount    = findViewById(R.id.tvTagCount);
        listViewTags  = findViewById(R.id.listViewTags);
        btnLerTags    = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnConcluir   = findViewById(R.id.btnConcluir);
        btnResumo     = findViewById(R.id.btnResumo);
        btnHistorico  = findViewById(R.id.btnHistorico);
        btnDistancia  = findViewById(R.id.btnDistancia);
        btnModoToggle = findViewById(R.id.btnModoToggleLivre);
        txtBotao      = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtModoToggle = findViewById(R.id.txtModoToggleLivre);
        txtInfoTopo   = findViewById(R.id.txtInfoTopo);
        txtInfoUser   = findViewById(R.id.txtInfoUser);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        adapter = new SimpleTagAdapter(this, listaTags, dbHelper);
        listViewTags.setAdapter(adapter);

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);
        txtInfoTopo.setText(localBanco != null && userBanco != null
                ? localBanco.getLocalNome() + " | " + userBanco.getNome()
                : "Dados não encontrados.");
    }

    private void inicializarLeitores() {
        rfidExecutor.execute(this::inicializarRFID);
        barcodeExecutor.execute(this::inicializarBarcode2D);
    }

    // ── RFID ──────────────────────────────────────────────────
    private void inicializarRFID() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && mReader.init(this))
                mainHandler.post(() -> Toast.makeText(this, "Leitor RFID conectado!", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { Log.e(TAG, "Erro RFID init", e); }
    }

    private void iniciarLeituraRFID() {
        if (isReadingRFID) return;
        isReadingRFID = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));
        rfidExecutor.execute(() -> {
            try { mReader.startInventoryTag(); loopRFID(); }
            catch (Exception e) { pararLeituraRFID(); }
        });
    }

    private void loopRFID() {
        rfidExecutor.execute(new Runnable() {
            @Override public void run() {
                if (!isReadingRFID) return;
                try {
                    UHFTAGInfo tag = mReader.readTagFromBuffer();
                    if (tag != null) {
                        String norm = normalizarCodigo(tag.getEPC());
                        if (norm != null && norm.length() >= 5) {
                            adicionarTagSegura(norm.substring(0, 5));
                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                        }
                    }
                } catch (Exception e) { Log.e(TAG, "Loop RFID", e); }
                if (isReadingRFID) mainHandler.postDelayed(this, 80);
            }
        });
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        mainHandler.post(() -> txtBotao.setText("Ler Tags"));
        rfidExecutor.execute(() -> { try { if (mReader != null) mReader.stopInventory(); } catch (Exception ignored) {} });
    }

    // ── Barcode 2D ────────────────────────────────────────────
    private void inicializarBarcode2D() {
        try {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            if (barcodeDecoder.open(this)) {
                barcodeDecoder.setDecodeCallback(entity -> {
                    if (entity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) return;
                    String code = normalizarCodigo(entity.getBarcodeData());
                    if (code == null || code.isEmpty()) return;
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                    rfidExecutor.execute(() -> adicionarTagSegura(code));
                });
            }
        } catch (Exception e) { Log.e(TAG, "Erro Barcode init", e); }
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
        mainHandler.post(() -> txtBotao.setText("Ler Tags"));
        try { barcodeDecoder.stopScan(); } catch (Exception ignored) {}
    }

    // ── Alternância de modo ───────────────────────────────────
    private void trocarModo() {
        if (modoRfid) pararLeituraRFID();
        else          pararLeitura2D();

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
            if (isReading2D)   pararLeitura2D();   else iniciarLeitura2D();
        }
    }

    // ── Listeners ─────────────────────────────────────────────
    private void configurarListeners() {
        btnLerTags.setOnClickListener(v -> alternarLeitura());

        btnModoToggle.setOnClickListener(v -> trocarModo());

        btnLimparTags.setOnClickListener(v -> limparTags());

        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());

        btnResumo.setOnClickListener(v -> abrirResumo());

        btnConcluir.setOnClickListener(v ->
                ConcluirHelper.executar(
                        this, rfidExecutor,
                        codigoFilial, codigoLocal, chapaFuncionario,
                        "LIVRE",
                        new ArrayList<>(listaTags)
                )
        );

        btnHistorico.setOnClickListener(v -> startActivity(new Intent(this, HistoricoActivity.class)));
    }

    // ── Tags ──────────────────────────────────────────────────
    private synchronized void adicionarTagSegura(String tag) {
        if (tag == null || tag.trim().isEmpty() || tag.equalsIgnoreCase("null")) return;
        tag = tag.trim();
        if (!tag.matches("\\d+")) return;

        long agora = System.currentTimeMillis();
        if (agora - ultimoUpdateUI < 100) return;
        ultimoUpdateUI = agora;

        if (!tagsLidas.contains(tag) && !listaTags.contains(tag)) {
            tagsLidas.add(0, tag);
            listaTags.add(0, tag);
            dbHelper.salvarHistoricoComTipo(codigoFilial, codigoLocal, chapaFuncionario, tag, "LIVRE");
            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                tvTagCount.setText("Tags lidas: " + listaTags.size());
                listViewTags.smoothScrollToPosition(0);
            });
        }
    }

    private void limparTags() {
        pararLeituraRFID();
        pararLeitura2D();
        synchronized (this) {
            tagsLidas.clear();
            listaTags.clear();
            adapter.notifyDataSetChanged();
            tvTagCount.setText("Tags lidas: 0");
        }
        Toast.makeText(this, "Lista limpa!", Toast.LENGTH_SHORT).show();
    }

    private void abrirResumo() {
        if (listaTags.isEmpty()) { Toast.makeText(this, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show(); return; }
        Intent intent = new Intent(this, ResumoActivity.class);
        intent.putStringArrayListExtra("tags", new ArrayList<>(listaTags));
        intent.putExtra("codigoFilial",     codigoFilial);
        intent.putExtra("codigoLocal",      codigoLocal);
        intent.putExtra("chapaFuncionario", chapaFuncionario);
        intent.putExtra("nomeUsuario",  userBanco  != null ? userBanco.getNome()       : "");
        intent.putExtra("nomeLocal",    localBanco != null ? localBanco.getLocalNome() : "");
        startActivity(intent);
    }

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    rfidExecutor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power))
                                mainHandler.post(() -> Toast.makeText(this,
                                        "Potência: " + power + " dBm", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) { Log.e(TAG, "Erro potência", e); }
                    });
                }).show();
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) return "";
        String epc = valor.trim();
        if (epc.equalsIgnoreCase("null") || epc.isEmpty()) return "";
        if (epc.startsWith("040") && epc.length() > 3) epc = epc.substring(3);
        else if (epc.startsWith("40") && epc.length() > 2) epc = epc.substring(2);
        epc = epc.replaceFirst("^0+", "");
        return epc.isEmpty() ? "" : epc;
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
        rfidExecutor.shutdown();
        barcodeExecutor.shutdown();
        try { if (barcodeDecoder != null) barcodeDecoder.close(); } catch (Exception ignored) {}
        if (toneGen != null) toneGen.release();
    }
}