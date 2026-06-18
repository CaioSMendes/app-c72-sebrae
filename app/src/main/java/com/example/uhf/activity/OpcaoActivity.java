package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class OpcaoActivity extends AppCompatActivity {

    private LinearLayout btnLeituraRFID;
    private LinearLayout btnLeituraCodBarra;
    private LinearLayout painelOpcoes;
    private LinearLayout optLeituraLivre;
    private LinearLayout optLeituraPorLocal;
    private LinearLayout optLeituraPorCategoria;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opcao);

        btnLeituraRFID         = findViewById(R.id.btnLeituraRFID);
        btnLeituraCodBarra     = findViewById(R.id.btnLeituraCodBarra);
        painelOpcoes           = findViewById(R.id.painelOpcoes);
        optLeituraLivre        = findViewById(R.id.optLeituraLivre);
        optLeituraPorLocal     = findViewById(R.id.optLeituraPorLocal);
        optLeituraPorCategoria = findViewById(R.id.optLeituraPorCategoria);

        // Clique no botão RFID → mostra/oculta o painel de opções
        btnLeituraRFID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (painelOpcoes.getVisibility() == View.VISIBLE) {
                    painelOpcoes.setVisibility(View.GONE);
                } else {
                    painelOpcoes.setVisibility(View.VISIBLE);
                }
            }
        });

        // Código de Barras (comportamento original mantido)
        btnLeituraCodBarra.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(OpcaoActivity.this, InventarioCodBarraActivity.class);
                intent.putExtra("tipoLeitura", "CODBARRA");
                startActivity(intent);
            }
        });

        // ── Opções do painel ──────────────────────────────────────────────

        optLeituraLivre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirInventario("LIVRE");
            }
        });

        optLeituraPorLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirInventario("LOCAL");
            }
        });

        optLeituraPorCategoria.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirInventario("CATEGORIA");
            }
        });
    }

    /**
     * Navega para InventarioActivity com o tipo de inventário selecionado.
     *
     * @param tipoInventario "LIVRE" | "LOCAL" | "CATEGORIA"
     */
    private void abrirInventario(String tipoInventario) {
        painelOpcoes.setVisibility(View.GONE);
        Intent intent = new Intent(OpcaoActivity.this, InventarioActivity.class);
        intent.putExtra("tipoLeitura",    "RFID");
        intent.putExtra("tipoInventario", tipoInventario);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // Se o painel estiver aberto, fecha antes de voltar
        if (painelOpcoes.getVisibility() == View.VISIBLE) {
            painelOpcoes.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }
}