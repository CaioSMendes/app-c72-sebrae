package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.example.uhf.R;
import com.example.uhf.model.Local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListaLocaisActivity extends AppCompatActivity {

    private ListView   listViewLocais;
    private SearchView searchViewLocal;
    private TextView   txtContador;

    private LocalAdapter adapter;
    private List<Local>  listaCompleta = new ArrayList<>();
    private DBHelper     dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_locais);

        listViewLocais  = findViewById(R.id.listViewLocais);
        searchViewLocal = findViewById(R.id.searchViewLocais);
        txtContador     = findViewById(R.id.txtContador);

        dbHelper = DBHelper.getInstance(this);

        carregarLocais();
        configurarBusca();
        configurarClique();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLocais();
    }

    // =========================================================================
    // Carrega e ordena alfabeticamente pelo nome
    // =========================================================================

    private void carregarLocais() {
        listaCompleta = dbHelper.listarLocais();

        Collections.sort(listaCompleta,
                (a, b) -> a.getLocalNome().compareToIgnoreCase(b.getLocalNome()));

        String query = searchViewLocal != null
                ? searchViewLocal.getQuery().toString().trim()
                : "";
        filtrar(query);
    }

    // =========================================================================
    // Busca
    // =========================================================================

    private void configurarBusca() {
        searchViewLocal.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filtrar(query.trim());
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filtrar(newText.trim());
                return true;
            }
        });
    }

    // =========================================================================
    // Filtro — nome OU código local + contador
    // =========================================================================

    private void filtrar(String texto) {
        List<Local> filtrados = new ArrayList<>();

        for (Local l : listaCompleta) {
            if (texto.isEmpty()
                    || l.getLocalNome().toLowerCase().contains(texto.toLowerCase())
                    || l.getCodigoLocal().toLowerCase().contains(texto.toLowerCase())) {
                filtrados.add(l);
            }
        }

        if (txtContador != null) {
            int total    = listaCompleta.size();
            int exibindo = filtrados.size();
            txtContador.setText(texto.isEmpty()
                    ? total + " local(is)"
                    : exibindo + " de " + total + " resultado(s)");
        }

        adapter = new LocalAdapter(this, filtrados);
        listViewLocais.setAdapter(adapter);
        configurarClique();
    }

    // =========================================================================
    // Clique — navega pro DetalheLocalActivity
    // =========================================================================

    private void configurarClique() {
        listViewLocais.setOnItemClickListener((parent, view, position, id) -> {
            Local selecionado = (Local) adapter.getItem(position);
            if (selecionado != null) {
                Intent intent = new Intent(this, DetalheLocalActivity.class);
                intent.putExtra("localId", selecionado.getId());
                startActivity(intent);
            }
        });
    }
}