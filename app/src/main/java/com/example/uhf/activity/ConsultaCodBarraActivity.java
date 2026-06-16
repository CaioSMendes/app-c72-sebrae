package com.example.uhf.activity;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
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

import com.rscja.deviceapi.Barcode1D;
import com.rscja.deviceapi.exception.ConfigurationException;

import java.util.ArrayList;
import java.util.List;

public class ConsultaCodBarraActivity extends AppCompatActivity {

    private static final String TAG = "ConsultaCodBarraActivity";

    private Barcode1D barcodeReader;
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

    private Thread scanThread;


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

        adapter = new SimpleTagAdapter(this, listaCodigos, dbHelper);
        listViewCodigos.setAdapter(adapter);

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);

        txtInfoTopo.setText(
                (localBanco != null ? localBanco.getLocalNome() : "Local ?") +
                        " | " +
                        (userBanco != null ? userBanco.getNome() : "Usuário ?")
        );

        inicializarScanner();

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


    /** ✅ Inicialização do GM65 usando apenas scan() */
    private void inicializarScanner() {
        try {
            barcodeReader = Barcode1D.getInstance();

            if (!barcodeReader.open(this)) {
                Toast.makeText(this, "Falha ao abrir scanner 1D", Toast.LENGTH_SHORT).show();
                return;
            }

            barcodeReader.setTimeOut(800); // timeout baixo só para acordar

            Log.d(TAG, "Acordando GM65...");

            // ✅ Manda "wake scans" — isso ativa o laser internamente
            for (int i = 0; i < 3; i++) {
                barcodeReader.scan();
                Thread.sleep(50);
            }

            barcodeReader.setTimeOut(3000);

            Toast.makeText(this, "Scanner 1D pronto!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar GM65", e);
        }
    }


    /** ✅ Iniciar leitura */
    private void iniciarLeitura() {
        isReading = true;
        txtTituloBotao.setText("Parar Leitura");

        scanThread = new Thread(() -> {

            while (isReading) {

                String code = barcodeReader.scan(); // único método real da API

                if (code == null || code.trim().isEmpty())
                    continue;

                Log.d(TAG, "Código lido: " + code);

                if (!codigosLidos.contains(code)) {

                    codigosLidos.add(code);
                    listaCodigos.add(code);

                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        tvCount.setText("Códigos: " + listaCodigos.size());
                    });
                }
            }

        });

        scanThread.start();
    }


    /** ✅ Parar leitura */
    private void pararLeitura() {
        isReading = false;
        txtTituloBotao.setText("Ler Código");
    }


    /** ✅ Gatilho físico */
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
            barcodeReader.close();
        } catch (Exception ignored) {}
    }
}
