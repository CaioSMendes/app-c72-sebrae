package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.example.uhf.R;

import java.util.ArrayList;
import java.util.List;

public class ListaPatrimoniosActivity extends AppCompatActivity {

    private ListView listView;
    private SearchView searchView;
    private PatrimonioAdapter adapter;

    private DBHelper db;
    private List<com.example.uhf.model.Patrimonio> listaPatrimonios;
    private List<com.example.uhf.model.Patrimonio> listaFiltrada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_patrimonios);

        db = new DBHelper(this);
        listView = findViewById(R.id.listViewPatrimonios);
        searchView = findViewById(R.id.searchViewPatrimonios);

        carregarPatrimonios();

        // Filtrar pela SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filtrarLista(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filtrarLista(newText);
                return false;
            }
        });

        // Clique em item para abrir detalhes
        listView.setOnItemClickListener((parent, view, position, id) -> {
            com.example.uhf.model.Patrimonio p = listaFiltrada.get(position);
            Intent intent = new Intent(ListaPatrimoniosActivity.this, DetalhePatrimonioActivity.class);
            intent.putExtra("id", p.getId());
            startActivity(intent);
        });
    }

    private void carregarPatrimonios() {
        listaPatrimonios = db.listarPatrimonios();
        listaFiltrada = new ArrayList<>(listaPatrimonios);
        adapter = new PatrimonioAdapter(this, listaFiltrada);
        listView.setAdapter(adapter);
    }

    private void filtrarLista(String query) {
        listaFiltrada.clear();
        if (query.isEmpty()) {
            listaFiltrada.addAll(listaPatrimonios);
        } else {
            query = query.toLowerCase();
            for (com.example.uhf.model.Patrimonio p : listaPatrimonios) {
                if (p.getPatrimonio().toLowerCase().contains(query) ||
                        p.getCodigoBarra().toLowerCase().contains(query)) {
                    listaFiltrada.add(p);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
}