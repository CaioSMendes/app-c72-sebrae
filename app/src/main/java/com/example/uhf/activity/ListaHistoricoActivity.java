package com.example.uhf.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.uhf.R;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class ListaHistoricoActivity extends AppCompatActivity {

    ListView list;
    DBHelper db;

    // Dados carregados do banco
    ArrayList<Integer> ids        = new ArrayList<>(); // id de cada registro
    ArrayList<String>  locais     = new ArrayList<>();
    ArrayList<String>  matriculas = new ArrayList<>();
    ArrayList<String>  tags       = new ArrayList<>();
    ArrayList<String>  tipos      = new ArrayList<>();
    ArrayList<String>  dataHoras  = new ArrayList<>();
    ArrayList<String>  descricoes = new ArrayList<>(); // descrição buscada no banco

    DetalheAdapter adapter;
    String localCodigoTela  = "";
    String codigoFilial     = "001";
    String chapaFuncionario = "00000001";

    LinearLayout btnExcluir;

    ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
    Handler mainHandler = new Handler(Looper.getMainLooper());

    private File ultimoArquivoGerado = null;

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico_lista_detalhe);

        list = findViewById(R.id.listHistoricoLocais);
        db   = new DBHelper(this);

        if (getIntent() != null) {
            localCodigoTela = getIntent().getStringExtra("localCodigo");
            String fil  = getIntent().getStringExtra("codigoFilial");
            String chap = getIntent().getStringExtra("chapaFuncionario");
            if (fil  != null) codigoFilial     = fil;
            if (chap != null) chapaFuncionario  = chap;
        }

        LinearLayout btnTxt     = findViewById(R.id.btnConcluir);
        LinearLayout btnPdf     = findViewById(R.id.btnResumo);
        LinearLayout btnVoltar  = findViewById(R.id.btnIncluir);
        btnExcluir              = findViewById(R.id.btnExcluir);

        carregar(localCodigoTela);

        // ── Gerar TXT ──────────────────────────────────────────
        btnTxt.setOnClickListener(v -> {
            if (tags.isEmpty()) {
                Toast.makeText(this, "Nenhum dado para enviar!", Toast.LENGTH_SHORT).show();
                return;
            }
            gerarArquivoTXTComEmail();
        });

        // ── Gerar PDF ──────────────────────────────────────────
        btnPdf.setOnClickListener(v -> {
            if (tags.isEmpty()) {
                Toast.makeText(this, "Nenhum dado para enviar!", Toast.LENGTH_SHORT).show();
                return;
            }
            gerarPDFComEmail();
        });

        // ── Seleção de itens para excluir ──────────────────────
        list.setOnItemClickListener((adapterView, view, position, id) -> {
            adapter.toggleSelecionado(position);
            btnExcluir.setVisibility(
                    adapter.getSelecionados().isEmpty() ? View.GONE : View.VISIBLE);
        });

        // ── Excluir selecionados ───────────────────────────────
        btnExcluir.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Confirmar exclusão")
                    .setMessage("Remover " + adapter.getSelecionados().size()
                            + " registro(s) selecionado(s)?")
                    .setPositiveButton("Remover", (dialog, which) -> excluirSelecionados())
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        // ── Incluir manualmente ────────────────────────────────
        btnVoltar.setOnClickListener(v -> abrirPopupIncluir());
    }

    // ─────────────────────────────────────────────────────────────
    // CARREGAMENTO
    // ─────────────────────────────────────────────────────────────
    private void carregar(String localCodigo) {
        ids.clear();
        locais.clear();
        matriculas.clear();
        tags.clear();
        tipos.clear();
        dataHoras.clear();
        descricoes.clear();

        // listarHistoricoPorLocal retorna todas as colunas: id, sessaoId, filial,
        // localCodigo, matricula, tag, tipo, dataHora
        Cursor c = db.listarHistoricoPorLocal(localCodigo);

        while (c.moveToNext()) {
            ids.add(       safeInt(c, "id"));
            locais.add(    safeStr(c, "localCodigo"));
            matriculas.add(safeStr(c, "matricula"));
            tags.add(      safeStr(c, "tag"));
            tipos.add(     safeStr(c, "tipo"));
            dataHoras.add( safeStr(c, "dataHora"));

            // Descrição do patrimônio pelo código de barra
            String tag = safeStr(c, "tag");
            String tag5 = tag.length() >= 5 ? tag.substring(0, 5) : tag;
            String desc = db.getDescricaoPorTag("040" + tag5);
            descricoes.add(desc != null && !desc.isEmpty() ? desc : "Sem descrição");
        }
        c.close();

        adapter = new DetalheAdapter();
        list.setAdapter(adapter);
    }

    private void carregarHistorico() {
        carregar(localCodigoTela);
        if (btnExcluir != null) btnExcluir.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────
    // EXCLUIR SELECIONADOS
    // ─────────────────────────────────────────────────────────────
    private void excluirSelecionados() {
        ArrayList<Integer> sel = new ArrayList<>(adapter.getSelecionados());
        Collections.sort(sel, Collections.reverseOrder()); // remove de trás pra frente

        for (int pos : sel) {
            db.deletarPorId(ids.get(pos));
            ids.remove(pos);
            locais.remove(pos);
            matriculas.remove(pos);
            tags.remove(pos);
            tipos.remove(pos);
            dataHoras.remove(pos);
            descricoes.remove(pos);
        }

        adapter.clearSelecionados();
        adapter.notifyDataSetChanged();
        btnExcluir.setVisibility(View.GONE);
        Toast.makeText(this, "Registro(s) removido(s).", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────
    // ADAPTER COM NOVO LAYOUT
    // ─────────────────────────────────────────────────────────────
    private class DetalheAdapter extends BaseAdapter {

        private final Set<Integer> selecionados = new HashSet<>();

        void toggleSelecionado(int pos) {
            if (selecionados.contains(pos)) selecionados.remove(pos);
            else selecionados.add(pos);
            notifyDataSetChanged();
        }

        Set<Integer> getSelecionados() { return selecionados; }

        void clearSelecionados() {
            selecionados.clear();
            notifyDataSetChanged();
        }

        @Override public int     getCount()          { return tags.size(); }
        @Override public Object  getItem(int pos)    { return tags.get(pos); }
        @Override public long    getItemId(int pos)  { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ListaHistoricoActivity.this)
                        .inflate(R.layout.item_historico_detalhe, parent, false);
            }

            TextView  txtTag      = convertView.findViewById(R.id.txtTag);
            TextView  txtDescricao= convertView.findViewById(R.id.txtDescricao);
            TextView  txtMatricula= convertView.findViewById(R.id.txtMatricula);
            TextView  txtLocal    = convertView.findViewById(R.id.txtLocal);
            TextView  txtTipo     = convertView.findViewById(R.id.txtTipoLeitura);
            TextView  txtData     = convertView.findViewById(R.id.txtDataHora);
            ImageView imgIcon     = convertView.findViewById(R.id.imgIcon);

            txtTag.setText(tags.get(pos));
            txtDescricao.setText(descricoes.get(pos));
            txtMatricula.setText("Mat: " + matriculas.get(pos));
            txtLocal.setText("Local: " + locais.get(pos));
            txtData.setText(dataHoras.get(pos));

            // Badge colorido por tipo
            String tipo = tipos.get(pos) == null ? "" : tipos.get(pos).toUpperCase();
            switch (tipo) {
                case "LOCAL":
                    txtTipo.setText("Local");
                    txtTipo.setBackgroundResource(R.drawable.bg_badge_verde);
                    imgIcon.setColorFilter(Color.parseColor("#2E7D32"));
                    break;
                case "CATEGORIA":
                    txtTipo.setText("Categoria");
                    txtTipo.setBackgroundResource(R.drawable.bg_badge_azul);
                    imgIcon.setColorFilter(Color.parseColor("#005eb8"));
                    break;
                case "CODBARRA":
                case "CODBARRAS":
                    txtTipo.setText("Cód.Barras");
                    txtTipo.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                    Color.parseColor("#E65100")));
                    imgIcon.setColorFilter(Color.parseColor("#E65100"));
                    break;
                default:
                    txtTipo.setText("Livre");
                    txtTipo.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                    Color.parseColor("#546E7A")));
                    imgIcon.setColorFilter(Color.parseColor("#546E7A"));
                    break;
            }

            // Destaca item selecionado
            convertView.setBackgroundColor(
                    selecionados.contains(pos)
                            ? Color.parseColor("#FFF9C4")  // amarelo suave
                            : Color.WHITE);

            return convertView;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POPUP INCLUIR MANUALMENTE (original preservado)
    // ─────────────────────────────────────────────────────────────
    private void abrirPopupIncluir() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText edtMatricula = new EditText(this);
        edtMatricula.setHint("Matrícula");
        edtMatricula.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtMatricula.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        layout.addView(edtMatricula);

        EditText edtLocal = new EditText(this);
        edtLocal.setHint("Código Local");
        edtLocal.setText(localCodigoTela);
        edtLocal.setEnabled(false);
        edtLocal.setTextColor(Color.GRAY);
        layout.addView(edtLocal);

        EditText edtTag = new EditText(this);
        edtTag.setHint("Tag EPC");
        edtTag.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtTag.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        layout.addView(edtTag);

        new AlertDialog.Builder(this)
                .setTitle("Incluir novo registro")
                .setView(layout)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String matricula   = edtMatricula.getText().toString().trim();
                    String codigoLocal = edtLocal.getText().toString().trim();
                    String tag         = edtTag.getText().toString().trim();

                    if (matricula.length() != 4) {
                        Toast.makeText(this, "Matrícula deve ter 4 dígitos!", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (codigoLocal.length() != 4) {
                        Toast.makeText(this, "Código local deve ter 4 dígitos!", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (tag.length() != 6) {
                        Toast.makeText(this, "Tag EPC deve ter 6 dígitos!", Toast.LENGTH_SHORT).show(); return;
                    }

                    db.inserirHistorico(matricula, codigoLocal, tag);
                    carregarHistorico();
                    Toast.makeText(this, "Registro adicionado!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // POPUP EMAIL (original preservado)
    // ─────────────────────────────────────────────────────────────
    private void abrirPopupEmail(List<File> arquivos) {
        EditText input = new EditText(this);
        input.setHint("Digite o e-mail do destinatário");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        new AlertDialog.Builder(this)
                .setTitle("Enviar Arquivo")
                .setMessage("Deseja enviar o arquivo por e-mail?")
                .setView(input)
                .setPositiveButton("Enviar", (d, w) -> {
                    String email = input.getText().toString().trim();
                    if (!email.isEmpty()) enviarArquivosPorEmail(arquivos, email);
                    else Toast.makeText(this, "Informe um e-mail!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────
    // GERAR TXT (original preservado)
    // ─────────────────────────────────────────────────────────────
    private void gerarArquivoTXTComEmail() {
        try {
            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            File arquivo = new File(pasta, "Historico_" + localCodigoTela + ".txt");
            FileOutputStream fos = new FileOutputStream(arquivo);
            int pulados = 0;

            for (int i = 0; i < tags.size(); i++) {
                String codigoBarra    = tags.get(i);
                String matriculaAtual = matriculas.get(i);

                if (!isNumeroValido(codigoFilial, 3)
                        || !isNumeroValido(localCodigoTela, 4)
                        || !isNumeroValido(matriculaAtual, 8)) {
                    pulados++; continue;
                }

                String epcLimpo;
                if (codigoBarra.startsWith("40") && codigoBarra.length() >= 7)
                    epcLimpo = codigoBarra.substring(2, 7);
                else
                    epcLimpo = codigoBarra.length() >= 5 ? codigoBarra.substring(0, 5) : codigoBarra;

                codigoBarra = "040" + epcLimpo;

                String linha = String.format("%03d", Integer.parseInt(codigoFilial)) + " "
                        + String.format("%04d", Integer.parseInt(localCodigoTela)) + "  "
                        + String.format("%08d", Integer.parseInt(matriculaAtual))
                        + "                      " + codigoBarra + "\n";
                fos.write(linha.getBytes());
            }
            fos.close();

            String msg = "TXT gerado!";
            if (pulados > 0) msg += " (" + pulados + " registros inválidos pulados)";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            abrirPopupEmail(Collections.singletonList(arquivo));

        } catch (Exception e) {
            Toast.makeText(this, "Erro TXT: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNumeroValido(String valor, int max) {
        return valor != null && !valor.trim().isEmpty()
                && valor.matches("\\d+") && valor.length() <= max;
    }

    // ─────────────────────────────────────────────────────────────
    // GERAR PDF (original preservado)
    // ─────────────────────────────────────────────────────────────
    private ArrayList<ResumoItem> montarResumoInterno() {
        HashMap<String, Integer> mapa = new HashMap<>();
        for (String tagCompleta : tags) {
            String tag5    = tagCompleta.length() >= 5 ? tagCompleta.substring(0, 5) : tagCompleta;
            String tagBanco = "040" + tag5;
            String descricao = db.getDescricaoPorTag(tagBanco);
            if (descricao == null || descricao.trim().isEmpty()) descricao = "DESCONHECIDO";
            mapa.put(descricao, mapa.containsKey(descricao) ? mapa.get(descricao) + 1 : 1);
        }
        ArrayList<ResumoItem> lista = new ArrayList<>();
        for (Map.Entry<String, Integer> e : mapa.entrySet())
            lista.add(new ResumoItem(e.getKey(), e.getValue()));
        return lista;
    }

    private void gerarPDFComEmail() {
        try {
            PdfDocument pdf = new PdfDocument();

            // ── Capa ──────────────────────────────────────────────
            PdfDocument.PageInfo capaInfo =
                    new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page capaPage = pdf.startPage(capaInfo);
            Canvas canvas = capaPage.getCanvas();

            Paint paintBlue  = new Paint(); paintBlue.setColor(Color.rgb(0, 94, 184));
            Paint paintBranco= new Paint(); paintBranco.setColor(Color.WHITE); paintBranco.setTextSize(32f); paintBranco.setFakeBoldText(true);
            Paint paintInfo  = new Paint(); paintInfo.setColor(Color.WHITE);  paintInfo.setTextSize(18f);

            canvas.drawRect(0, 0, 595, 842, paintBlue);

            Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sebrae_branco);
            Bitmap logoGrande = Bitmap.createScaledBitmap(logo, 260, 80, true);
            canvas.drawBitmap(logoGrande, (595 - logoGrande.getWidth()) / 2f, 120, null);

            canvas.drawText("Histórico de Leitura RFID", 100, 260, paintBranco);
            int yCapa = 340;
            canvas.drawText("Local: " + localCodigoTela, 60, yCapa, paintInfo); yCapa += 30;
            canvas.drawText("Total de Tags: " + tags.size(), 60, yCapa, paintInfo); yCapa += 30;
            canvas.drawText("Gerado em: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()), 60, yCapa, paintInfo);

            Paint rodape = new Paint(); rodape.setColor(Color.WHITE); rodape.setTextSize(14f);
            canvas.drawText("SEBRAE © " + new SimpleDateFormat("yyyy").format(new Date()), 240, 810, rodape);
            pdf.finishPage(capaPage);

            // ── Resumo ────────────────────────────────────────────
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(595, 842, 2).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            canvas = page.getCanvas();

            Paint paintTexto  = new Paint(); paintTexto.setColor(Color.BLACK);  paintTexto.setTextSize(16f);
            Paint paintHeader = new Paint(); paintHeader.setColor(Color.rgb(220,220,220));
            Paint paintZebra  = new Paint(); paintZebra.setColor(Color.rgb(240,240,240));
            Paint paintTit    = new Paint(); paintTit.setColor(Color.BLACK); paintTit.setTextSize(22f); paintTit.setFakeBoldText(true);
            Paint paintLinha  = new Paint(); paintLinha.setColor(Color.BLACK); paintLinha.setStrokeWidth(2);

            canvas.drawText("Resumo do Inventário", 180, 70, paintTit);
            int y = 110;
            canvas.drawRect(40, y-25, 555, y+5, paintHeader);
            canvas.drawText("Qtd", 60, y, paintTexto);
            canvas.drawText("Descrição", 150, y, paintTexto);
            y += 25; canvas.drawLine(40, y, 555, y, paintLinha); y += 20;

            boolean zebra = false; int paginaAtual = 2; int totalGeral = 0;
            ArrayList<ResumoItem> resumo = montarResumoInterno();

            for (ResumoItem item : resumo) {
                if (zebra) canvas.drawRect(40, y-18, 555, y+10, paintZebra);
                zebra = !zebra;

                String desc = item.getDescricao();
                if (desc.length() > 48) desc = desc.substring(0, 48) + "...";
                canvas.drawText(String.valueOf(item.getQuantidade()), 60, y, paintTexto);
                canvas.drawText(desc, 150, y, paintTexto);
                totalGeral += item.getQuantidade();
                y += 28;

                if (y > 760) {
                    Paint rp = new Paint(); rp.setColor(Color.GRAY); rp.setTextSize(12f);
                    canvas.drawText("Página " + paginaAtual, 500, 820, rp);
                    pdf.finishPage(page); paginaAtual++;
                    page = pdf.startPage(pageInfo); canvas = page.getCanvas();
                    canvas.drawText("Resumo (continuação)", 160, 70, paintTit);
                    y = 110;
                    canvas.drawRect(40, y-25, 555, y+5, paintHeader);
                    canvas.drawText("Qtd", 60, y, paintTexto);
                    canvas.drawText("Descrição", 150, y, paintTexto);
                    y += 25; canvas.drawLine(40, y, 555, y, paintLinha); y += 20;
                }
            }

            y += 10; canvas.drawLine(40, y, 555, y, paintLinha); y += 28;
            Paint paintTotal = new Paint(); paintTotal.setColor(Color.BLACK); paintTotal.setTextSize(18f); paintTotal.setFakeBoldText(true);
            canvas.drawText("TOTAL GERAL: " + totalGeral + " itens", 60, y, paintTotal);

            Paint rpFinal = new Paint(); rpFinal.setColor(Color.GRAY); rpFinal.setTextSize(12f);
            canvas.drawText("Página " + paginaAtual, 500, 820, rpFinal);
            pdf.finishPage(page);

            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();
            File arquivo = new File(pasta, "Historico_RFID_" + localCodigoTela + ".pdf");
            FileOutputStream fos = new FileOutputStream(arquivo);
            pdf.writeTo(fos); pdf.close(); fos.close();
            ultimoArquivoGerado = arquivo;

            Toast.makeText(this, "PDF gerado com sucesso!", Toast.LENGTH_LONG).show();
            abrirPopupEmail(Collections.singletonList(arquivo));

        } catch (Exception e) {
            Toast.makeText(this, "Erro ao gerar PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ENVIO EMAIL (original preservado)
    // ─────────────────────────────────────────────────────────────
    private void enviarArquivosPorEmail(List<File> arquivos, String destinatario) {
        String usuario = "smartmailbuilding@gmail.com";
        String senha   = "ebzzwrvykwihempj";

        emailExecutor.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(usuario, senha);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(usuario));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
                message.setSubject("Inventário RFID - Local " + localCodigoTela);

                MimeMultipart multipart = new MimeMultipart();
                MimeBodyPart corpo = new MimeBodyPart();
                corpo.setText("Segue arquivo(s) gerado(s) pelo inventário RFID.");
                multipart.addBodyPart(corpo);

                for (File arquivo : arquivos) {
                    MimeBodyPart anexo = new MimeBodyPart();
                    anexo.setDataHandler(new DataHandler(new FileDataSource(arquivo)));
                    anexo.setFileName(arquivo.getName());
                    multipart.addBodyPart(anexo);
                }
                message.setContent(multipart);
                Transport.send(message);

                mainHandler.post(() -> Toast.makeText(this,
                        "E-mail enviado com sucesso!", Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this,
                        "Erro ao enviar: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void compartilharPdfViaIntent(File arquivo) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", arquivo);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Enviar PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao compartilhar: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
    private String safeStr(Cursor c, String col) {
        try {
            int idx = c.getColumnIndex(col);
            return idx >= 0 && !c.isNull(idx) ? c.getString(idx) : "";
        } catch (Exception e) { return ""; }
    }

    private int safeInt(Cursor c, String col) {
        try {
            int idx = c.getColumnIndex(col);
            return idx >= 0 && !c.isNull(idx) ? c.getInt(idx) : -1;
        } catch (Exception e) { return -1; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        emailExecutor.shutdown();
    }

    // ─────────────────────────────────────────────────────────────
    // MODELO INTERNO
    // ─────────────────────────────────────────────────────────────
    private static class ResumoItem {
        private final String descricao;
        private final int    quantidade;
        ResumoItem(String d, int q) { descricao = d; quantidade = q; }
        String getDescricao()  { return descricao;  }
        int    getQuantidade() { return quantidade; }
    }
}