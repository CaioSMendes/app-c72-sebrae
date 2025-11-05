package com.example.uhf.activity;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class ConsultaTagActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_tag);

        String codigoFilial = getIntent().getStringExtra("codigoFilial");
        String codigoLocal = getIntent().getStringExtra("codigoLocal");
        String chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        TextView txtInfoTopo = findViewById(R.id.txtInfoTopo);
        txtInfoTopo.setText("Filial: " + codigoFilial + " | Local: " + codigoLocal + " | Chapa: " + chapaFuncionario);
    }
}