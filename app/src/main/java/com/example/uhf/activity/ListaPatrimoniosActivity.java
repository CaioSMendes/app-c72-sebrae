package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.example.uhf.R;
import com.example.uhf.model.Patrimonio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListaPatrimoniosActivity extends AppCompatActivity {

    private ListView   listView;
    private SearchView searchView;
    private TextView   txtContador;

    private PatrimonioAdapter adapter;
    private DBHelper          db;

    private List<Patrimonio> listaCompleta = new ArrayList<>();
    private List<Patrimonio> listaFiltrada = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_patrimonios);

        db          = DBHelper.getInstance(this);
        listView    = findViewById(R.id.listViewPatrimonios);
        searchView  = findViewById(R.id.searchViewPatrimonios);
        txtContador = findViewById(R.id.txtContador);

        carregarPatrimonios();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filtrarLista(query.trim());
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filtrarLista(newText.trim());
                return false;
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Patrimonio p = listaFiltrada.get(position);
            Intent intent = new Intent(ListaPatrimoniosActivity.this, DetalhePatrimonioActivity.class);
            intent.putExtra("id", p.getId());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarPatrimonios();
    }

    // =========================================================================
    // Carrega e ordena alfabeticamente pela descrição
    // =========================================================================

    private void carregarPatrimonios() {
        listaCompleta = db.listarPatrimonios();

        // Ordem alfabética pela descrição
        Collections.sort(listaCompleta,
                (a, b) -> a.getDescricao().compareToIgnoreCase(b.getDescricao()));

        // Reaplica filtro atual
        String query = searchView != null
                ? searchView.getQuery().toString().trim()
                : "";
        filtrarLista(query);
    }

    // =========================================================================
    // Filtro — patrimônio OU código de barras + contador
    // =========================================================================

    private void filtrarLista(String query) {
        listaFiltrada.clear();

        if (query.isEmpty()) {
            listaFiltrada.addAll(listaCompleta);
        } else {
            String q = query.toLowerCase();
            for (Patrimonio p : listaCompleta) {
                if (p.getPatrimonio().toLowerCase().contains(q)  ||
                        p.getDescricao().toLowerCase().contains(q)   ||
                        p.getCodigoBarra().toLowerCase().contains(q)) {
                    listaFiltrada.add(p);
                }
            }
        }

        // Contador
        if (txtContador != null) {
            int total    = listaCompleta.size();
            int exibindo = listaFiltrada.size();
            txtContador.setText(query.isEmpty()
                    ? total + " patrimônio(s)"
                    : exibindo + " de " + total + " resultado(s)");
        }

        adapter = new PatrimonioAdapter(this, listaFiltrada);
        listView.setAdapter(adapter);
    }
}