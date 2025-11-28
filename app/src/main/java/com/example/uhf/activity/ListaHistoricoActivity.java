package com.example.uhf.activity;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import java.util.ArrayList;

public class ListaHistoricoActivity extends AppCompatActivity {

    ListView list;
    DBHelper db;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_historico);

        list = findViewById(R.id.listHistoricoLocais);
        db = new DBHelper(this);

        // ✔ Agora pega o código do local correto
        String localCodigo = getIntent().getStringExtra("localCodigo");

        carregar(localCodigo);
    }

    private void carregar(String localCodigo) {
        Cursor c = db.listarHistoricoPorSessao(localCodigo);

        ArrayList<String> lista = new ArrayList<>();

        while (c.moveToNext()) {

            String tag = c.getString(c.getColumnIndexOrThrow("tag"));
            String tipo = c.getString(c.getColumnIndexOrThrow("tipo"));
            String data = c.getString(c.getColumnIndexOrThrow("dataHora"));

            lista.add(tipo + " | " + tag + " | " + data);
        }

        c.close();

        ArrayAdapter<String> ad = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                lista
        );

        list.setAdapter(ad);
    }
}