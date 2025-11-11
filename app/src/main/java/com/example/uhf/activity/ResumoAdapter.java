package com.example.uhf.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;

import java.util.List;

public class ResumoAdapter extends BaseAdapter {

    private Context context;
    private List<ResumoItem> lista;

    public ResumoAdapter(Context context, List<ResumoItem> lista) {
        this.context = context;
        this.lista = lista;
    }

    @Override
    public int getCount() { return lista.size(); }

    @Override
    public Object getItem(int i) { return lista.get(i); }

    @Override
    public long getItemId(int i) { return i; }

    static class Holder {
        View faixa;
        TextView descricao, qtd;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {

        Holder h;

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_resumo, parent, false);

            h = new Holder();
            h.faixa = convertView.findViewById(R.id.faixaCor);
            h.descricao = convertView.findViewById(R.id.txtResumoDescricao);
            h.qtd = convertView.findViewById(R.id.txtResumoQtd);

            convertView.setTag(h);
        } else h = (Holder) convertView.getTag();

        ResumoItem item = lista.get(pos);

        h.descricao.setText(item.getDescricao());
        h.qtd.setText(item.getQuantidade() + "x");

        int[] cores = { 0xff2196F3, 0xff4CAF50, 0xffFF9800,
                0xff9C27B0, 0xff009688, 0xffF44336 };

        h.faixa.setBackgroundColor(cores[pos % cores.length]);

        return convertView;
    }
}
