package com.example.uhf.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.BaseAdapter;
import com.example.uhf.R;
import java.util.List;
import com.example.uhf.model.Usuario;

public class UsuarioAdapter extends BaseAdapter {

    private Context context;
    private List<Usuario> lista;
    private LayoutInflater inflater;

    public UsuarioAdapter(Context context, List<Usuario> lista) {
        this.context = context;
        this.lista = lista;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return lista.size();
    }

    @Override
    public Object getItem(int position) {
        return lista.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_usuario, parent, false);
        }

        ImageView imgUsuario = convertView.findViewById(R.id.imgUsuario);
        TextView txtNome = convertView.findViewById(R.id.txtNome);
        TextView txtMatricula = convertView.findViewById(R.id.txtMatricula);

        Usuario usuario = lista.get(position);

        txtNome.setText(usuario.getNome());
        txtMatricula.setText(usuario.getMatricula());

        if (usuario.getImagemResId() != 0) {
            imgUsuario.setImageResource(usuario.getImagemResId());
        } else {
            imgUsuario.setImageResource(R.drawable.ic_user);
        }

        return convertView;
    }

    public void updateList(List<Usuario> novaLista) {
        this.lista = novaLista;
        notifyDataSetChanged();
    }
}