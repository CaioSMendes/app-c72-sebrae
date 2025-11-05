package com.example.uhf.activity;

import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.adapter.SimpleTagAdapter;
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
    private LinearLayout btnLerTags;
    private TextView txtBotao; // Texto interno do botão

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_tag);

        // Inicializa componentes
        tvTagCount = findViewById(R.id.tvTagCount);
        listViewTags = findViewById(R.id.listViewTags);
        btnLerTags = findViewById(R.id.btnLerTags);
        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        adapter = new SimpleTagAdapter(this, listaTags);
        listViewTags.setAdapter(adapter);

        // Inicializa leitor
        inicializarLeitor();

        // Clique do botão principal
        btnLerTags.setOnClickListener(v -> {
            if (isReading) {
                pararLeitura();
            } else {
                iniciarLeitura();
            }
        });
    }

    private void inicializarLeitor() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && mReader.init(this)) {
                Toast.makeText(this, "Leitor UHF conectado!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Falha ao conectar ao leitor UHF.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar leitor", e);
            Toast.makeText(this, "Erro ao inicializar leitor: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

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
                boolean ok = mReader.startInventoryTag();
                if (!ok) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Falha ao iniciar leitura.", Toast.LENGTH_SHORT).show();
                        isReading = false;
                        txtBotao.setText("Ler Tags");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar inventário", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Erro ao iniciar leitura: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    isReading = false;
                    txtBotao.setText("Ler Tags");
                });
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
                                listaTags.add(epc);
                                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

                                runOnUiThread(() -> {
                                    adapter.notifyDataSetChanged();
                                    tvTagCount.setText("Tags lidas: " + listaTags.size());
                                });
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Erro na leitura de tag", e);
                    }

                    handler.postDelayed(this, 100);
                }
            }
        };

        handler.post(leituraRunnable);
    }

    private void pararLeitura() {
        isReading = false;
        txtBotao.setText("Ler Tags");

        handler.removeCallbacks(leituraRunnable);

        readerHandler.post(() -> {
            try {
                if (mReader != null) {
                    boolean stopped = mReader.stopInventory();
                    if (!stopped) {
                        Log.e(TAG, "Falha ao parar inventário");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar leitura", e);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mReader != null) {
                mReader.free();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao liberar leitor", e);
        }
    }
}