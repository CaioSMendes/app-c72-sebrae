package com.example.uhf.activity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import java.util.ArrayList;

public class HistoricoActivity extends AppCompatActivity {

    ListView listView;
    DBHelper db;

    ArrayList<String> locais = new ArrayList<>();
    ArrayList<String> datas = new ArrayList<>();
    ArrayList<String> codigos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico);

        listView = findViewById(R.id.listHistoricoLocais);
        db = new DBHelper(this);

        carregarLista();

        // Clique no item → abre a lista de leituras do local
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent i = new Intent(this, ListaHistoricoActivity.class);
            i.putExtra("localCodigo", codigos.get(position));
            startActivity(i);
        });
    }

    private void carregarLista() {
        Cursor c = db.listarHistoricoAgrupado();

        locais.clear();
        datas.clear();
        codigos.clear();

        while (c.moveToNext()) {
            codigos.add(c.getString(0)); // localCodigo
            locais.add(c.getString(1));  // localNome
            datas.add(c.getString(3));   // ultimaData
        }

        c.close();

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return locais.size();
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {

                View v = convertView;

                if (v == null)
                    v = getLayoutInflater().inflate(R.layout.item_historico_local, parent, false);

                TextView txtLocalNome = v.findViewById(R.id.txtLocalNome);
                TextView txtCodLocal = v.findViewById(R.id.txtCodLocal);
                TextView txtUltimaData = v.findViewById(R.id.txtUltimaData);

                txtLocalNome.setText(locais.get(pos));
                txtCodLocal.setText("Código: " + codigos.get(pos));
                txtUltimaData.setText("Última leitura: " + datas.get(pos));

                return v;
            }
        });
    }
}