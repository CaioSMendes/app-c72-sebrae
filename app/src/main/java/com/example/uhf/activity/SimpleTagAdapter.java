package com.example.uhf.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.uhf.R;
import com.example.uhf.activity.DBHelper;

import java.util.List;

public class SimpleTagAdapter extends BaseAdapter {
    private Context context;
    private List<String> tagList;
    private DBHelper db;

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
            convertView = LayoutInflater.from(context).inflate(R.layout.item_tag, parent, false);
            holder = new ViewHolder();
            holder.imgPatrimonio = convertView.findViewById(R.id.imgPatrimonio);
            holder.txtTag = convertView.findViewById(R.id.txtTag);
            holder.txtItemDescricao = convertView.findViewById(R.id.txtItemDescricao);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String rawTag = tagList.get(position);
        holder.txtTag.setText(rawTag);

        holder.txtItemDescricao.setText("Carregando...");
        holder.imgPatrimonio.setImageResource(R.drawable.ic_loading);

        // 🔐 GUARDA A POSIÇÃO PARA EVITAR CRASH
        final int posFinal = position;
        final ViewHolder holderFinal = holder;

        new Thread(() -> {
            String fullTag = "040" + rawTag;
            String resultado = db.getDescricaoPorTag(fullTag);

            ((Activity) context).runOnUiThread(() -> {

                // 👇 Antes de atualizar, confirma que a linha ainda existe
                if (posFinal >= tagList.size())
                    return;

                String atual = tagList.get(posFinal);

                // 👌 Se o item mudou (recycling), não atualiza
                if (!atual.equals(rawTag))
                    return;

                if (resultado != null) {
                    String texto = resultado.length() > 25 ?
                            resultado.substring(0, 25) + "..." :
                            resultado;

                    holderFinal.txtItemDescricao.setText(texto);
                    holderFinal.imgPatrimonio.setImageResource(R.drawable.ic_ativo_pat);
                } else {
                    holderFinal.txtItemDescricao.setText("DESCONHECIDO");
                    holderFinal.imgPatrimonio.setImageResource(R.drawable.ic_desconhecido);
                }
            });
        }).start();

        return convertView;
    }
}
