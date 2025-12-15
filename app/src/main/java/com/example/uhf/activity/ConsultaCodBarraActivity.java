package com.example.uhf.activity;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
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
import com.rscja.deviceapi.entity.BarcodeEntity;

import java.util.ArrayList;
import java.util.List;

public class ConsultaCodBarraActivity extends AppCompatActivity {

    private static final String TAG = "ConsultaCodBarraActivity";

    /** NOVO: driver 2D */
    private BarcodeDecoder barcodeDecoder;

    private boolean isReading = false;

    private ToneGenerator toneGen;

    private final List<String> codigosLidos = new ArrayList<>();
    private final List<String> listaCodigos = new ArrayList<>();

    private SimpleTagAdapter adapter;

    private TextView tvCount;
    private ListView listViewCodigos;

    private LinearLayout btnLer, btnLimpar, btnResumo, btnConcluir;
    private TextView txtTituloBotao, txtInfoTopo, txtInfoUser;

    private String codigoFilial, codigoLocal, chapaFuncionario;

    private Local localBanco;
    private Usuario userBanco;
    private DBHelper dbHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_codbarra);

        dbHelper = new DBHelper(this);

        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        tvCount = findViewById(R.id.tvCountCodBarra);
        listViewCodigos = findViewById(R.id.listViewCodBarra);

        btnLer = findViewById(R.id.btnLerCodBarra);
        btnLimpar = findViewById(R.id.btnLimparCodBarra);
        btnResumo = findViewById(R.id.btnResumoCodBarra);
        btnConcluir = findViewById(R.id.btnConcluirCodBarra);

        txtTituloBotao = findViewById(R.id.txtTituloBotaoCodBarra);
        txtInfoTopo = findViewById(R.id.txtInfoTopoCodBarra);
        txtInfoUser = findViewById(R.id.txtInfoUserCodBarra);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        //adapter = new SimpleTagAdapter(this, listaCodigos, dbHelper);
        adapter = new SimpleTagAdapter(this, listaCodigos, dbHelper);

        listViewCodigos.setAdapter(adapter);

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);

        txtInfoTopo.setText(
                (localBanco != null ? localBanco.getLocalNome() : "Local ?") +
                        " | " +
                        (userBanco != null ? userBanco.getNome() : "Usuário ?")
        );

        inicializarScanner2D();

        btnLer.setOnClickListener(v -> {
            if (isReading)
                pararLeitura();
            else
                iniciarLeitura();
        });

        btnLimpar.setOnClickListener(v -> limparLista());
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnConcluir.setOnClickListener(v -> gerarTXT());
    }


    /** NOVO — inicialização do leitor 2D */
    private void inicializarScanner2D() {

        barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();

        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... voids) {
                return barcodeDecoder.open(ConsultaCodBarraActivity.this);
            }

            @Override
            protected void onPostExecute(Boolean ok) {
                if (!ok) {
                    Toast.makeText(ConsultaCodBarraActivity.this,
                            "Falha ao abrir leitor 2D", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(ConsultaCodBarraActivity.this,
                        "Scanner 2D pronto!", Toast.LENGTH_SHORT).show();

                configurarCallback();
            }

        }.execute();
    }


    /** Callback de leitura 2D */
    private void configurarCallback() {
        barcodeDecoder.setDecodeCallback(barcodeEntity -> {

            if (barcodeEntity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS)
                return;

            String code = barcodeEntity.getBarcodeData();

            Log.d(TAG, "Código 2D lido: " + code);

            if (!codigosLidos.contains(code)) {
                codigosLidos.add(code);
                listaCodigos.add(code);

                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 80);

                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    tvCount.setText("Códigos: " + listaCodigos.size());
                });
            }
        });
    }


    /** Iniciar leitura 2D */
    private void iniciarLeitura() {
        isReading = true;
        txtTituloBotao.setText("Parar Leitura");

        barcodeDecoder.startScan();
    }


    /** Parar leitura 2D */
    private void pararLeitura() {
        isReading = false;
        txtTituloBotao.setText("Ler Código");

        barcodeDecoder.stopScan();
    }


    /** Botão físico */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getKeyCode() == 293 && event.getAction() == KeyEvent.ACTION_DOWN) {

            if (!isReading)
                iniciarLeitura();
            else
                pararLeitura();

            return true;
        }

        return super.dispatchKeyEvent(event);
    }


    private void limparLista() {
        codigosLidos.clear();
        listaCodigos.clear();
        adapter.notifyDataSetChanged();
        tvCount.setText("Códigos: 0");
    }


    private void abrirResumo() {
        if (listaCodigos.isEmpty()) {
            Toast.makeText(this, "Nenhum código lido!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ResumoActivity.class);
        intent.putStringArrayListExtra("tags", new ArrayList<>(listaCodigos));
        startActivity(intent);
    }


    private void gerarTXT() {
        Toast.makeText(this, "TODO: Gerar TXT", Toast.LENGTH_SHORT).show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        pararLeitura();

        try {
            barcodeDecoder.close();
        } catch (Exception ignored) {}
    }
}