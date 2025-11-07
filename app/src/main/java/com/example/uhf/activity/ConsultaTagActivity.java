package com.example.uhf.activity;

import android.content.Intent;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.net.Uri;
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

import java.io.File;
import java.io.FileOutputStream;
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
    private LinearLayout btnLerTags, btnLimparTags, btnDistancia, btnResumo, btnConcluir;
    private TextView txtBotao;
    private TextView txtInfoTopo, txtInfoUser;

    private RadioButton rbLoop, rbSingle;
    private boolean modoSingle = false;

    private DBHelper dbHelper;

    // ✅ Salvamos aqui para enviar ao resumo
    private String codigoFilial;
    private String codigoLocal;
    private String chapaFuncionario;
    private Local localBanco;
    private Usuario userBanco;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_tag);

        dbHelper = new DBHelper(this);

        // ✅ Dados da Intent
        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        // ✅ Carregar dados do banco
        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        // ✅ Referências UI
        tvTagCount = findViewById(R.id.tvTagCount);
        listViewTags = findViewById(R.id.listViewTags);
        btnLerTags = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnDistancia = findViewById(R.id.btnDistancia);
        btnResumo = findViewById(R.id.btnResumo);
        btnConcluir = findViewById(R.id.btnConcluir);

        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtInfoTopo = findViewById(R.id.txtInfoTopo);
        txtInfoUser = findViewById(R.id.txtInfoUser);

        rbLoop = findViewById(R.id.rbLoop);
        rbSingle = findViewById(R.id.rbSingle);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        DBHelper db = new DBHelper(this);
        adapter = new SimpleTagAdapter(this, listaTags, db);
        listViewTags.setAdapter(adapter);

        // ✅ Configura modos
        rbLoop.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = false;
        });

        rbSingle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = true;
        });

        // ✅ Botões
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnConcluir.setOnClickListener(v -> gerarArquivoTXT());
        btnLimparTags.setOnClickListener(v -> limparTags());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);

        if (localBanco != null && userBanco != null)
            txtInfoTopo.setText(localBanco.getLocalNome() + " | " + userBanco.getNome());
        else
            txtInfoTopo.setText("Dados não encontrados.");

        inicializarLeitor();

        btnLerTags.setOnClickListener(v -> {
            if (isReading) pararLeitura();
            else iniciarLeitura();
        });
    }


    // ✅ Inicializar leitor
    private void inicializarLeitor() {
        try {
            mReader = RFIDWithUHFUART.getInstance();

            if (mReader != null && mReader.init(this)) {

                Toast.makeText(this, "Leitor conectado!", Toast.LENGTH_SHORT).show();

                new Handler().postDelayed(() -> {
                    try {
                        int power = mReader.getPower();
                        Log.d("UHF", "Potência atual: " + power);
                    } catch (Exception ex) {
                        Log.e("UHF", "Erro potência: " + ex.getMessage());
                    }
                }, 400);

            } else {
                Toast.makeText(this, "Falha ao conectar leitor!", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro inicializando leitor", e);
        }
    }


    // ✅ Gatilho físico (toggle)
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
                    if (!isReading) iniciarLeitura();
                    else pararLeitura();
                }
            }

            return true;
        }

        return super.dispatchKeyEvent(event);
    }


    // ✅ Iniciar leitura
    private void iniciarLeitura() {

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

                if (!isReading) return;

                try {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();

                    if (tagInfo != null) {

                        String epc = tagInfo.getEPC();

                        if (!tagsLidas.contains(epc)) {

                            tagsLidas.add(epc);

                            // ✅ SOMENTE os 5 primeiros dígitos
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
        };

        handler.post(leituraRunnable);
    }


    // ✅ Parar leitura
    private void pararLeitura() {
        isReading = false;
        txtBotao.setText("Ler Tags");

        handler.removeCallbacks(leituraRunnable);

        readerHandler.post(() -> {
            try {
                mReader.stopInventory();
            } catch (Exception e) {
                Log.e(TAG, "Erro parar leitura", e);
            }
        });
    }


    // ✅ Limpar tags
    private void limparTags() {
        if (isReading) pararLeitura();

        tagsLidas.clear();
        listaTags.clear();
        adapter.notifyDataSetChanged();
        tvTagCount.setText("Tags lidas: 0");

        Toast.makeText(this, "Lista limpa!", Toast.LENGTH_SHORT).show();
    }


    // ✅ Ajustar distância
    private void abrirSelecionadorDeDistancia() {

        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {

                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;

                    try {
                        if (mReader.setPower(power)) {
                            Toast.makeText(this, "Potência ajustada: " + power, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }


    private void finalizarInventario() {
        Toast.makeText(this, "Inventário concluído!", Toast.LENGTH_SHORT).show();
    }


    // ✅ Enviar dados para o ResumoActivity
    private void abrirResumo() {

        if (listaTags.isEmpty()) {
            Toast.makeText(this, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ResumoActivity.class);

        intent.putStringArrayListExtra("tags", new ArrayList<>(listaTags));

        intent.putExtra("codigoFilial", codigoFilial);
        intent.putExtra("codigoLocal", codigoLocal);
        intent.putExtra("chapaFuncionario", chapaFuncionario);
        intent.putExtra("nomeUsuario", userBanco != null ? userBanco.getNome() : "");
        intent.putExtra("nomeLocal", localBanco != null ? localBanco.getLocalNome() : "");

        startActivity(intent);
    }

    // ✅ GERA O TXT EXATAMENTE NO PADRÃO SEBRAE
    private void gerarArquivoTXT() {

        if (listaTags.isEmpty()) {
            Toast.makeText(this, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {

            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            File arquivo = new File(pasta, "inventario_final.txt");

            FileOutputStream fos = new FileOutputStream(arquivo);

            for (String tag5 : listaTags) {

                String codigoBarra = "040" + tag5;

                String filialFmt = String.format("%03d",
                        Integer.parseInt(codigoFilial));

                String localFmt = String.format("%04d",
                        Integer.parseInt(codigoLocal));

                String matriculaFmt = String.format("%08d",
                        Integer.parseInt(chapaFuncionario));

                String linha = filialFmt + " "
                        + localFmt + "  "
                        + matriculaFmt
                        + "                      "
                        + codigoBarra
                        + "\n";

                fos.write(linha.getBytes());
            }

            fos.close();

            Toast.makeText(this,
                    "TXT gerado:\n" + arquivo.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");

            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", arquivo
            );

            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(share, "Enviar TXT"));

        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mReader != null) mReader.free();
        } catch (Exception ignored) {}
    }
}
