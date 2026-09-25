package com.example.uhf.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
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
import java.util.Map;

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

    private ArrayList<ResumoItem> listaResumo =
            new ArrayList<>();

    private ArrayList<String> tagsRecebidas;
    private ArrayList<String> descricoesRecebidas;

    private ArrayList<Boolean> itensPertencemAoLocal;

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

        tagsRecebidas =
                getIntent().getStringArrayListExtra("tags");

        descricoesRecebidas =
                getIntent().getStringArrayListExtra("descricoes");

        itensPertencemAoLocal =
                (ArrayList<Boolean>) getIntent()
                        .getSerializableExtra("itensPertencemAoLocal");

        codigoFilial =
                getIntent().getStringExtra("codigoFilial");

        codigoLocal =
                getIntent().getStringExtra("codigoLocal");

        chapa =
                getIntent().getStringExtra("chapaFuncionario");

        nomeUsuario =
                getIntent().getStringExtra("nomeUsuario");

        nomeLocal =
                getIntent().getStringExtra("nomeLocal");

        if (tagsRecebidas == null ||
                tagsRecebidas.isEmpty()) {

            Toast.makeText(
                    this,
                    "Nenhuma tag recebida!",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        montarResumo();
    }

    private Uri uriFromFile(File file) {

        return FileProvider.getUriForFile(
                this,
                this.getPackageName() + ".provider",
                file
        );
    }

    // ============================================================
    // MONTA O RESUMO
    // ============================================================

    private void montarResumo() {

        HashMap<String, Integer> mapa =
                new HashMap<>();

        boolean temDescricoes =
                descricoesRecebidas != null
                        && descricoesRecebidas.size()
                        == tagsRecebidas.size();

        for (int i = 0;
             i < tagsRecebidas.size();
             i++) {

            String descricao;

            // ----------------------------------------------------
            // DESCRIÇÃO
            // ----------------------------------------------------

            if (temDescricoes) {

                descricao =
                        descricoesRecebidas.get(i);

                if (descricao == null ||
                        descricao.trim().isEmpty()) {

                    descricao = "DESCONHECIDO";
                }

            } else {

                String tagCompleta =
                        tagsRecebidas.get(i);

                String tag5 =
                        tagCompleta.length() >= 5
                                ? tagCompleta.substring(0, 5)
                                : tagCompleta;

                String tagBanco =
                        "040" + tag5;

                descricao =
                        db.getDescricaoPorTag(tagBanco);

                if (descricao == null ||
                        descricao.trim().isEmpty()) {

                    descricao = "DESCONHECIDO";
                }
            }

            // ----------------------------------------------------
            // PERTENCE AO LOCAL?
            // ----------------------------------------------------

            boolean pertenceAoLocal = true;

            if (itensPertencemAoLocal != null &&
                    i < itensPertencemAoLocal.size()) {

                Boolean valor =
                        itensPertencemAoLocal.get(i);

                if (valor != null) {
                    pertenceAoLocal = valor;
                }
            }

            // ----------------------------------------------------
            // CHAVE DO AGRUPAMENTO
            //
            // O mesmo item pertencente e fora do local
            // não será agrupado junto.
            // ----------------------------------------------------

            String chave =
                    (pertenceAoLocal ? "PERTENCE|" : "FORA|")
                            + descricao;

            if (mapa.containsKey(chave)) {

                mapa.put(
                        chave,
                        mapa.get(chave) + 1
                );

            } else {

                mapa.put(
                        chave,
                        1
                );
            }
        }

        listaResumo.clear();

        for (Map.Entry<String, Integer> entry :
                mapa.entrySet()) {

            String chave = entry.getKey();

            boolean pertenceAoLocal =
                    chave.startsWith("PERTENCE|");

            String descricao =
                    chave.substring(
                            chave.indexOf("|") + 1
                    );

            listaResumo.add(
                    new ResumoItem(
                            descricao,
                            entry.getValue(),
                            pertenceAoLocal
                    )
            );
        }

        // --------------------------------------------------------
        // Ordena: pertencentes primeiro, depois fora
        // --------------------------------------------------------

        listaResumo.sort(
                (a, b) -> {

                    if (a.isPertenceAoLocal()
                            && !b.isPertenceAoLocal()) {

                        return -1;
                    }

                    if (!a.isPertenceAoLocal()
                            && b.isPertenceAoLocal()) {

                        return 1;
                    }

                    return a.getDescricao()
                            .compareToIgnoreCase(
                                    b.getDescricao()
                            );
                }
        );

        listResumo.setAdapter(
                new ResumoAdapter(
                        this,
                        listaResumo
                )
        );

        btnExportarPDFPro.setOnClickListener(
                v -> exportarPDF_Profissional()
        );
    }

    // ============================================================
    // QUEBRA TEXTO SEM CORTAR
    // ============================================================

    private ArrayList<String> quebrarTexto(
            String texto,
            Paint paint,
            float larguraMaxima
    ) {

        ArrayList<String> linhas =
                new ArrayList<>();

        if (texto == null ||
                texto.trim().isEmpty()) {

            linhas.add("DESCONHECIDO");
            return linhas;
        }

        String[] palavras =
                texto.trim().split("\\s+");

        StringBuilder linhaAtual =
                new StringBuilder();

        for (String palavra : palavras) {

            String teste;

            if (linhaAtual.length() == 0) {

                teste = palavra;

            } else {

                teste =
                        linhaAtual +
                                " " +
                                palavra;
            }

            if (paint.measureText(teste)
                    <= larguraMaxima) {

                linhaAtual =
                        new StringBuilder(teste);

            } else {

                if (linhaAtual.length() > 0) {

                    linhas.add(
                            linhaAtual.toString()
                    );
                }

                linhaAtual =
                        new StringBuilder(palavra);
            }
        }

        if (linhaAtual.length() > 0) {

            linhas.add(
                    linhaAtual.toString()
            );
        }

        return linhas;
    }

    // ============================================================
    // EXPORTAR PDF
    // ============================================================

    private void exportarPDF_Profissional() {

        try {

            PdfDocument pdf =
                    new PdfDocument();

            // ====================================================
            // CAPA
            // ====================================================

            PdfDocument.PageInfo capaInfo =
                    new PdfDocument.PageInfo.Builder(
                            595,
                            842,
                            1
                    ).create();

            PdfDocument.Page capaPage =
                    pdf.startPage(capaInfo);

            Canvas canvas =
                    capaPage.getCanvas();

            Paint paintBlue =
                    new Paint();

            paintBlue.setColor(
                    Color.rgb(0, 94, 184)
            );

            Paint paintTitulo =
                    new Paint();

            paintTitulo.setColor(
                    Color.WHITE
            );

            paintTitulo.setTextSize(
                    32f
            );

            paintTitulo.setFakeBoldText(
                    true
            );

            Paint paintInfo =
                    new Paint();

            paintInfo.setColor(
                    Color.WHITE
            );

            paintInfo.setTextSize(
                    18f
            );

            canvas.drawRect(
                    0,
                    0,
                    595,
                    842,
                    paintBlue
            );

            Bitmap logoCapa =
                    BitmapFactory.decodeResource(
                            getResources(),
                            R.drawable.ic_sebrae_branco
                    );

            Bitmap logoGrande =
                    Bitmap.createScaledBitmap(
                            logoCapa,
                            280,
                            90,
                            true
                    );

            canvas.drawBitmap(
                    logoGrande,
                    (595 -
                            logoGrande.getWidth()) / 2,
                    120,
                    null
            );

            canvas.drawText(
                    "Relatório de Inventário Patrimonial",
                    50,
                    280,
                    paintTitulo
            );

            int yCapa = 360;

            canvas.drawText(
                    "Filial: " + codigoFilial,
                    60,
                    yCapa,
                    paintInfo
            );

            yCapa += 30;

            // ----------------------------------------------------
            // LOCAL DA CAPA
            // ----------------------------------------------------

            String textoLocal =
                    "Local: " +
                            nomeLocal +
                            " (" +
                            codigoLocal +
                            ")";

            ArrayList<String> linhasLocal =
                    quebrarTexto(
                            textoLocal,
                            paintInfo,
                            470
                    );

            for (String linha :
                    linhasLocal) {

                canvas.drawText(
                        linha,
                        60,
                        yCapa,
                        paintInfo
                );

                yCapa += 25;
            }

            yCapa += 5;

            canvas.drawText(
                    "Usuário: " +
                            nomeUsuario +
                            " - Matrícula: " +
                            chapa,
                    60,
                    yCapa,
                    paintInfo
            );

            yCapa += 30;

            String dataGeracao =
                    new SimpleDateFormat(
                            "dd/MM/yyyy HH:mm"
                    ).format(
                            new java.util.Date()
                    );

            canvas.drawText(
                    "Gerado em: " +
                            dataGeracao,
                    60,
                    yCapa,
                    paintInfo
            );

            Paint rodapeCapa =
                    new Paint();

            rodapeCapa.setColor(
                    Color.WHITE
            );

            rodapeCapa.setTextSize(
                    14f
            );

            canvas.drawText(
                    "SEBRAE © " +
                            new SimpleDateFormat(
                                    "yyyy"
                            ).format(
                                    new java.util.Date()
                            ),
                    230,
                    810,
                    rodapeCapa
            );

            pdf.finishPage(
                    capaPage
            );

            // ====================================================
            // PÁGINA DO RELATÓRIO
            // ====================================================

            int paginaAtual = 2;

            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(
                            595,
                            842,
                            paginaAtual
                    ).create();

            PdfDocument.Page page =
                    pdf.startPage(pageInfo);

            canvas =
                    page.getCanvas();

            Paint paintTexto =
                    new Paint();

            paintTexto.setColor(
                    Color.BLACK
            );

            paintTexto.setTextSize(
                    15f
            );

            Paint paintTituloTabela =
                    new Paint();

            paintTituloTabela.setColor(
                    Color.BLACK
            );

            paintTituloTabela.setTextSize(
                    22f
            );

            paintTituloTabela.setFakeBoldText(
                    true
            );

            Paint paintTituloSecao =
                    new Paint();

            paintTituloSecao.setColor(
                    Color.WHITE
            );

            paintTituloSecao.setTextSize(
                    16f
            );

            paintTituloSecao.setFakeBoldText(
                    true
            );

            Paint paintHeader =
                    new Paint();

            paintHeader.setColor(
                    Color.rgb(
                            220,
                            220,
                            220
                    )
            );

            Paint paintLinha =
                    new Paint();

            paintLinha.setColor(
                    Color.BLACK
            );

            paintLinha.setStrokeWidth(
                    2
            );

            Paint paintPertence =
                    new Paint();

            paintPertence.setColor(
                    Color.rgb(
                            220,
                            245,
                            220
                    )
            );

            Paint paintFora =
                    new Paint();

            paintFora.setColor(
                    Color.rgb(
                            255,
                            230,
                            230
                    )
            );

            Paint paintSecaoPertence =
                    new Paint();

            paintSecaoPertence.setColor(
                    Color.rgb(
                            40,
                            130,
                            70
                    )
            );

            Paint paintSecaoFora =
                    new Paint();

            paintSecaoFora.setColor(
                    Color.rgb(
                            190,
                            60,
                            60
                    )
            );

            int margemEsquerda = 40;
            int margemDireita = 555;

            int colQtd = 60;
            int colDesc = 150;

            float larguraDescricao =
                    margemDireita -
                            colDesc -
                            10;

            int y = 90;

            canvas.drawText(
                    "Resumo do Inventário",
                    180,
                    y,
                    paintTituloTabela
            );

            y += 40;

            // ====================================================
            // CONTADORES
            // ====================================================

            int totalGeral = 0;
            int totalPertencentes = 0;
            int totalFora = 0;

            for (ResumoItem item :
                    listaResumo) {

                totalGeral +=
                        item.getQuantidade();

                if (item.isPertenceAoLocal()) {

                    totalPertencentes +=
                            item.getQuantidade();

                } else {

                    totalFora +=
                            item.getQuantidade();
                }
            }

            Paint paintResumo =
                    new Paint();

            paintResumo.setColor(
                    Color.DKGRAY
            );

            paintResumo.setTextSize(
                    14f
            );

            canvas.drawText(
                    "Total: " + totalGeral +
                            " | Pertencentes: " +
                            totalPertencentes +
                            " | Fora do local: " +
                            totalFora,
                    40,
                    y,
                    paintResumo
            );

            y += 35;

            boolean secaoPertencenteAtual =
                    true;

            boolean primeiraSecao =
                    true;

            boolean zebra =
                    false;

            // ====================================================
            // ITENS
            // ====================================================

            for (ResumoItem item :
                    listaResumo) {

                boolean pertence =
                        item.isPertenceAoLocal();

                // ------------------------------------------------
                // NOVA SEÇÃO
                // ------------------------------------------------

                if (primeiraSecao ||
                        pertence != secaoPertencenteAtual) {

                    if (!primeiraSecao) {

                        y += 20;
                    }

                    secaoPertencenteAtual =
                            pertence;

                    primeiraSecao =
                            false;

                    Paint paintSecao =
                            pertence
                                    ? paintSecaoPertence
                                    : paintSecaoFora;

                    String tituloSecao =
                            pertence
                                    ? "ITENS PERTENCENTES AO LOCAL"
                                    : "ITENS FORA DO LOCAL";

                    canvas.drawRect(
                            margemEsquerda,
                            y - 20,
                            margemDireita,
                            y + 10,
                            paintSecao
                    );

                    canvas.drawText(
                            tituloSecao,
                            colDesc,
                            y,
                            paintTituloSecao
                    );

                    y += 35;

                    // Cabeçalho
                    canvas.drawRect(
                            margemEsquerda,
                            y - 20,
                            margemDireita,
                            y + 10,
                            paintHeader
                    );

                    canvas.drawText(
                            "Qtd",
                            colQtd,
                            y,
                            paintTexto
                    );

                    canvas.drawText(
                            "Descrição",
                            colDesc,
                            y,
                            paintTexto
                    );

                    y += 20;

                    canvas.drawLine(
                            margemEsquerda,
                            y,
                            margemDireita,
                            y,
                            paintLinha
                    );

                    y += 20;

                    zebra = false;
                }

                // ------------------------------------------------
                // DESCRIÇÃO
                // ------------------------------------------------

                String descricaoCompleta =
                        item.getDescricao();

                if (descricaoCompleta == null ||
                        descricaoCompleta.trim().isEmpty()) {

                    descricaoCompleta =
                            "DESCONHECIDO";
                }

                String[] partes =
                        descricaoCompleta.split(
                                "\\r?\\n"
                        );

                ArrayList<String> linhas =
                        new ArrayList<>();

                for (String parte :
                        partes) {

                    if (parte == null ||
                            parte.trim().isEmpty()) {

                        continue;
                    }

                    linhas.addAll(
                            quebrarTexto(
                                    parte.trim(),
                                    paintTexto,
                                    larguraDescricao
                            )
                    );
                }

                if (linhas.isEmpty()) {

                    linhas.add(
                            "DESCONHECIDO"
                    );
                }

                // ------------------------------------------------
                // ALTURA
                // ------------------------------------------------

                int alturaItem =
                        Math.max(
                                50,
                                (linhas.size() * 22) + 16
                        );

                // ------------------------------------------------
                // NOVA PÁGINA
                // ------------------------------------------------

                if (y + alturaItem > 760) {

                    Paint rodape =
                            new Paint();

                    rodape.setColor(
                            Color.GRAY
                    );

                    rodape.setTextSize(
                            12f
                    );

                    canvas.drawText(
                            "Página " +
                                    paginaAtual,
                            500,
                            820,
                            rodape
                    );

                    pdf.finishPage(
                            page
                    );

                    paginaAtual++;

                    page = pdf.startPage(
                            new PdfDocument.PageInfo.Builder(
                                    595,
                                    842,
                                    paginaAtual
                            ).create()
                    );

                    canvas =
                            page.getCanvas();

                    y = 60;

                    // --------------------------------------------
                    // Reexibe seção na nova página
                    // --------------------------------------------

                    Paint paintSecao =
                            pertence
                                    ? paintSecaoPertence
                                    : paintSecaoFora;

                    String tituloSecao =
                            pertence
                                    ? "ITENS PERTENCENTES AO LOCAL"
                                    : "ITENS FORA DO LOCAL";

                    canvas.drawRect(
                            margemEsquerda,
                            y - 20,
                            margemDireita,
                            y + 10,
                            paintSecao
                    );

                    canvas.drawText(
                            tituloSecao,
                            colDesc,
                            y,
                            paintTituloSecao
                    );

                    y += 35;

                    canvas.drawRect(
                            margemEsquerda,
                            y - 20,
                            margemDireita,
                            y + 10,
                            paintHeader
                    );

                    canvas.drawText(
                            "Qtd",
                            colQtd,
                            y,
                            paintTexto
                    );

                    canvas.drawText(
                            "Descrição",
                            colDesc,
                            y,
                            paintTexto
                    );

                    y += 40;
                }

                // ------------------------------------------------
                // FUNDO DO ITEM
                // ------------------------------------------------

                if (pertence) {

                    if (zebra) {

                        canvas.drawRect(
                                margemEsquerda,
                                y - 18,
                                margemDireita,
                                y + alturaItem - 8,
                                paintPertence
                        );
                    }

                } else {

                    canvas.drawRect(
                            margemEsquerda,
                            y - 18,
                            margemDireita,
                            y + alturaItem - 8,
                            paintFora
                    );
                }

                zebra = !zebra;

                // ------------------------------------------------
                // QUANTIDADE
                // ------------------------------------------------

                canvas.drawText(
                        String.valueOf(
                                item.getQuantidade()
                        ),
                        colQtd,
                        y,
                        paintTexto
                );

                // ------------------------------------------------
                // DESCRIÇÃO / LOCAL
                // ------------------------------------------------

                int yTexto = y;

                for (String linha :
                        linhas) {

                    canvas.drawText(
                            linha,
                            colDesc,
                            yTexto,
                            paintTexto
                    );

                    yTexto += 22;
                }

                y += alturaItem;
            }

            // ====================================================
            // TOTAL
            // ====================================================

            if (y > 750) {

                Paint rodape =
                        new Paint();

                rodape.setColor(
                        Color.GRAY
                );

                rodape.setTextSize(
                        12f
                );

                canvas.drawText(
                        "Página " +
                                paginaAtual,
                        500,
                        820,
                        rodape
                );

                pdf.finishPage(
                        page
                );

                paginaAtual++;

                page = pdf.startPage(
                        new PdfDocument.PageInfo.Builder(
                                595,
                                842,
                                paginaAtual
                        ).create()
                );

                canvas =
                        page.getCanvas();

                y = 70;
            }

            y += 20;

            canvas.drawLine(
                    margemEsquerda,
                    y,
                    margemDireita,
                    y,
                    paintLinha
            );

            y += 30;

            canvas.drawText(
                    "TOTAL GERAL DE ITENS: " +
                            totalGeral,
                    40,
                    y,
                    paintTituloTabela
            );

            y += 30;

            Paint paintTotal =
                    new Paint();

            paintTotal.setColor(
                    Color.rgb(
                            40,
                            130,
                            70
                    )
            );

            paintTotal.setTextSize(
                    15f
            );

            paintTotal.setFakeBoldText(
                    true
            );

            canvas.drawText(
                    "Pertencentes ao local: " +
                            totalPertencentes,
                    40,
                    y,
                    paintTotal
            );

            y += 25;

            Paint paintTotalFora =
                    new Paint();

            paintTotalFora.setColor(
                    Color.rgb(
                            190,
                            60,
                            60
                    )
            );

            paintTotalFora.setTextSize(
                    15f
            );

            paintTotalFora.setFakeBoldText(
                    true
            );

            canvas.drawText(
                    "Fora do local: " +
                            totalFora,
                    40,
                    y,
                    paintTotalFora
            );

            // ====================================================
            // RODAPÉ
            // ====================================================

            Paint rodapeFinal =
                    new Paint();

            rodapeFinal.setColor(
                    Color.GRAY
            );

            rodapeFinal.setTextSize(
                    12f
            );

            canvas.drawText(
                    "Página " +
                            paginaAtual,
                    500,
                    820,
                    rodapeFinal
            );

            pdf.finishPage(
                    page
            );

            // ====================================================
            // SALVAR
            // ====================================================

            File pasta =
                    new File(
                            getExternalFilesDir(null),
                            "export"
                    );

            if (!pasta.exists()) {
                pasta.mkdirs();
            }

            File arquivo =
                    new File(
                            pasta,
                            "Inventario_SEBRAE.pdf"
                    );

            FileOutputStream fos =
                    new FileOutputStream(
                            arquivo
                    );

            pdf.writeTo(fos);

            fos.close();

            pdf.close();

            Toast.makeText(
                    this,
                    "PDF exportado:\n" +
                            arquivo.getAbsolutePath(),
                    Toast.LENGTH_LONG
            ).show();

            mostrarPopupEnvioPDF(
                    arquivo
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Erro ao gerar PDF: " +
                            e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            e.printStackTrace();
        }
    }

    // ============================================================
    // POPUP E-MAIL
    // ============================================================

    private void mostrarPopupEnvioPDF(
            File arquivo
    ) {

        EditText inputEmail =
                new EditText(this);

        inputEmail.setHint(
                "Digite o e-mail do destinatário"
        );

        inputEmail.setInputType(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        new android.app.AlertDialog.Builder(this)

                .setTitle(
                        "PDF Gerado"
                )

                .setMessage(
                        "Deseja enviar o PDF por e-mail?"
                )

                .setView(
                        inputEmail
                )

                .setPositiveButton(
                        "Enviar",
                        (dialog, which) -> {

                            String emailDestino =
                                    inputEmail
                                            .getText()
                                            .toString()
                                            .trim();

                            if (!emailDestino.isEmpty()) {

                                enviarPDFPorEmail(
                                        arquivo,
                                        emailDestino
                                );

                            } else {

                                Toast.makeText(
                                        this,
                                        "E-mail não informado!",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )

                .setNegativeButton(
                        "Fechar",
                        (dialog, which) ->
                                dialog.dismiss()
                )

                .setCancelable(false)
                .show();
    }

    // ============================================================
    // ENVIO POR E-MAIL
    // ============================================================

    private void enviarPDFPorEmail(
            File arquivo,
            String destinatario
    ) {

        String usuario =
                "smartmailbuilding@gmail.com";

        // NÃO deixe a senha diretamente no código.
        // Use uma variável segura/configuração.
        String senha =
                "ebzzwrvykwihempj";

        String assunto =
                "Relatório de Inventário";

        String corpo =
                "Segue em anexo o PDF do inventário.";

        new Thread(() -> {

            try {

                java.util.Properties props =
                        new java.util.Properties();

                props.put(
                        "mail.smtp.host",
                        "smtp.gmail.com"
                );

                props.put(
                        "mail.smtp.socketFactory.port",
                        "465"
                );

                props.put(
                        "mail.smtp.socketFactory.class",
                        "javax.net.ssl.SSLSocketFactory"
                );

                props.put(
                        "mail.smtp.auth",
                        "true"
                );

                props.put(
                        "mail.smtp.port",
                        "465"
                );

                Session session =
                        Session.getDefaultInstance(
                                props,
                                new Authenticator() {

                                    @Override
                                    protected PasswordAuthentication
                                    getPasswordAuthentication() {

                                        return new PasswordAuthentication(
                                                usuario,
                                                senha
                                        );
                                    }
                                }
                        );

                MimeMessage message =
                        new MimeMessage(session);

                message.setFrom(
                        new javax.mail.internet.InternetAddress(
                                usuario
                        )
                );

                message.setRecipients(
                        javax.mail.Message.RecipientType.TO,
                        javax.mail.internet.InternetAddress.parse(
                                destinatario
                        )
                );

                message.setSubject(
                        assunto
                );

                MimeBodyPart texto =
                        new MimeBodyPart();

                texto.setText(
                        corpo
                );

                MimeBodyPart anexo =
                        new MimeBodyPart();

                anexo.setDataHandler(
                        new DataHandler(
                                new FileDataSource(
                                        arquivo
                                )
                        )
                );

                anexo.setFileName(
                        arquivo.getName()
                );

                MimeMultipart multipart =
                        new MimeMultipart();

                multipart.addBodyPart(
                        texto
                );

                multipart.addBodyPart(
                        anexo
                );

                message.setContent(
                        multipart
                );

                Transport.send(
                        message
                );

                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "E-mail enviado com sucesso!",
                                Toast.LENGTH_LONG
                        ).show()
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "Erro ao enviar e-mail: " +
                                        e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );

                e.printStackTrace();
            }

        }).start();
    }
}