package com.example.uhf.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.model.Patrimonio;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

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

/**
 * Helper reutilizável para o botão Concluir em todos os modos de inventário.
 *
 * Gera TXT no padrão TOTVS e PDF com capa SEBRAE + resumo agrupado por descrição.
 * Popup oferece: Gerar TXT / Gerar PDF / Só salvar.
 *
 * Uso — Leitura Livre (lista de Strings):
 *   ConcluirHelper.executar(activity, executor, codigoFilial, codigoLocal,
 *                           chapaFuncionario, "LIVRE", listaTags);
 *
 * Uso — Por Local / Por Categoria (lista de Patrimonio):
 *   ConcluirHelper.executarPatrimonios(activity, executor, codigoFilial,
 *                           codigoLocal, chapaFuncionario, "LOCAL", listaLidos);
 */
public class ConcluirHelper {

    private static final String TAG = "ConcluirHelper";

    private static final String EMAIL_REMETENTE = "smartmailbuilding@gmail.com";
    private static final String EMAIL_SENHA     = "ebzzwrvykwihempj";

    // =========================================================================
    // ENTRADAS PÚBLICAS
    // =========================================================================

    /** Para InventarioLivreActivity — tags como lista de String */
    public static void executar(Activity activity,
                                ExecutorService executor,
                                String codigoFilial,
                                String codigoLocal,
                                String chapaFuncionario,
                                String tipoInventario,
                                List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            Toast.makeText(activity, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Converte para lista de descrições agrupáveis (tag → busca no banco)
        DBHelper db = new DBHelper(activity);
        List<Patrimonio> patrimonios = new ArrayList<>();
        for (String epc : tags) {
            String norm = normalizarCodigo(epc);
            if (norm.length() < 5) continue;
            String cb   = "040" + norm.substring(0, 5);
            String desc = db.getDescricaoPorTag(cb);
            // Monta Patrimonio sintético só para o PDF
            patrimonios.add(new Patrimonio(0, cb, desc != null ? desc : "Sem descrição",
                    cb, "", "", codigoLocal, ""));
        }
        mostrarPopupAcao(activity, executor,
                codigoFilial, codigoLocal, chapaFuncionario,
                tipoInventario, tags, patrimonios);
    }

    /** Para InventarioLocalActivity / InventarioCategoriaActivity */
    public static void executarPatrimonios(Activity activity,
                                           ExecutorService executor,
                                           String codigoFilial,
                                           String codigoLocal,
                                           String chapaFuncionario,
                                           String tipoInventario,
                                           List<Patrimonio> lidosList) {
        if (lidosList == null || lidosList.isEmpty()) {
            Toast.makeText(activity, "Nenhum item lido ainda!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Converte para lista de Strings para o TXT
        List<String> tags = new ArrayList<>();
        for (Patrimonio p : lidosList) tags.add(p.getCodigoBarra());

        mostrarPopupAcao(activity, executor,
                codigoFilial, codigoLocal, chapaFuncionario,
                tipoInventario, tags, lidosList);
    }

    // =========================================================================
    // POPUP DE AÇÃO — escolhe TXT, PDF ou só salvar
    // =========================================================================

    private static void mostrarPopupAcao(Activity activity,
                                         ExecutorService executor,
                                         String codigoFilial,
                                         String codigoLocal,
                                         String chapaFuncionario,
                                         String tipoInventario,
                                         List<String> tags,
                                         List<Patrimonio> patrimonios) {
        new AlertDialog.Builder(activity)
                .setTitle("Ação Concluída")
                .setMessage("O que deseja fazer com os " + tags.size() + " item(s) lido(s)?")
                .setPositiveButton("Gerar TXT", (d, w) -> {
                    try {
                        File f = gerarTXT(activity, codigoFilial, codigoLocal,
                                chapaFuncionario, tipoInventario, tags);
                        mostrarPopupEmail(activity, executor, f,
                                "TXT gerado: " + f.getName());
                    } catch (Exception e) {
                        Toast.makeText(activity, "Erro TXT: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Gerar PDF", (d, w) -> {
                    try {
                        File f = gerarPDF(activity, codigoFilial, codigoLocal,
                                chapaFuncionario, tipoInventario, patrimonios);
                        mostrarPopupEmail(activity, executor, f,
                                "PDF gerado: " + f.getName());
                    } catch (Exception e) {
                        Toast.makeText(activity, "Erro PDF: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Só salvar", (d, w) -> {
                    try {
                        File f = gerarTXT(activity, codigoFilial, codigoLocal,
                                chapaFuncionario, tipoInventario, tags);
                        Toast.makeText(activity, "Arquivo salvo:\n" + f.getAbsolutePath(),
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(activity, "Erro: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setCancelable(false)
                .show();
    }

    // =========================================================================
    // POPUP DE EMAIL
    // =========================================================================

    private static void mostrarPopupEmail(Activity activity,
                                          ExecutorService executor,
                                          File arquivo,
                                          String mensagem) {
        EditText inputEmail = new EditText(activity);
        inputEmail.setHint("Digite o e-mail do destinatário");

        new AlertDialog.Builder(activity)
                .setTitle(mensagem)
                .setMessage("Deseja enviar o arquivo por e-mail?")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String email = inputEmail.getText().toString().trim();
                    if (!email.isEmpty()) enviarEmail(activity, executor, arquivo, email);
                    else Toast.makeText(activity, "E-mail não informado!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Não enviar", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    // =========================================================================
    // GERAÇÃO DO TXT
    // =========================================================================

    private static File gerarTXT(Activity activity,
                                 String codigoFilial, String codigoLocal,
                                 String chapaFuncionario, String tipoInventario,
                                 List<String> tags) throws Exception {
        File pasta = new File(activity.getExternalFilesDir(null), "export");
        if (!pasta.exists()) pasta.mkdirs();

        String dataHora = new SimpleDateFormat("dd-MM-yy_HH-mm").format(new Date());
        File arquivo = new File(pasta,
                codigoLocal + "_" + tipoInventario + "_" + dataHora + ".txt");
        FileOutputStream fos = new FileOutputStream(arquivo);

        for (String epc : tags) {
            String norm = normalizarCodigo(epc);
            if (norm.length() < 5) continue;
            if (!isValido(codigoFilial, 3) || !isValido(codigoLocal, 4)
                    || !isValido(chapaFuncionario, 8)) continue;

            String cb   = "040" + norm.substring(0, 5);
            String linha = String.format("%03d", Integer.parseInt(codigoFilial)) + " "
                    + String.format("%04d", Integer.parseInt(codigoLocal)) + "  "
                    + String.format("%08d", Integer.parseInt(chapaFuncionario))
                    + "                      " + cb + "\n";
            fos.write(linha.getBytes());
        }
        fos.close();
        return arquivo;
    }

    // =========================================================================
    // GERAÇÃO DO PDF — capa azul SEBRAE + resumo agrupado por descrição
    // =========================================================================

    private static File gerarPDF(Activity activity,
                                 String codigoFilial, String codigoLocal,
                                 String chapaFuncionario, String tipoInventario,
                                 List<Patrimonio> patrimonios) throws Exception {
        PdfDocument pdf = new PdfDocument();

        // ── Capa ─────────────────────────────────────────────────────────────
        PdfDocument.PageInfo capaInfo =
                new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page capaPage = pdf.startPage(capaInfo);
        Canvas canvas = capaPage.getCanvas();

        Paint pAzul   = new Paint(); pAzul.setColor(Color.rgb(0, 94, 184));
        Paint pTitulo = new Paint(); pTitulo.setColor(Color.WHITE);
        pTitulo.setTextSize(28f); pTitulo.setFakeBoldText(true);
        Paint pInfo   = new Paint(); pInfo.setColor(Color.WHITE); pInfo.setTextSize(17f);
        Paint pRodape = new Paint(); pRodape.setColor(Color.WHITE); pRodape.setTextSize(14f);

        canvas.drawRect(0, 0, 595, 842, pAzul);

        try {
            Bitmap logo = BitmapFactory.decodeResource(
                    activity.getResources(), R.drawable.ic_sebrae_branco);
            Bitmap logoBig = Bitmap.createScaledBitmap(logo, 260, 80, true);
            canvas.drawBitmap(logoBig, (595 - logoBig.getWidth()) / 2f, 120, null);
        } catch (Exception ignored) {}

        canvas.drawText("Relatório de Inventário RFID", 80, 260, pTitulo);

        int y = 320;
        canvas.drawText("Tipo: " + tipoInventario, 60, y, pInfo);            y += 30;
        canvas.drawText("Local: " + codigoLocal, 60, y, pInfo);              y += 30;
        canvas.drawText("Filial: " + codigoFilial, 60, y, pInfo);            y += 30;
        canvas.drawText("Matrícula: " + chapaFuncionario, 60, y, pInfo);     y += 30;
        canvas.drawText("Total de itens: " + patrimonios.size(), 60, y, pInfo); y += 30;
        canvas.drawText("Gerado em: " + new SimpleDateFormat(
                "dd/MM/yyyy HH:mm").format(new Date()), 60, y, pInfo);

        canvas.drawText("SEBRAE © " + new SimpleDateFormat("yyyy").format(new Date()),
                240, 820, pRodape);
        pdf.finishPage(capaPage);

        // ── Resumo agrupado por descrição ────────────────────────────────────
        // Agrupa
        HashMap<String, Integer> mapa = new HashMap<>();
        for (Patrimonio p : patrimonios) {
            String desc = (p.getDescricao() != null && !p.getDescricao().isEmpty())
                    ? p.getDescricao() : "DESCONHECIDO";
            mapa.put(desc, mapa.containsKey(desc) ? mapa.get(desc) + 1 : 1);
        }
        List<Map.Entry<String, Integer>> resumo = new ArrayList<>(mapa.entrySet());

        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(595, 842, 2).create();
        PdfDocument.Page page = pdf.startPage(pageInfo);
        canvas = page.getCanvas();

        Paint pTexto  = new Paint(); pTexto.setColor(Color.BLACK);  pTexto.setTextSize(15f);
        Paint pHeader = new Paint(); pHeader.setColor(Color.rgb(220, 220, 220));
        Paint pZebra  = new Paint(); pZebra.setColor(Color.rgb(245, 245, 245));
        Paint pTit    = new Paint(); pTit.setColor(Color.BLACK);
        pTit.setTextSize(20f); pTit.setFakeBoldText(true);
        Paint pLinha  = new Paint(); pLinha.setColor(Color.BLACK); pLinha.setStrokeWidth(1.5f);
        Paint pTotal  = new Paint(); pTotal.setColor(Color.BLACK);
        pTotal.setTextSize(17f); pTotal.setFakeBoldText(true);

        canvas.drawText("Resumo por Descrição", 170, 60, pTit);

        y = 100;
        // Cabeçalho da tabela
        canvas.drawRect(40, y - 22, 555, y + 8, pHeader);
        canvas.drawText("Qtd", 50, y, pTexto);
        canvas.drawText("Descrição", 130, y, pTexto);
        y += 20;
        canvas.drawLine(40, y, 555, y, pLinha);
        y += 18;

        boolean zebra = false;
        int paginaAtual = 2;
        int totalGeral  = 0;

        for (Map.Entry<String, Integer> entry : resumo) {
            if (zebra) canvas.drawRect(40, y - 16, 555, y + 10, pZebra);
            zebra = !zebra;

            String desc = entry.getKey();
            if (desc.length() > 50) desc = desc.substring(0, 50) + "...";

            canvas.drawText(String.valueOf(entry.getValue()), 50, y, pTexto);
            canvas.drawText(desc, 130, y, pTexto);
            totalGeral += entry.getValue();
            y += 26;

            // Nova página se necessário
            if (y > 760) {
                Paint rp = new Paint(); rp.setColor(Color.GRAY); rp.setTextSize(12f);
                canvas.drawText("Página " + paginaAtual, 500, 820, rp);
                pdf.finishPage(page);
                paginaAtual++;
                page = pdf.startPage(pageInfo);
                canvas = page.getCanvas();
                canvas.drawText("Resumo por Descrição (cont.)", 150, 60, pTit);
                y = 100;
                canvas.drawRect(40, y - 22, 555, y + 8, pHeader);
                canvas.drawText("Qtd", 50, y, pTexto);
                canvas.drawText("Descrição", 130, y, pTexto);
                y += 20;
                canvas.drawLine(40, y, 555, y, pLinha);
                y += 18;
            }
        }

        // Linha de total
        y += 8;
        canvas.drawLine(40, y, 555, y, pLinha);
        y += 26;
        canvas.drawText("TOTAL GERAL: " + totalGeral + " item(s)", 50, y, pTotal);

        Paint rpFinal = new Paint(); rpFinal.setColor(Color.GRAY); rpFinal.setTextSize(12f);
        canvas.drawText("Página " + paginaAtual, 500, 820, rpFinal);
        pdf.finishPage(page);

        // Salva
        File pasta = new File(activity.getExternalFilesDir(null), "export");
        if (!pasta.exists()) pasta.mkdirs();
        String dataHora = new SimpleDateFormat("dd-MM-yy_HH-mm").format(new Date());
        File arquivo = new File(pasta,
                codigoLocal + "_" + tipoInventario + "_" + dataHora + ".pdf");
        FileOutputStream fos = new FileOutputStream(arquivo);
        pdf.writeTo(fos);
        pdf.close();
        fos.close();
        return arquivo;
    }

    // =========================================================================
    // ENVIO POR SMTP
    // =========================================================================

    private static void enviarEmail(Activity activity,
                                    ExecutorService executor,
                                    File arquivo,
                                    String destinatario) {
        executor.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_REMETENTE, EMAIL_SENHA);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_REMETENTE));
                message.setRecipients(Message.RecipientType.TO,
                        InternetAddress.parse(destinatario));
                message.setSubject("Inventário RFID — " + arquivo.getName());

                MimeBodyPart corpo = new MimeBodyPart();
                corpo.setText("Segue em anexo o arquivo de inventário RFID.\n\nArquivo: "
                        + arquivo.getName());

                MimeBodyPart anexo = new MimeBodyPart();
                anexo.setDataHandler(new DataHandler(new FileDataSource(arquivo)));
                anexo.setFileName(arquivo.getName());

                MimeMultipart multipart = new MimeMultipart();
                multipart.addBodyPart(corpo);
                multipart.addBodyPart(anexo);
                message.setContent(multipart);
                Transport.send(message);

                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "E-mail enviado com sucesso!",
                                Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e(TAG, "Erro ao enviar email", e);
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Erro ao enviar: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    private static String normalizarCodigo(String valor) {
        if (valor == null) return "";
        String epc = valor.trim();
        if (epc.equalsIgnoreCase("null") || epc.isEmpty()) return "";
        if (epc.startsWith("040") && epc.length() > 3) epc = epc.substring(3);
        else if (epc.startsWith("40") && epc.length() > 2) epc = epc.substring(2);
        epc = epc.replaceFirst("^0+", "");
        return epc.isEmpty() ? "" : epc;
    }

    private static boolean isValido(String v, int max) {
        return v != null && !v.trim().isEmpty() && v.matches("\\d+") && v.length() <= max;
    }
}