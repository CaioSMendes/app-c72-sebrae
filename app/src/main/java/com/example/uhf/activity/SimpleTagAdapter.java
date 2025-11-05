package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;
import java.util.List;

public class SimpleTagAdapter extends BaseAdapter {

    private final Context context;
    private final List<String> tagList;

    public SimpleTagAdapter(Context context, List<String> tagList) {
        this.context = context;
        this.tagList = tagList;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_tag, parent, false);
        }

        TextView txtTag = view.findViewById(R.id.txtTag);
        txtTag.setText(tagList.get(position));

        return view;
    }
}