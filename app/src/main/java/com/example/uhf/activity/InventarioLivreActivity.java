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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventarioLivreActivity extends AppCompatActivity {

    private static final String TAG = "InventarioLivre";

    private RFIDWithUHFUART mReader;
    private BarcodeDecoder barcodeDecoder;

    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D = false;
    private volatile boolean modoRfid = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService rfidExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGen;

    // listaTags é a lista final enviada ao resumo/conclusão.
    // A posição 0 será sempre a última tag lida.
    private final List<String> listaTags = new ArrayList<>();

    // Mantida para controle interno de leituras.
    private final List<String> tagsLidas = new ArrayList<>();

    private long ultimoUpdateUI = 0;

    private DBHelper dbHelper;
    private String codigoFilial, codigoLocal, chapaFuncionario;
    private Local localBanco;
    private Usuario userBanco;

    private LinearLayout containerTags;
    private TextView tvTagCount, txtBotao, txtInfoTopo, txtInfoUser, txtModoToggle;

    private LinearLayout btnLerTags, btnLimparTags, btnConcluir, btnResumo,
            btnHistorico, btnDistancia, btnModoToggle;

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
        dbHelper = new DBHelper(this);

        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        tvTagCount = findViewById(R.id.tvTagCount);
        containerTags = findViewById(R.id.containerTags);

        btnLerTags = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnConcluir = findViewById(R.id.btnConcluir);
        btnResumo = findViewById(R.id.btnResumo);
        btnHistorico = findViewById(R.id.btnHistorico);
        btnDistancia = findViewById(R.id.btnDistancia);
        btnModoToggle = findViewById(R.id.btnModoToggleLivre);

        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtModoToggle = findViewById(R.id.txtModoToggleLivre);
        txtInfoTopo = findViewById(R.id.txtInfoTopo);
        txtInfoUser = findViewById(R.id.txtInfoUser);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        txtInfoUser.setText(
                codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario
        );

        txtInfoTopo.setText(
                localBanco != null && userBanco != null
                        ? localBanco.getLocalNome() + " | " + userBanco.getNome()
                        : "Dados não encontrados."
        );
    }

    private void inicializarLeitores() {
        rfidExecutor.execute(this::inicializarRFID);
        barcodeExecutor.execute(this::inicializarBarcode2D);
    }

    private void inicializarRFID() {
        try {
            mReader = RFIDWithUHFUART.getInstance();

            if (mReader != null && mReader.init(this)) {
                mainHandler.post(() ->
                        Toast.makeText(
                                this,
                                "Leitor RFID conectado!",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro RFID init", e);
        }
    }

    private void iniciarLeituraRFID() {
        if (isReadingRFID) return;

        isReadingRFID = true;

        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));

        rfidExecutor.execute(() -> {
            try {
                if (mReader != null) {
                    mReader.startInventoryTag();
                    loopRFID();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar leitura RFID", e);
                pararLeituraRFID();
            }
        });
    }

    private void loopRFID() {
        rfidExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isReadingRFID) return;

                try {
                    UHFTAGInfo tag = mReader.readTagFromBuffer();

                    if (tag != null) {
                        String norm = normalizarCodigo(tag.getEPC());

                        if (norm != null && norm.length() >= 5) {
                            adicionarTagSegura(norm.substring(0, 5));

                            if (toneGen != null) {
                                toneGen.startTone(
                                        ToneGenerator.TONE_PROP_BEEP,
                                        100
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro no loop RFID", e);
                }

                if (isReadingRFID) {
                    mainHandler.postDelayed(this, 80);
                }
            }
        });
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;

        mainHandler.post(() -> txtBotao.setText("Ler Tags"));

        rfidExecutor.execute(() -> {
            try {
                if (mReader != null) {
                    mReader.stopInventory();
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void inicializarBarcode2D() {
        try {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();

            if (barcodeDecoder.open(this)) {
                barcodeDecoder.setDecodeCallback(entity -> {
                    if (entity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) {
                        return;
                    }

                    String code = normalizarCodigo(entity.getBarcodeData());

                    if (code == null || code.isEmpty()) {
                        return;
                    }

                    if (toneGen != null) {
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                    }

                    rfidExecutor.execute(() -> adicionarTagSegura(code));
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro Barcode init", e);
        }
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

        mainHandler.post(() -> txtBotao.setText("Ler Tags"));

        try {
            barcodeDecoder.stopScan();
        } catch (Exception ignored) {
        }
    }

    private void trocarModo() {
        if (modoRfid) {
            pararLeituraRFID();
        } else {
            pararLeitura2D();
        }

        modoRfid = !modoRfid;

        atualizarBotaoModo();

        Toast.makeText(
                this,
                "Modo: " + (modoRfid ? "RFID" : "Código de Barras"),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void atualizarBotaoModo() {
        if (modoRfid) {
            btnModoToggle.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#005eb8"))
            );

            txtModoToggle.setText("Modo: RFID");

        } else {
            btnModoToggle.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#388E3C"))
            );

            txtModoToggle.setText("Modo: Cód. Barras");
        }
    }

    private void alternarLeitura() {
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

    private void configurarListeners() {
        btnLerTags.setOnClickListener(v -> alternarLeitura());

        btnModoToggle.setOnClickListener(v -> trocarModo());

        btnLimparTags.setOnClickListener(v -> limparTags());

        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());

        btnResumo.setOnClickListener(v -> abrirResumo());

        btnConcluir.setOnClickListener(v ->
                ConcluirHelper.executar(
                        this,
                        rfidExecutor,
                        codigoFilial,
                        codigoLocal,
                        chapaFuncionario,
                        "LIVRE",
                        new ArrayList<>(listaTags)
                )
        );

        btnHistorico.setOnClickListener(v ->
                startActivity(new Intent(this, HistoricoActivity.class))
        );
    }

    // ============================================================
    // TAGS
    // ============================================================

    private synchronized void adicionarTagSegura(String tag) {
        if (tag == null || tag.trim().isEmpty() || tag.equalsIgnoreCase("null")) {
            return;
        }

        tag = tag.trim();

        if (!tag.matches("\\d+")) {
            return;
        }

        long agora = System.currentTimeMillis();

        // Evita várias atualizações idênticas em milissegundos.
        if (agora - ultimoUpdateUI < 100) {
            return;
        }

        ultimoUpdateUI = agora;

        // Verifica se já existia antes de remover.
        boolean tagJaExistia = listaTags.contains(tag);

        // Se a tag já estava na lista, remove da posição antiga.
        // Assim ela poderá voltar como primeira.
        tagsLidas.remove(tag);
        listaTags.remove(tag);

        // A ÚLTIMA TAG LIDA SEMPRE SERÁ A PRIMEIRA DA LISTA.
        tagsLidas.add(0, tag);
        listaTags.add(0, tag);

        // Salva no histórico somente quando a tag aparece pela primeira vez.
        if (!tagJaExistia) {
            dbHelper.salvarHistoricoComTipo(
                    codigoFilial,
                    codigoLocal,
                    chapaFuncionario,
                    tag,
                    "LIVRE"
            );
        }

        final String tagFinal = tag;

        mainHandler.post(() -> {
            moverOuAdicionarItemNoTopo(tagFinal);

            tvTagCount.setText("Tags lidas: " + listaTags.size());
        });
    }

    /**
     * Se a tag já estiver aparecendo na tela:
     * remove da posição antiga e move para o topo.
     *
     * Se ainda não existir:
     * cria um item novo no topo.
     */
    private void moverOuAdicionarItemNoTopo(String rawTag) {
        for (int i = 0; i < containerTags.getChildCount(); i++) {
            View item = containerTags.getChildAt(i);

            TextView txtCodigo = item.findViewById(R.id.txtItemCodigo);

            if (txtCodigo != null
                    && rawTag.equals(txtCodigo.getText().toString())) {

                containerTags.removeViewAt(i);
                containerTags.addView(item, 0);

                return;
            }
        }

        adicionarItemNoContainer(rawTag);
    }

    private void adicionarItemNoContainer(String rawTag) {

        View itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_patrimonio, containerTags, false);

        TextView txtCodigo = itemView.findViewById(R.id.txtItemCodigo);
        TextView txtDescricao = itemView.findViewById(R.id.txtItemDescricao);
        TextView txtLocal = itemView.findViewById(R.id.txtItemLocal);
        ImageView imgPatrimonio = itemView.findViewById(R.id.imgPatrimonio);

        // =========================================================
        // ESTADO INICIAL — CARREGANDO
        // =========================================================

        txtCodigo.setText(rawTag);
        txtDescricao.setText("Carregando...");
        txtLocal.setVisibility(View.GONE);

        itemView.setBackgroundColor(
                Color.parseColor("#F5F5F5")
        );

        txtCodigo.setTextColor(
                Color.parseColor("#9E9E9E")
        );

        txtDescricao.setTextColor(
                Color.parseColor("#757575")
        );

        // ÍCONE CINZA
        imgPatrimonio.clearColorFilter();
        imgPatrimonio.setImageTintList(
                ColorStateList.valueOf(
                        Color.parseColor("#9E9E9E")
                )
        );
        imgPatrimonio.setImageResource(
                R.drawable.ic_loading
        );

        // Item novo entra no topo
        containerTags.addView(itemView, 0);

        // =========================================================
        // BUSCAR PATRIMÔNIO
        // =========================================================

        new Thread(() -> {

            Patrimonio patrimonio =
                    dbHelper.buscarPatrimonioPorCodigoBarra("040" + rawTag);

            mainHandler.post(() -> {

                // =====================================================
                // ENCONTRADO
                // =====================================================

                if (patrimonio != null
                        && patrimonio.getDescricao() != null
                        && !patrimonio.getDescricao().isEmpty()) {

                    String descricao = patrimonio.getDescricao();

                    String texto = descricao.length() > 25
                            ? descricao.substring(0, 25) + "..."
                            : descricao;

                    txtDescricao.setText(texto);

                    // Fundo verde
                    itemView.setBackgroundColor(
                            Color.parseColor("#E8F5E9")
                    );

                    // Código verde escuro
                    txtCodigo.setTextColor(
                            Color.parseColor("#1B5E20")
                    );

                    // Descrição verde
                    txtDescricao.setTextColor(
                            Color.parseColor("#2E7D32")
                    );

                    // =================================================
                    // ÍCONE VERDE ORIGINAL
                    // =================================================

                    imgPatrimonio.clearColorFilter();
                    imgPatrimonio.setImageTintList(null);
                    imgPatrimonio.setImageResource(
                            R.drawable.ic_ativo_pat
                    );

                    String nomeLocal = patrimonio.getNomeLocal();

                    if (nomeLocal != null && !nomeLocal.isEmpty()) {

                        txtLocal.setText(
                                "Local: " + nomeLocal
                        );

                        txtLocal.setTextColor(
                                Color.parseColor("#2E7D32")
                        );

                        txtLocal.setVisibility(View.VISIBLE);

                    } else {

                        txtLocal.setText("");
                        txtLocal.setVisibility(View.GONE);
                    }

                }

                // =====================================================
                // DESCONHECIDO
                // =====================================================

                else {

                    txtDescricao.setText(
                            "DESCONHECIDO"
                    );

                    // Fundo amarelo claro
                    itemView.setBackgroundColor(
                            Color.parseColor("#FFFDE7")
                    );

                    // Código
                    txtCodigo.setTextColor(
                            Color.parseColor("#F57F17")
                    );

                    // Descrição
                    txtDescricao.setTextColor(
                            Color.parseColor("#E65100")
                    );

                    txtLocal.setText("");
                    txtLocal.setVisibility(View.GONE);

                    // =================================================
                    // ÍCONE DESCONHECIDO
                    // =================================================

                    imgPatrimonio.clearColorFilter();
                    imgPatrimonio.setImageTintList(null);
                    imgPatrimonio.setImageResource(
                            R.drawable.ic_desconhecido
                    );
                }
            });

        }).start();
    }

    private void limparTags() {
        pararLeituraRFID();
        pararLeitura2D();

        synchronized (this) {
            tagsLidas.clear();
            listaTags.clear();
        }

        mainHandler.post(() -> {
            containerTags.removeAllViews();
            tvTagCount.setText("Tags lidas: 0");
        });

        Toast.makeText(this, "Lista limpa!", Toast.LENGTH_SHORT).show();
    }

    private void abrirResumo() {
        if (listaTags.isEmpty()) {
            Toast.makeText(this, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show();
            return;
        }

        String nomeLocalAtual = localBanco != null
                ? localBanco.getLocalNome()
                : codigoLocal;

        ArrayList<String> tagsLidas = new ArrayList<>(listaTags);
        ArrayList<String> descricoesLidas = new ArrayList<>();

        // TRUE = pertence ao local atual
        ArrayList<Boolean> itensPertencemAoLocal = new ArrayList<>();

        // =========================================================
        // ITENS LIDOS
        // =========================================================
        for (String codigoBarra : tagsLidas) {

            descricoesLidas.add(
                    codigoBarra + "\n" +
                            "Local: " + nomeLocalAtual
            );

            // Neste fluxo, todos os itens pertencem ao local
            itensPertencemAoLocal.add(true);
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

        // Envia a informação de pertencimento
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

        intent.putExtra(
                "totalItensLidos",
                tagsLidas.size()
        );

        intent.putExtra(
                "totalIdentificados",
                tagsLidas.size()
        );

        // Neste fluxo não existem itens fora do local
        intent.putExtra(
                "totalForaDoLocal",
                0
        );

        startActivity(intent);
    }
    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {
                "Curta (10 dBm)",
                "Média (20 dBm)",
                "Longa (30 dBm)"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power;

                    if (which == 0) {
                        power = 10;
                    } else if (which == 1) {
                        power = 20;
                    } else {
                        power = 30;
                    }

                    rfidExecutor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power)) {
                                mainHandler.post(() ->
                                        Toast.makeText(
                                                this,
                                                "Potência: " + power + " dBm",
                                                Toast.LENGTH_SHORT
                                        ).show()
                                );
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Erro potência", e);
                        }
                    });
                })
                .show();
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }

        String epc = valor.trim();

        if (epc.equalsIgnoreCase("null") || epc.isEmpty()) {
            return "";
        }

        if (epc.startsWith("040") && epc.length() > 3) {
            epc = epc.substring(3);

        } else if (epc.startsWith("40") && epc.length() > 2) {
            epc = epc.substring(2);
        }

        epc = epc.replaceFirst("^0+", "");

        return epc.isEmpty() ? "" : epc;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == 293
                && event.getAction() == KeyEvent.ACTION_DOWN) {

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
}