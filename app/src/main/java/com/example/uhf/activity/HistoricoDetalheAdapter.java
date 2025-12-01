package com.example.uhf.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;

import java.util.ArrayList;
import java.util.HashSet;

public class HistoricoDetalheAdapter extends BaseAdapter {

    Context ctx;
    ArrayList<String> locais;
    ArrayList<String> matriculas;
    ArrayList<String> tags;

    // Guarda posições selecionadas
    private HashSet<Integer> selecionados = new HashSet<>();

    public HistoricoDetalheAdapter(Context c,
                                   ArrayList<String> locais,
                                   ArrayList<String> matriculas,
                                   ArrayList<String> tags) {
        this.ctx = c;
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
        return tags.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    // Alterna seleção
    public void toggleSelection(int position) {
        if (selecionados.contains(position))
            selecionados.remove(position);
        else
            selecionados.add(position);

        notifyDataSetChanged();
    }

    public ArrayList<Integer> getSelecionados() {
        return new ArrayList<>(selecionados);
    }

    public void clearSelection() {
        selecionados.clear();
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = LayoutInflater.from(ctx).inflate(R.layout.item_historico_detalhe, parent, false);

        TextView txtLocal = v.findViewById(R.id.txtLocal);
        TextView txtMatricula = v.findViewById(R.id.txtMatricula);
        TextView txtTag = v.findViewById(R.id.txtTag);

        txtLocal.setText(locais.get(position));
        txtMatricula.setText(matriculas.get(position));
        txtTag.setText(tags.get(position));

        // Se estiver selecionado → muda cor
        if (selecionados.contains(position)) {
            v.setBackgroundColor(Color.parseColor("#D6EAF8")); // azul claro
        } else {
            v.setBackgroundColor(Color.WHITE);
        }

        return v;
    }
}