package com.example.uhf.activity;

import com.example.uhf.R;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.SearchView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import com.example.uhf.model.Usuario;

public class ListaUsuariosActivity extends AppCompatActivity {

    private ListView listViewUsuarios;
    private SearchView searchViewUsuario;
    private UsuarioAdapter adapter;
    private List<Usuario> listaUsuarios;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_usuarios);

        listViewUsuarios = findViewById(R.id.listViewUsuarios);
        searchViewUsuario = findViewById(R.id.searchViewUsuario);
        dbHelper = new DBHelper(this);

        carregarUsuarios();
        configurarBusca();
        configurarClique();
    }

    private void carregarUsuarios() {
        listaUsuarios = dbHelper.listarUsuarios();
        adapter = new UsuarioAdapter(this, listaUsuarios);
        listViewUsuarios.setAdapter(adapter);
    }

    private void configurarBusca() {
        searchViewUsuario.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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
        List<Usuario> filtrados = new ArrayList<>();
        for (Usuario u : listaUsuarios) {
            if (u.getNome().toLowerCase().contains(texto.toLowerCase())) {
                filtrados.add(u);
            }
        }
        adapter = new UsuarioAdapter(this, filtrados);
        listViewUsuarios.setAdapter(adapter);
    }

    private void configurarClique() {
        listViewUsuarios.setOnItemClickListener((parent, view, position, id) -> {
            Usuario usuarioSelecionado = (Usuario) parent.getItemAtPosition(position);
            Intent intent = new Intent(this, DetalheUsuarioActivity.class);
            intent.putExtra("nome", usuarioSelecionado.getNome());
            intent.putExtra("matricula", usuarioSelecionado.getMatricula());
            startActivity(intent);
        });
    }
}
