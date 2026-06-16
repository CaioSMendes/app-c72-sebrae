package com.example.uhf.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.ImageView;
import com.example.uhf.R;
import com.example.uhf.model.Local;
import java.util.List;

public class LocalAdapter extends BaseAdapter {
    private Context context;
    private List<Local> lista;

    public LocalAdapter(Context context, List<Local> lista) {
        this.context = context;
        this.lista = lista;
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
        return lista.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_local, parent, false);
        }

        Local local = lista.get(position);
        TextView txtNome = convertView.findViewById(R.id.txtLocalNome);
        TextView txtCodigoLocal = convertView.findViewById(R.id.txtCodigoLocal);
        ImageView img = convertView.findViewById(R.id.imgLocal);

        txtNome.setText(local.getLocalNome());
        txtCodigoLocal.setText(local.getCodigoLocal());
        img.setImageResource(R.drawable.ic_locais); // imagem fixa

        return convertView;
    }
}
