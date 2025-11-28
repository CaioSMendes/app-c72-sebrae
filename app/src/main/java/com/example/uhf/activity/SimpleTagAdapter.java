package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.uhf.R;
import com.example.uhf.activity.DBHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleTagAdapter extends BaseAdapter {

    private Context context;
    private List<String> tagList;
    private DBHelper db;

    // 🔥 CACHE: DESCRICAO POR TAG
    private Map<String, String> cacheDescricao = new HashMap<>();

    public SimpleTagAdapter(Context context, List<String> tagList, DBHelper db) {
        this.context = context;
        this.tagList = tagList;
        this.db = db;
    }

    @Override
    public int getCount() {
        return tagList.size();
    }

    @Override
    public Object getItem(int position) {
        return tagList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        ImageView imgPatrimonio;
        TextView txtTag, txtItemDescricao;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_tag, parent, false);

            holder = new ViewHolder();
            holder.imgPatrimonio = convertView.findViewById(R.id.imgPatrimonio);
            holder.txtTag = convertView.findViewById(R.id.txtTag);
            holder.txtItemDescricao = convertView.findViewById(R.id.txtItemDescricao);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String tag = tagList.get(position);
        holder.txtTag.setText(tag);

        // 🔎 SE JÁ TEM NO CACHE → NUNCA MAIS BUSCA NO BANCO
        if (cacheDescricao.containsKey(tag)) {
            aplicarResultado(holder, cacheDescricao.get(tag));
            return convertView;
        }

        // ⏳ PRIMEIRA VEZ → MOSTRA “carregando” só 1x
        holder.txtItemDescricao.setText("Carregando...");
        holder.imgPatrimonio.setImageResource(R.drawable.ic_loading);

        // 🌟 BUSCA NO BANCO APENAS UMA VEZ
        new Thread(() -> {
            String fullTag = "040" + tag;
            String resultado = db.getDescricaoPorTag(fullTag);

            // Armazena no cache
            cacheDescricao.put(tag, resultado);

            // Atualiza a UI de forma segura
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    aplicarResultado(holder, resultado);
                });
            }
        }).start();

        return convertView;
    }

    private void aplicarResultado(ViewHolder holder, String resultado) {
        if (resultado != null) {
            String texto = resultado.length() > 25 ?
                    resultado.substring(0, 25) + "..." :
                    resultado;

            holder.txtItemDescricao.setText(texto);
            holder.imgPatrimonio.setImageResource(R.drawable.ic_ativo_pat);

        } else {
            holder.txtItemDescricao.setText("DESCONHECIDO");
            holder.imgPatrimonio.setImageResource(R.drawable.ic_desconhecido);
        }
    }
}