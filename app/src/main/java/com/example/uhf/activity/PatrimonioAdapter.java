package com.example.uhf.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.uhf.R;
import com.example.uhf.model.Patrimonio; // <-- Import obrigatório

import java.util.ArrayList;
import java.util.List;

public class PatrimonioAdapter extends BaseAdapter {

    private Context context;
    private List<Patrimonio> originalList; // lista completa
    private List<Patrimonio> filteredList; // lista filtrada

    public PatrimonioAdapter(Context context, List<Patrimonio> lista) {
        this.context = context;
        this.originalList = lista;
        this.filteredList = new ArrayList<>(lista);
    }

    @Override
    public int getCount() {
        return filteredList.size();
    }

    @Override
    public Object getItem(int position) {
        return filteredList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return filteredList.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_patrimonio, parent, false);
        }

        Patrimonio p = filteredList.get(position);

        ImageView img = convertView.findViewById(R.id.imgPatrimonio);
        TextView txtCodigo = convertView.findViewById(R.id.txtItemCodigo);
        TextView txtDescricao = convertView.findViewById(R.id.txtItemDescricao);

        // Aqui você define a imagem do patrimônio (pode ser diferente se quiser)
        img.setImageResource(R.drawable.ic_ativo_pat);

        // Exibe código de barra e descrição
        txtCodigo.setText(p.getCodigoBarra());

        String descricao = p.getDescricao();
        if (descricao.length() > 15) {
            descricao = descricao.substring(0, 15) + "...";
        }
        txtDescricao.setText(descricao);

        return convertView;
    }

    // Método para filtrar a lista pelo nome ou código
    public void filter(String text) {
        filteredList.clear();
        if (text.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            text = text.toLowerCase();
            for (Patrimonio p : originalList) {
                if (p.getPatrimonio().toLowerCase().contains(text) ||
                        p.getCodigoBarra().toLowerCase().contains(text)) {
                    filteredList.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }
}