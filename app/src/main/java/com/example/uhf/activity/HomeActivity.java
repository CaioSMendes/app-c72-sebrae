package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LinearLayout btnInventario = findViewById(R.id.btnInventario);
        LinearLayout btnPatrimonio = findViewById(R.id.btnPatrimonio);
        LinearLayout btnUsuario = findViewById(R.id.btnUsuario);
        LinearLayout btnLocal = findViewById(R.id.btnLocal);


        btnInventario.setOnClickListener(v -> {
            //Intent intent = new Intent(this, InventarioActivity.class);
            Intent intent = new Intent(this, OpcaoActivity.class);
            startActivity(intent);
        });

        btnPatrimonio.setOnClickListener(v -> {
            Intent intent = new Intent(this, PatrimonioActivity.class);
            //intent.putExtra("acao", "retirada");
            startActivity(intent);
        });

        btnUsuario.setOnClickListener(v -> {
            Intent intent = new Intent(this, UsuarioActivity.class);
            //intent.putExtra("acao", "entrega");
            startActivity(intent);
        });

        btnLocal.setOnClickListener(v -> {
            Intent intent = new Intent(this, LocalActivity.class);
            //intent.putExtra("acao", "ativos");
            startActivity(intent);
        });


    }
}
