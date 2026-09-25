package com.example.uhf.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.activity.DBHelper;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleTagAdapter extends RecyclerView.Adapter<SimpleTagAdapter.ViewHolder> {

    private final Context      context;
    private final List<String> tagList;
    private final DBHelper     db;

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public SimpleTagAdapter(Context context, List<String> tagList, DBHelper db) {
        this.context = context;
        this.tagList = tagList;
        this.db      = db;
        setHasStableIds(false);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPatrimonio;
        TextView  txtTag, txtItemDescricao;

        public ViewHolder(View v) {
            super(v);
            imgPatrimonio    = v.findViewById(R.id.imgPatrimonio);
            txtTag           = v.findViewById(R.id.txtTag);
            txtItemDescricao = v.findViewById(R.id.txtItemDescricao);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_tag, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position >= tagList.size()) return;

        String rawTag = tagList.get(position);
        holder.txtTag.setText(rawTag);
        holder.itemView.setTag(rawTag);

        if (cache.containsKey(rawTag)) {
            aplicarDescricao(holder, rawTag, cache.get(rawTag));
            return;
        }

        holder.txtItemDescricao.setText("Carregando...");
        holder.imgPatrimonio.clearColorFilter();
        holder.imgPatrimonio.setImageResource(R.drawable.ic_loading);

        new Thread(() -> {
            String resultado = db.getDescricaoPorTag("040" + rawTag);
            String descricao = resultado != null ? resultado : "";
            cache.put(rawTag, descricao);

            ((Activity) context).runOnUiThread(() -> {
                if (rawTag.equals(holder.itemView.getTag())) {
                    aplicarDescricao(holder, rawTag, descricao);
                }
            });
        }).start();
    }

    private void aplicarDescricao(ViewHolder holder, String rawTag, String descricao) {
        holder.imgPatrimonio.clearColorFilter();
        if (descricao != null && !descricao.isEmpty()) {
            String texto = descricao.length() > 25
                    ? descricao.substring(0, 25) + "..."
                    : descricao;
            holder.txtItemDescricao.setText(texto);
            holder.imgPatrimonio.setImageResource(R.drawable.ic_ativo_pat);
        } else {
            holder.txtItemDescricao.setText("DESCONHECIDO");
            holder.imgPatrimonio.setImageResource(R.drawable.ic_desconhecido);
        }
    }

    @Override
    public int getItemCount() {
        return tagList.size();
    }
}