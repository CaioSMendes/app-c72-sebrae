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
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.uhf.R;
import com.example.uhf.adapter.HistoricoDetalheAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

    ArrayList<String> locais = new ArrayList<>();
    ArrayList<String> matriculas = new ArrayList<>();
    ArrayList<String> tags = new ArrayList<>();

    // AGORA É GLOBAL
    HistoricoDetalheAdapter adapter;
    String localCodigoTela = "";
    String codigoFilial = "001";
    String chapaFuncionario = "00000001";

    ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
    Handler mainHandler = new Handler(Looper.getMainLooper());

    // Guarda último arquivo gerado para envio rápido (opcional)
    private File ultimoArquivoGerado = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico_lista_detalhe);

        list = findViewById(R.id.listHistoricoLocais);
        db = new DBHelper(this);

        // Recebe parâmetros
        if (getIntent() != null) {
            localCodigoTela = getIntent().getStringExtra("localCodigo");
            String fil = getIntent().getStringExtra("codigoFilial");
            String chap = getIntent().getStringExtra("chapaFuncionario");

            if (fil != null) codigoFilial = fil;
            if (chap != null) chapaFuncionario = chap;
        }

        // Botões -------------- AQUI ESTAVA O ERRO
        LinearLayout btnTxt = findViewById(R.id.btnConcluir);
        LinearLayout btnPdf = findViewById(R.id.btnResumo);
        LinearLayout btnVoltar = findViewById(R.id.btnIncluir);
        LinearLayout btnExcluir = findViewById(R.id.btnExcluir); // <-- CORREÇÃO

        carregar(localCodigoTela);

        btnTxt.setOnClickListener(v -> {
            if (tags.isEmpty()) {
                Toast.makeText(this, "Nenhum dado para enviar!", Toast.LENGTH_SHORT).show();
                return;
            }
            gerarArquivoTXTComEmail();
        });

        btnPdf.setOnClickListener(v -> {
            if (tags.isEmpty()) {
                Toast.makeText(this, "Nenhum dado para enviar!", Toast.LENGTH_SHORT).show();
                return;
            }
            gerarPDFComEmail();
        });

        list.setOnItemClickListener((adapterView, view, position, id) -> {
            adapter.toggleSelection(position);

            if (adapter.getSelecionados().size() > 0) {
                btnExcluir.setVisibility(View.VISIBLE);
            } else {
                btnExcluir.setVisibility(View.GONE);
            }
        });

        btnExcluir.setOnClickListener(v -> {
            ArrayList<Integer> sel = adapter.getSelecionados();
            sel.sort((a, b) -> b - a);

            for (int pos : sel) {
                db.deletarPorTag(tags.get(pos));
                locais.remove(pos);
                matriculas.remove(pos);
                tags.remove(pos);
            }

            adapter.clearSelection();
            adapter.notifyDataSetChanged();
            btnExcluir.setVisibility(View.GONE);
        });

        btnVoltar.setOnClickListener(v -> abrirPopupIncluir());
    }

    private void carregarHistorico() {
        matriculas.clear();
        locais.clear();
        tags.clear();

        Cursor c = db.getHistoricoPorLocal(localCodigoTela);

        while (c.moveToNext()) {

            String m = c.getString(c.getColumnIndex("matricula"));
            String l = c.getString(c.getColumnIndex("localCodigo"));
            String t = c.getString(c.getColumnIndex("tag"));

            matriculas.add(m);
            locais.add(l);
            tags.add(t);
        }

        c.close();

        adapter.notifyDataSetChanged();
    }


    private void abrirPopupIncluir() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        // ➤ MATRÍCULA (4 dígitos)
        EditText edtMatricula = new EditText(this);
        edtMatricula.setHint("Matrícula");
        edtMatricula.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtMatricula.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(4)
        });
        layout.addView(edtMatricula);

        // ➤ CÓDIGO LOCAL — preenchido e bloqueado
        EditText edtLocal = new EditText(this);
        edtLocal.setHint("Código Local");
        edtLocal.setText(localCodigoTela);
        edtLocal.setEnabled(false);
        edtLocal.setTextColor(Color.GRAY);
        layout.addView(edtLocal);

        // ➤ TAG EPC (6 dígitos)
        EditText edtTag = new EditText(this);
        edtTag.setHint("Tag EPC");
        edtTag.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtTag.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(6)
        });
        layout.addView(edtTag);

        new AlertDialog.Builder(this)
                .setTitle("Incluir novo registro")
                .setView(layout)
                .setPositiveButton("Salvar", (dialog, which) -> {

                    String matricula = edtMatricula.getText().toString().trim();
                    String codigoLocal = edtLocal.getText().toString().trim();
                    String tag = edtTag.getText().toString().trim();

                    // ➤ Validações
                    if (matricula.length() != 4) {
                        Toast.makeText(this, "A matrícula deve ter 4 dígitos!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (codigoLocal.length() != 4) {
                        Toast.makeText(this, "O código local deve ter 4 dígitos!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (tag.length() != 6) {
                        Toast.makeText(this, "A Tag EPC deve ter 6 dígitos!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // ➤ SALVA NO BANCO
                    db.inserirHistorico(matricula, codigoLocal, tag);

                    // ➤ RECARREGA LISTA DO BANCO
                    carregarHistorico();

                    // ➤ ATUALIZA O ADAPTER
                    adapter.notifyDataSetChanged();

                    Toast.makeText(this, "Registro adicionado!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }



    // POPUP PARA DIGITAR E-MAIL
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
                    if (!email.isEmpty())
                        enviarArquivosPorEmail(arquivos, email);
                    else
                        Toast.makeText(this, "Informe um e-mail!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // CARREGAR LISTA DO BANCO
    private void carregar(String localCodigo) {

        locais = new ArrayList<>();
        matriculas = new ArrayList<>();
        tags = new ArrayList<>();

        Cursor c = db.getDados(localCodigo);

        while (c.moveToNext()) {
            locais.add(c.getString(0));
            matriculas.add(c.getString(1));
            tags.add(c.getString(2));
        }
        c.close();

        adapter = new HistoricoDetalheAdapter(this, locais, matriculas, tags);
        list.setAdapter(adapter);
    }

    // GERA TXT + EMAIL
    private void gerarArquivoTXTComEmail() {
        try {
            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            File arquivo = new File(pasta, "Historico_" + localCodigoTela + ".txt");
            FileOutputStream fos = new FileOutputStream(arquivo);

            for (int i = 0; i < tags.size(); i++) {

                String epc = tags.get(i).replaceFirst("^0+", "");
                if (epc.length() < 5) epc = String.format("%-5s", epc).replace(" ", "0");
                else epc = epc.substring(0, 5);

                String codigoBarra = "040" + epc;

                String filialFmt = String.format("%03d", Integer.parseInt(codigoFilial));
                String localFmt = String.format("%04d", Integer.parseInt(localCodigoTela));
                String matriculaFmt = String.format("%08d", Integer.parseInt(matriculas.get(i)));

                String linha = filialFmt + " " + localFmt + "  " + matriculaFmt +
                        "                      " + codigoBarra + "\n";

                fos.write(linha.getBytes());
            }

            fos.close();
            Toast.makeText(this, "TXT gerado!", Toast.LENGTH_SHORT).show();

            abrirPopupEmail(Collections.singletonList(arquivo));

        } catch (Exception e) {
            Toast.makeText(this, "Erro TXT: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------------
    // MONTA RESUMO AGRUPADO (TAG -> DESCRIÇÃO/CONTAGEM)
    // ----------------------------
    private ArrayList<ResumoItem> montarResumoInterno() {
        HashMap<String, Integer> mapa = new HashMap<>();

        for (String tagCompleta : tags) {
            String tag5 = tagCompleta.length() >= 5 ? tagCompleta.substring(0, 5) : tagCompleta;
            String tagBanco = "040" + tag5;

            // Usa método do DBHelper para buscar descrição (presumido existente)
            String descricao = db.getDescricaoPorTag(tagBanco);
            if (descricao == null || descricao.trim().isEmpty())
                descricao = "DESCONHECIDO";

            Integer c = mapa.get(descricao);
            if (c == null) mapa.put(descricao, 1);
            else mapa.put(descricao, c + 1);
        }

        ArrayList<ResumoItem> lista = new ArrayList<>();
        for (Map.Entry<String, Integer> e : mapa.entrySet()) {
            lista.add(new ResumoItem(e.getKey(), e.getValue()));
        }

        return lista;
    }

    // GERA PDF + EMAIL (AGORA COM RESUMO)
    private void gerarPDFComEmail() {
        try {
            PdfDocument pdf = new PdfDocument();

            // ====================== CAPA ============================
            PdfDocument.PageInfo capaInfo =
                    new PdfDocument.PageInfo.Builder(595, 842, 1).create();

            PdfDocument.Page capaPage = pdf.startPage(capaInfo);
            Canvas canvas = capaPage.getCanvas();

            Paint paintBlue = new Paint();
            paintBlue.setColor(Color.rgb(0, 94, 184));

            Paint paintTitulo = new Paint();
            paintTitulo.setColor(Color.WHITE);
            paintTitulo.setTextSize(32f);
            paintTitulo.setFakeBoldText(true);

            Paint paintInfo = new Paint();
            paintInfo.setColor(Color.WHITE);
            paintInfo.setTextSize(18f);

            canvas.drawRect(0, 0, 595, 842, paintBlue);

            Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sebrae_branco);
            Bitmap logoGrande = Bitmap.createScaledBitmap(logo, 260, 80, true);
            canvas.drawBitmap(logoGrande, (595 - logoGrande.getWidth()) / 2, 120, null);

            canvas.drawText("Histórico de Leitura RFID", 100, 260, paintTitulo);

            int yCapa = 340;
            canvas.drawText("Local: " + localCodigoTela, 60, yCapa, paintInfo); yCapa += 30;
            canvas.drawText("Total de Tags Lidas: " + tags.size(), 60, yCapa, paintInfo); yCapa += 30;

            String dataGeracao = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
            canvas.drawText("Gerado em: " + dataGeracao, 60, yCapa, paintInfo);

            Paint rodape = new Paint();
            rodape.setColor(Color.WHITE);
            rodape.setTextSize(14f);
            canvas.drawText("SEBRAE © " + new SimpleDateFormat("yyyy").format(new Date()),
                    240, 810, rodape);

            pdf.finishPage(capaPage);

            // ==================== RESUMO (AGRUPO) ===========================
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(595, 842, 2).create();

            PdfDocument.Page page = pdf.startPage(pageInfo);
            canvas = page.getCanvas();

            Paint paintTexto = new Paint();
            paintTexto.setColor(Color.BLACK);
            paintTexto.setTextSize(16f);

            Paint paintHeader = new Paint();
            paintHeader.setColor(Color.rgb(220, 220, 220));

            Paint paintZebra = new Paint();
            paintZebra.setColor(Color.rgb(240, 240, 240));

            Paint paintTituloPg = new Paint();
            paintTituloPg.setColor(Color.BLACK);
            paintTituloPg.setTextSize(22f);
            paintTituloPg.setFakeBoldText(true);

            Paint paintLinha = new Paint();
            paintLinha.setColor(Color.BLACK);
            paintLinha.setStrokeWidth(2);

            canvas.drawText("Resumo do Inventário", 180, 70, paintTituloPg);

            int y = 110;

            // Cabeçalho da tabela resumo
            canvas.drawRect(40, y - 25, 555, y + 5, paintHeader);
            canvas.drawText("Qtd", 60, y, paintTexto);
            canvas.drawText("Descrição", 150, y, paintTexto);

            y += 25;
            canvas.drawLine(40, y, 555, y, paintLinha);
            y += 20;

            boolean zebra = false;
            int paginaAtual = 2;

            ArrayList<ResumoItem> resumo = montarResumoInterno();
            int totalGeral = 0;

            for (ResumoItem item : resumo) {

                if (zebra)
                    canvas.drawRect(40, y - 18, 555, y + 10, paintZebra);

                zebra = !zebra;

                String descricao = item.getDescricao();
                if (descricao.length() > 48) descricao = descricao.substring(0, 48) + "...";

                canvas.drawText(String.valueOf(item.getQuantidade()), 60, y, paintTexto);
                canvas.drawText(descricao, 150, y, paintTexto);

                totalGeral += item.getQuantidade();

                y += 28;

                if (y > 760) {
                    Paint rodapePag = new Paint();
                    rodapePag.setColor(Color.GRAY);
                    rodapePag.setTextSize(12f);

                    canvas.drawText("Página " + paginaAtual, 500, 820, rodapePag);

                    pdf.finishPage(page);
                    paginaAtual++;

                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();

                    // redesenha título na nova página (opcional)
                    canvas.drawText("Resumo do Inventário (continuação)", 160, 70, paintTituloPg);
                    y = 110;
                    canvas.drawRect(40, y - 25, 555, y + 5, paintHeader);
                    canvas.drawText("Qtd", 60, y, paintTexto);
                    canvas.drawText("Descrição", 150, y, paintTexto);
                    y += 25;
                    canvas.drawLine(40, y, 555, y, paintLinha);
                    y += 20;
                }
            }

            // total geral
            y += 10;
            canvas.drawLine(40, y, 555, y, paintLinha);
            y += 28;

            Paint paintTotal = new Paint();
            paintTotal.setColor(Color.BLACK);
            paintTotal.setTextSize(18f);
            paintTotal.setFakeBoldText(true);
            canvas.drawText("TOTAL GERAL: " + totalGeral + " itens", 60, y, paintTotal);

            Paint rodapeFinal = new Paint();
            rodapeFinal.setColor(Color.GRAY);
            rodapeFinal.setTextSize(12f);
            canvas.drawText("Página " + paginaAtual, 500, 820, rodapeFinal);

            pdf.finishPage(page);

            // SALVAR PDF
            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            File arquivo = new File(pasta, "Historico_RFID_" + localCodigoTela + ".pdf");

            FileOutputStream fos = new FileOutputStream(arquivo);
            pdf.writeTo(fos);

            pdf.close();
            fos.close();

            ultimoArquivoGerado = arquivo; // para uso posterior se quiser enviar rápido

            Toast.makeText(this, "PDF gerado com sucesso!", Toast.LENGTH_LONG).show();

            abrirPopupEmail(Collections.singletonList(arquivo));

        } catch (Exception e) {
            Toast.makeText(this, "Erro ao gerar PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ENVIO DE E-MAIL (ACEITA TXT + PDF)
    private void enviarArquivosPorEmail(List<File> arquivos, String destinatario) {

        String usuario = "smartmailbuilding@gmail.com";
        String senha = "ebzzwrvykwihempj";

        emailExecutor.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(usuario, senha);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(usuario));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
                message.setSubject("Inventário RFID - Local " + localCodigoTela);

                MimeMultipart multipart = new MimeMultipart();

                // Corpo
                MimeBodyPart corpo = new MimeBodyPart();
                corpo.setText("Segue arquivo(s) gerado(s) pelo inventário RFID.");
                multipart.addBodyPart(corpo);

                // Anexos
                for (File arquivo : arquivos) {
                    MimeBodyPart anexo = new MimeBodyPart();
                    anexo.setDataHandler(new DataHandler(new FileDataSource(arquivo)));
                    anexo.setFileName(arquivo.getName());
                    multipart.addBodyPart(anexo);
                }

                message.setContent(multipart);

                // ENVIO
                Transport.send(message);

                // 🔔 TOAST AQUI — no thread principal
                mainHandler.post(() -> Toast.makeText(
                        ListaHistoricoActivity.this,
                        "📨 E-mail enviado com sucesso!",
                        Toast.LENGTH_LONG
                ).show());

            } catch (Exception e) {

                mainHandler.post(() -> Toast.makeText(
                        ListaHistoricoActivity.this,
                        "❌ Erro ao enviar: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    // opcional: método rápido para compartilhar via intent usando FileProvider
    private void compartilharPdfViaIntent(File arquivo) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", arquivo);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Enviar PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao compartilhar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        emailExecutor.shutdown();
    }

    // ---- CLASSE INTERNA PARA ITENS DO RESUMO ----
    private static class ResumoItem {
        private final String descricao;
        private final int quantidade;

        ResumoItem(String descricao, int quantidade) {
            this.descricao = descricao;
            this.quantidade = quantidade;
        }

        String getDescricao() {
            return descricao;
        }

        int getQuantidade() {
            return quantidade;
        }
    }
}