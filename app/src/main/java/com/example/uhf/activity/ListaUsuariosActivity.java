package com.example.uhf.activity;

import com.example.uhf.R;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.uhf.model.Usuario;

public class ListaUsuariosActivity extends AppCompatActivity {

    private ListView   listViewUsuarios;
    private SearchView searchViewUsuario;
    private TextView   txtContador;

    private UsuarioAdapter adapter;
    private List<Usuario>  listaCompleta = new ArrayList<>();
    private DBHelper       dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_usuarios);

        listViewUsuarios  = findViewById(R.id.listViewUsuarios);
        searchViewUsuario = findViewById(R.id.searchViewUsuario);
        txtContador       = findViewById(R.id.txtContador);

        dbHelper = DBHelper.getInstance(this);

        carregarUsuarios();
        configurarBusca();
        configurarClique();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarUsuarios();
    }

    // =========================================================================
    // Carrega e ordena alfabeticamente
    // =========================================================================

    private void carregarUsuarios() {
        listaCompleta = dbHelper.listarUsuarios();

        // Ordem alfabética pelo nome
        Collections.sort(listaCompleta,
                (a, b) -> a.getNome().compareToIgnoreCase(b.getNome()));

        // Reaplica filtro atual se já tiver algo digitado
        String query = searchViewUsuario != null
                ? searchViewUsuario.getQuery().toString().trim()
                : "";
        filtrar(query);
    }

    // =========================================================================
    // Busca
    // =========================================================================

    private void configurarBusca() {
        searchViewUsuario.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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
    // Filtro — nome OU matrícula + contador
    // =========================================================================

    private void filtrar(String texto) {
        List<Usuario> filtrados = new ArrayList<>();

        for (Usuario u : listaCompleta) {
            if (texto.isEmpty()
                    || u.getNome().toLowerCase().contains(texto.toLowerCase())
                    || u.getMatricula().contains(texto)) {
                filtrados.add(u);
            }
        }

        // Atualiza contador
        if (txtContador != null) {
            int total    = listaCompleta.size();
            int exibindo = filtrados.size();
            txtContador.setText(texto.isEmpty()
                    ? total + " responsável(is)"
                    : exibindo + " de " + total + " resultado(s)");
        }

        adapter = new UsuarioAdapter(this, filtrados);
        listViewUsuarios.setAdapter(adapter);
        configurarClique();
    }

    // =========================================================================
    // Clique — navega pro DetalheUsuarioActivity
    // =========================================================================

    private void configurarClique() {
        listViewUsuarios.setOnItemClickListener((parent, view, position, id) -> {
            Usuario usuarioSelecionado = (Usuario) parent.getItemAtPosition(position);
            Intent intent = new Intent(this, DetalheUsuarioActivity.class);
            intent.putExtra("nome",      usuarioSelecionado.getNome());
            intent.putExtra("matricula", usuarioSelecionado.getMatricula());
            startActivity(intent);
        });
    }
}