package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.uhf.R;

import java.util.ArrayList;

public class HistoricoDetalheAdapter extends BaseAdapter {

    Context context;

    ArrayList<String> locais;       // ← Nome do local
    ArrayList<String> matriculas;   // ← Matrícula do usuário
    ArrayList<String> tags;         // ← TAG RFID

    public HistoricoDetalheAdapter(Context ctx,
                                   ArrayList<String> locais,
                                   ArrayList<String> matriculas,
                                   ArrayList<String> tags) {

        this.context = ctx;
        this.locais = locais;
        this.matriculas = matriculas;
        this.tags = tags;
    }

    @Override
    public int getCount() {
        return tags.size();
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

        if (convertView == null)
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_historico_detalhe, parent, false);

        // Campos do XML
        ImageView imgIcon = convertView.findViewById(R.id.imgIcon);
        TextView txtLocal = convertView.findViewById(R.id.txtLocal);
        TextView txtMatricula = convertView.findViewById(R.id.txtMatricula);
        TextView txtTag = convertView.findViewById(R.id.txtTag);

        // Setar valores
        txtTag.setText("TAG: " + tags.get(pos));
        txtMatricula.setText("Matrícula: " + matriculas.get(pos));
        txtLocal.setText("Local: " + locais.get(pos));

        // Se quiser mudar ícone dependendo do tipo, posso adicionar depois
        imgIcon.setImageResource(R.drawable.carrinho);

        return convertView;
    }
}