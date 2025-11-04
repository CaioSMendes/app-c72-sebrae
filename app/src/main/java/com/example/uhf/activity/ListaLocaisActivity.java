package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import android.widget.ListView;

import com.example.uhf.R;
import com.example.uhf.model.Local;

import java.util.ArrayList;
import java.util.List;

public class ListaLocaisActivity extends AppCompatActivity {

    private ListView listViewLocais;
    private androidx.appcompat.widget.SearchView searchViewLocal;
    private LocalAdapter adapter;
    private List<Local> listaLocais;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_locais);

        listViewLocais = findViewById(R.id.listViewLocais);
        searchViewLocal = findViewById(R.id.searchViewLocais);
        dbHelper = new DBHelper(this);

        carregarLocais();
        configurarBusca();
        configurarClique();
    }

    private void carregarLocais() {
        listaLocais = dbHelper.listarLocais();
        adapter = new LocalAdapter(this, listaLocais);
        listViewLocais.setAdapter(adapter);
    }

    private void configurarBusca() {
        searchViewLocal.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filtrar(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filtrar(newText);
                return true;
            }
        });
    }

    private void filtrar(String texto) {
        List<Local> filtrados = new ArrayList<>();
        for (Local l : listaLocais) {
            if (l.getLocalNome().toLowerCase().contains(texto.toLowerCase()) ||
                    l.getCodigoLocal().toLowerCase().contains(texto.toLowerCase())) {
                filtrados.add(l);
            }
        }
        adapter = new LocalAdapter(this, filtrados);
        listViewLocais.setAdapter(adapter);
    }

    private void configurarClique() {
        listViewLocais.setOnItemClickListener((parent, view, position, id) -> {
            Local selecionado = (Local) adapter.getItem(position); // cast necessário
            if (selecionado != null) {
                Intent intent = new Intent(this, DetalheLocalActivity.class);
                intent.putExtra("localId", selecionado.getId()); // ⚠️ passar o ID
                startActivity(intent);
            }
        });
    }
}