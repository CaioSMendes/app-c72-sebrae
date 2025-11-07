package com.example.uhf.activity;

import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.adapter.SimpleTagAdapter;
import com.example.uhf.model.Local;
import com.example.uhf.model.Usuario;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.List;

public class ConsultaTagActivity extends AppCompatActivity {

    private static final String TAG = "ConsultaTagActivity";

    private RFIDWithUHFUART mReader;
    private boolean isReading = false;

    private final Handler handler = new Handler();
    private final Handler readerHandler = new Handler();
    private Runnable leituraRunnable;

    private ToneGenerator toneGen;

    private List<String> tagsLidas = new ArrayList<>();
    private List<String> listaTags = new ArrayList<>();
    private SimpleTagAdapter adapter;

    private TextView tvTagCount;
    private ListView listViewTags;
    private LinearLayout btnLerTags, btnLimparTags, btnDistancia;
    private TextView txtBotao;
    private TextView txtInfoTopo, txtInfoUser;

    private RadioButton rbLoop, rbSingle;
    private boolean modoSingle = false; // false = AUTO, true = SINGLE

    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_tag);

        dbHelper = new DBHelper(this);

        // Dados passados da Activity anterior
        String codigoFilial = getIntent().getStringExtra("codigoFilial");
        String codigoLocal = getIntent().getStringExtra("codigoLocal");
        String chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        // UI
        tvTagCount = findViewById(R.id.tvTagCount);
        listViewTags = findViewById(R.id.listViewTags);
        btnLerTags = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnDistancia = findViewById(R.id.btnDistancia);
        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtInfoTopo = findViewById(R.id.txtInfoTopo);
        txtInfoUser = findViewById(R.id.txtInfoUser);

        rbLoop = findViewById(R.id.rbLoop);
        rbSingle = findViewById(R.id.rbSingle);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        DBHelper db = new DBHelper(this);
        adapter = new SimpleTagAdapter(this, listaTags, db);
        listViewTags.setAdapter(adapter);

        // Configura modos Auto / Single
        rbLoop.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = false;
        });

        rbSingle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = true;
        });

        // Botão limpar lista
        btnLimparTags.setOnClickListener(v -> limparTags());

        // Botão Distância (Curta / Média / Longa)
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());

        // Exibe informações carregadas
        txtInfoUser.setText(codigoFilial + "|" + codigoLocal + "|" + chapaFuncionario);

        Local localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        Usuario userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        if (localBanco != null && userBanco != null) {
            txtInfoTopo.setText(localBanco.getLocalNome() + "|" + userBanco.getNome());
        } else {
            txtInfoTopo.setText("Nenhum dado encontrado no banco para esses códigos.");
        }

        inicializarLeitor();

        btnLerTags.setOnClickListener(v -> {
            if (isReading) pararLeitura();
            else iniciarLeitura();
        });
    }

    private void inicializarLeitor() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && mReader.init(this)) {

                Toast.makeText(this, "Leitor UHF conectado!", Toast.LENGTH_SHORT).show();

                // ✅ LE POTÊNCIA ATUAL APÓS DELAY
                new Handler().postDelayed(() -> {
                    try {
                        int power = mReader.getPower();
                        Log.d("UHF", "Potência atual do leitor (dBm): " + power);
                        Toast.makeText(this, "Potência atual: " + power + " dBm", Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        Log.e("UHF", "Erro ao consultar potência: " + ex.getMessage());
                    }
                }, 400);

            } else {
                Toast.makeText(this, "Falha ao conectar ao leitor UHF.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar leitor", e);
            Toast.makeText(this, "Erro ao inicializar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ GATILHO FÍSICO DO LEITOR (TOGGLE)
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293;

        if (event.getKeyCode() == triggerKeyCode) {

            if (event.getAction() == KeyEvent.ACTION_DOWN) {

                if (modoSingle) {
                    if (!isReading) {
                        iniciarLeitura();
                        handler.postDelayed(this::pararLeitura, 250);
                    }
                } else {
                    // ✅ AUTO → TOGGLE
                    if (!isReading) iniciarLeitura();
                    else pararLeitura();
                }
            }

            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    // ✅ INICIAR INVENTÁRIO
    private void iniciarLeitura() {
        if (mReader == null) {
            Toast.makeText(this, "Leitor não inicializado!", Toast.LENGTH_SHORT).show();
            return;
        }

        isReading = true;
        txtBotao.setText("Parar Leitura");

        tagsLidas.clear();
        listaTags.clear();
        adapter.notifyDataSetChanged();
        tvTagCount.setText("Tags lidas: 0");

        readerHandler.post(() -> {
            try {
                mReader.startInventoryTag();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar inventário", e);
            }
        });

        leituraRunnable = new Runnable() {
            @Override
            public void run() {
                if (isReading && mReader != null) {

                    try {
                        UHFTAGInfo tagInfo = mReader.readTagFromBuffer();

                        if (tagInfo != null) {

                            String epc = tagInfo.getEPC();

                            if (!tagsLidas.contains(epc)) {

                                tagsLidas.add(epc);

                                String epcCurto = epc.length() > 5 ? epc.substring(0, 5) : epc;

                                listaTags.add(epcCurto);
                                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

                                runOnUiThread(() -> {
                                    adapter.notifyDataSetChanged();
                                    tvTagCount.setText("Tags lidas: " + listaTags.size());
                                });
                            }
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Erro leitura tag", e);
                    }

                    handler.postDelayed(this, 80);
                }
            }
        };

        handler.post(leituraRunnable);
    }

    // ✅ PARAR INVENTÁRIO
    private void pararLeitura() {
        isReading = false;
        txtBotao.setText("Ler Tags");

        handler.removeCallbacks(leituraRunnable);

        readerHandler.post(() -> {
            try {
                mReader.stopInventory();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar leitura", e);
            }
        });
    }

    // ✅ BOTÃO LIMPAR TAGS
    private void limparTags() {
        if (isReading) pararLeitura();

        tagsLidas.clear();
        listaTags.clear();
        adapter.notifyDataSetChanged();

        tvTagCount.setText("Tags lidas: 0");
        Toast.makeText(this, "Lista de tags limpa!", Toast.LENGTH_SHORT).show();
    }

    // ✅ BOTÃO DISTÂNCIA
    private void abrirSelecionadorDeDistancia() {

        String[] opcoes = {
                "Curta (10 dBm)",
                "Média (20 dBm)",
                "Longa (30 dBm)"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância de Leitura")
                .setItems(opcoes, (dialog, which) -> {

                    int potenciaSelecionada = 20;

                    switch (which) {
                        case 0: potenciaSelecionada = 10; break;
                        case 1: potenciaSelecionada = 20; break;
                        case 2: potenciaSelecionada = 30; break;
                    }

                    try {
                        boolean ok = mReader.setPower(potenciaSelecionada);

                        if (ok) {
                            Toast.makeText(this,
                                    "Potência ajustada para " + potenciaSelecionada + " dBm",
                                    Toast.LENGTH_SHORT).show();

                            Log.d("UHF", "Potência ajustada para: " + potenciaSelecionada + " dBm");

                        } else {
                            Toast.makeText(this,
                                    "Falha ao ajustar potência.",
                                    Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        Toast.makeText(this,
                                "Erro ao ajustar potência: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }

                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mReader != null) mReader.free();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao liberar leitor", e);
        }
    }
}