package com.example.uhf.activity;

import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import java.util.ArrayList;
import java.util.List;

public class HistoricoActivity extends AppCompatActivity {

    private ListView listView;
    private DBHelper db;
    private HistoricoAdapter adapter;

    private final List<ItemHistorico> itens = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico);

        listView = findViewById(R.id.listHistoricoLocais);
        db       = new DBHelper(this);

        adapter = new HistoricoAdapter();
        listView.setAdapter(adapter);

        // Toque simples → abre detalhe das tags daquele local
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ItemHistorico item = itens.get(position);
            android.content.Intent i = new android.content.Intent(
                    this, ListaHistoricoActivity.class);
            i.putExtra("localCodigo", item.localCodigo);
            startActivity(i);
        });

        // Toque longo → confirma exclusão de todo o histórico do local
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmarExclusao(itens.get(position), position);
            return true;
        });

        carregarLista();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLista();
    }

    private void carregarLista() {
        itens.clear();
        Cursor c = db.listarHistoricoAgrupadoComTipo();
        while (c.moveToNext()) {
            ItemHistorico item  = new ItemHistorico();
            item.localCodigo    = nvl(c, 0);
            item.localNome      = nvl(c, 1);
            item.filial         = nvl(c, 2);
            item.tipoInventario = nvl(c, 3);
            item.ultimaData     = nvl(c, 4);
            item.totalTags      = c.getColumnCount() > 5 ? c.getInt(5) : 0;
            itens.add(item);
        }
        c.close();
        adapter.notifyDataSetChanged();
        if (itens.isEmpty())
            Toast.makeText(this, "Nenhum histórico encontrado.", Toast.LENGTH_SHORT).show();
    }

    private String nvl(Cursor c, int col) {
        try { return c.isNull(col) ? "" : c.getString(col); }
        catch (Exception e) { return ""; }
    }

    private void confirmarExclusao(ItemHistorico item, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Apagar histórico")
                .setMessage("Apagar todo o histórico do local\n\""
                        + item.localNome + "\" (" + item.localCodigo + ")?")
                .setPositiveButton("Apagar", (dialog, which) -> {
                    db.deletarPorLocal(item.localCodigo);
                    itens.remove(position);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Histórico apagado.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static class ItemHistorico {
        String localCodigo, localNome, filial, tipoInventario, ultimaData;
        int totalTags;
    }

    private class HistoricoAdapter extends BaseAdapter {

        @Override public int     getCount()          { return itens.size(); }
        @Override public Object  getItem(int pos)    { return itens.get(pos); }
        @Override public long    getItemId(int pos)  { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(HistoricoActivity.this)
                        .inflate(R.layout.item_historico_sessao, parent, false);

            ItemHistorico item = itens.get(pos);

            ImageView imgTipo     = convertView.findViewById(R.id.imgTipoInventario);
            TextView txtLocalNome = convertView.findViewById(R.id.txtHistLocalNome);
            TextView txtTipo      = convertView.findViewById(R.id.txtHistTipo);
            TextView txtData      = convertView.findViewById(R.id.txtHistData);
            TextView txtTotal     = convertView.findViewById(R.id.txtHistTotal);
            TextView txtDica      = convertView.findViewById(R.id.txtHistDica);

            txtLocalNome.setText(
                    (item.localNome != null && !item.localNome.isEmpty()
                            ? item.localNome : "Local " + item.localCodigo)
                            + " • Filial " + item.filial);
            txtData.setText(item.ultimaData);
            txtTotal.setText(item.totalTags + " tag(s)");
            txtDica.setText("Segure para apagar");

            // Badge colorido por tipo — só setBackgroundResource, sem setBackgroundTintList
            switch (item.tipoInventario == null ? "" : item.tipoInventario.toUpperCase()) {
                case "LOCAL":
                    txtTipo.setText("Por Local");
                    txtTipo.setBackgroundResource(R.drawable.bg_badge_verde);
                    imgTipo.setColorFilter(Color.parseColor("#2E7D32"));
                    break;
                case "CATEGORIA":
                    txtTipo.setText("Por Categoria");
                    txtTipo.setBackgroundResource(R.drawable.bg_badge_azul);
                    imgTipo.setColorFilter(Color.parseColor("#005eb8"));
                    break;
                case "CODBARRA":
                case "CODBARRAS":
                    txtTipo.setText("Cód. Barras");
                    txtTipo.setBackgroundResource(R.drawable.bg_badge_laranja);
                    imgTipo.setColorFilter(Color.parseColor("#E65100"));
                    break;
                default: // LIVRE, RFID ou vazio
                    txtTipo.setText("Leitura Livre");
                    txtTipo.setBackgroundResource(R.drawable.bg_badge_cinza);
                    imgTipo.setColorFilter(Color.parseColor("#546E7A"));
                    break;
            }
            // Garante texto branco em qualquer badge
            txtTipo.setTextColor(Color.WHITE);

            return convertView;
        }
    }
}