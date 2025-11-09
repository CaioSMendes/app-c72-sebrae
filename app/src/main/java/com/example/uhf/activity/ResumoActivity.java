package com.example.uhf.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.uhf.R;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class ResumoActivity extends AppCompatActivity {

    private DBHelper db;
    private ListView listResumo;
    private LinearLayout btnExportarPDFPro;

    private ArrayList<ResumoItem> listaResumo = new ArrayList<>();
    private ArrayList<String> tagsRecebidas;

    private String codigoFilial;
    private String codigoLocal;
    private String chapa;
    private String nomeUsuario;
    private String nomeLocal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resumo);

        db = new DBHelper(this);

        listResumo = findViewById(R.id.listResumo);
        btnExportarPDFPro = findViewById(R.id.btnExportarPDFPro);

        tagsRecebidas = getIntent().getStringArrayListExtra("tags");

        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapa = getIntent().getStringExtra("chapaFuncionario");
        nomeUsuario = getIntent().getStringExtra("nomeUsuario");
        nomeLocal = getIntent().getStringExtra("nomeLocal");

        if (tagsRecebidas == null || tagsRecebidas.isEmpty()) {
            Toast.makeText(this, "Nenhuma tag recebida!", Toast.LENGTH_SHORT).show();
            return;
        }

        montarResumo();
    }

    private Uri uriFromFile(File file) {
        return FileProvider.getUriForFile(this, this.getPackageName() + ".provider", file);
    }

    private void montarResumo() {
        HashMap<String, Integer> mapa = new HashMap<>();

        for (String tagCompleta : tagsRecebidas) {
            String tag5 = tagCompleta.length() >= 5 ? tagCompleta.substring(0, 5) : tagCompleta;
            String tagBanco = "040" + tag5;

            String descricao = db.getDescricaoPorTag(tagBanco);
            if (descricao == null || descricao.trim().isEmpty())
                descricao = "DESCONHECIDO";

            if (mapa.containsKey(descricao))
                mapa.put(descricao, mapa.get(descricao) + 1);
            else
                mapa.put(descricao, 1);
        }

        listaResumo.clear();
        for (String desc : mapa.keySet()) {
            listaResumo.add(new ResumoItem(desc, mapa.get(desc)));
        }

        listResumo.setAdapter(new ResumoAdapter(this, listaResumo));
        btnExportarPDFPro.setOnClickListener(v -> exportarPDF_Profissional());
    }

    private void exportarPDF_Profissional() {
        try {
            PdfDocument pdf = new PdfDocument();

            // ----------------------------
            // Página 1 → CAPA
            // ----------------------------
            PdfDocument.PageInfo capaInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
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

            Bitmap logoCapa = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sebrae_branco);
            Bitmap logoGrande = Bitmap.createScaledBitmap(logoCapa, 280, 90, true);
            canvas.drawBitmap(logoGrande, (595 - logoGrande.getWidth()) / 2, 120, null);

            canvas.drawText("Relatório de Inventário Patrimonial", 50, 280, paintTitulo);

            int yCapa = 360;
            canvas.drawText("Filial: " + codigoFilial, 60, yCapa, paintInfo); yCapa += 30;
            canvas.drawText("Local: " + nomeLocal + " (" + codigoLocal + ")", 60, yCapa, paintInfo); yCapa += 30;
            canvas.drawText("Usuário: " + nomeUsuario + " - Matrícula: " + chapa, 60, yCapa, paintInfo); yCapa += 30;
            String dataGeracao = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
            canvas.drawText("Gerado em: " + dataGeracao, 60, yCapa, paintInfo);

            Paint rodapeCapa = new Paint();
            rodapeCapa.setColor(Color.WHITE);
            rodapeCapa.setTextSize(14f);
            canvas.drawText("SEBRAE © " + new SimpleDateFormat("yyyy").format(new java.util.Date()), 230, 810, rodapeCapa);

            pdf.finishPage(capaPage);

            // ----------------------------
            // Página 2 → CONTEÚDO (TABELA)
            // ----------------------------
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 2).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            canvas = page.getCanvas();

            int paginaAtual = 2;
            Paint paintTexto = new Paint();
            paintTexto.setColor(Color.BLACK);
            paintTexto.setTextSize(16f);

            Paint paintTituloTabela = new Paint();
            paintTituloTabela.setColor(Color.BLACK);
            paintTituloTabela.setTextSize(22f);
            paintTituloTabela.setFakeBoldText(true);

            Paint paintHeader = new Paint();
            paintHeader.setColor(Color.rgb(220, 220, 220));

            Paint paintLinha = new Paint();
            paintLinha.setColor(Color.BLACK);
            paintLinha.setStrokeWidth(2);

            Paint paintZebra = new Paint();
            paintZebra.setColor(Color.rgb(240, 240, 240));

            int y = 90;
            canvas.drawText("Resumo do Inventário", 180, y, paintTituloTabela);
            y += 40;

            int colQtd = 60;
            int colDesc = 150;

            canvas.drawRect(40, y - 20, 555, y + 10, paintHeader);
            canvas.drawText("Qtd", colQtd, y, paintTexto);
            canvas.drawText("Descrição", colDesc, y, paintTexto);
            y += 20;
            canvas.drawLine(40, y, 555, y, paintLinha);
            y += 20;

            int totalGeral = 0;
            boolean zebra = false;

            for (ResumoItem item : listaResumo) {
                if (zebra) canvas.drawRect(40, y - 18, 555, y + 10, paintZebra);
                zebra = !zebra;

                totalGeral += item.getQuantidade();
                String descricao = item.getDescricao();
                if (descricao.length() > 38) descricao = descricao.substring(0, 38) + "...";

                canvas.drawText(String.valueOf(item.getQuantidade()), colQtd, y, paintTexto);
                canvas.drawText(descricao, colDesc, y, paintTexto);
                y += 28;

                if (y > 760) {
                    Paint rodape = new Paint();
                    rodape.setColor(Color.GRAY);
                    rodape.setTextSize(12f);
                    canvas.drawText("Página " + paginaAtual, 500, 820, rodape);

                    pdf.finishPage(page);
                    paginaAtual++;

                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }
            }

            y += 20;
            canvas.drawLine(40, y, 555, y, paintLinha);
            y += 30;
            canvas.drawText("TOTAL GERAL DE ITENS: " + totalGeral, 40, y, paintTituloTabela);

            Paint rodapeFinal = new Paint();
            rodapeFinal.setColor(Color.GRAY);
            rodapeFinal.setTextSize(12f);
            canvas.drawText("Página " + paginaAtual, 500, 820, rodapeFinal);

            pdf.finishPage(page);

            // ----------------------------
            // Salvar PDF
            // ----------------------------
            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            File arquivo = new File(pasta, "Inventario_SEBRAE.pdf");
            FileOutputStream fos = new FileOutputStream(arquivo);
            pdf.writeTo(fos);
            fos.close();
            pdf.close();

            Toast.makeText(this, "PDF exportado:\n" + arquivo.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // ----------------------------
            // Mostrar popup para envio por e-mail
            // ----------------------------
            mostrarPopupEnvioPDF(arquivo);

        } catch (Exception e) {
            Toast.makeText(this, "Erro ao gerar PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarPopupEnvioPDF(File arquivo) {
        EditText inputEmail = new EditText(this);
        inputEmail.setHint("Digite o e-mail do destinatário");
        inputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        new android.app.AlertDialog.Builder(this)
                .setTitle("PDF Gerado")
                .setMessage("Deseja enviar o PDF por e-mail?")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String emailDestino = inputEmail.getText().toString().trim();
                    if (!emailDestino.isEmpty()) {
                        enviarPDFPorEmail(arquivo, emailDestino);
                    } else {
                        Toast.makeText(this, "E-mail não informado!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Fechar", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void enviarPDFPorEmail(File arquivo, String destinatario) {
        String usuario = "smartmailbuilding@gmail.com";
        String senha = "ebzzwrvykwihempj";
        String assunto = "Relatório de Inventário";
        String corpo = "Segue em anexo o PDF do inventário.";

        new Thread(() -> {
            try {
                java.util.Properties props = new java.util.Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getDefaultInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(usuario, senha);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new javax.mail.internet.InternetAddress(usuario));
                message.setRecipients(javax.mail.Message.RecipientType.TO,
                        javax.mail.internet.InternetAddress.parse(destinatario));
                message.setSubject(assunto);

                MimeBodyPart texto = new MimeBodyPart();
                texto.setText(corpo);

                MimeBodyPart anexo = new MimeBodyPart();
                anexo.setDataHandler(new DataHandler(new FileDataSource(arquivo)));
                anexo.setFileName(arquivo.getName());

                MimeMultipart multipart = new MimeMultipart();
                multipart.addBodyPart(texto);
                multipart.addBodyPart(anexo);

                message.setContent(multipart);
                Transport.send(message);

                runOnUiThread(() ->
                        Toast.makeText(this, "E-mail enviado com sucesso!", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Erro ao enviar e-mail: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
                e.printStackTrace();
            }
        }).start();
    }
}
