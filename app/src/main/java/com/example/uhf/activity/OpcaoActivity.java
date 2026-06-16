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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opcao);

        btnLeituraRFID = findViewById(R.id.btnLeituraRFID);
        btnLeituraCodBarra = findViewById(R.id.btnLeituraCodBarra);

        // RFID → InventarioActivity
        btnLeituraRFID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(OpcaoActivity.this, InventarioActivity.class);
                intent.putExtra("tipoLeitura", "RFID");
                startActivity(intent);
            }
        });

        // Código de Barras → InventarioCodBarraActivity
        btnLeituraCodBarra.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(OpcaoActivity.this, InventarioCodBarraActivity.class);
                intent.putExtra("tipoLeitura", "CODBARRA");
                startActivity(intent);
            }
        });
    }
}
