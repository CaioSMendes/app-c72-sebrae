package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class HistoricoListaDetalheActivity extends AppCompatActivity {

    private LinearLayout btnResumo, btnConcluir, btnIncluir;
    private ListView listHistoricoLocais;

    // ---- RECEBIDOS DE OUTRA ACTIVITY ----
    private ArrayList<String> listaTags = new ArrayList<>();
    private String codigoFilial = "0";
    private String codigoLocal = "0";
    private String chapaFuncionario = "0";

    private Object userBanco, localBanco;

    // 🔥 VARIÁVEL QUE FALTAVA — define se é RFID ou código de barras
    private boolean modoRfid = true; // altere conforme sua lógica real

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico_lista_detalhe);

        btnResumo = findViewById(R.id.btnResumo);
        btnConcluir = findViewById(R.id.btnConcluir);
        btnIncluir = findViewById(R.id.btnIncluir);
        listHistoricoLocais = findViewById(R.id.listHistoricoLocais);

        // eventos
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnConcluir.setOnClickListener(v -> gerarArquivoTXT());
        btnIncluir.setOnClickListener(v ->
                Toast.makeText(this, "Adicionar item (implementar)", Toast.LENGTH_SHORT).show()
        );

        receberDados();
    }

    private void receberDados() {
        Intent i = getIntent();

        if (i.hasExtra("tags")) listaTags = i.getStringArrayListExtra("tags");
        if (i.hasExtra("codigoFilial")) codigoFilial = i.getStringExtra("codigoFilial");
        if (i.hasExtra("codigoLocal")) codigoLocal = i.getStringExtra("codigoLocal");
        if (i.hasExtra("chapaFuncionario")) chapaFuncionario = i.getStringExtra("chapaFuncionario");
    }

    // -------------------------------------------------------------------
    // ---------------------------- RESUMO -------------------------------
    // -------------------------------------------------------------------

    private void abrirResumo() {
        if (listaTags.isEmpty()) {
            Toast.makeText(this, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ResumoActivity.class);
        intent.putStringArrayListExtra("tags", new ArrayList<>(listaTags));
        intent.putExtra("codigoFilial", codigoFilial);
        intent.putExtra("codigoLocal", codigoLocal);
        intent.putExtra("chapaFuncionario", chapaFuncionario);
        intent.putExtra("nomeUsuario", userBanco != null ? userBanco.toString() : "");
        intent.putExtra("nomeLocal", localBanco != null ? localBanco.toString() : "");

        startActivity(intent);
    }

    // -------------------------------------------------------------------
    // --------------------------- GERAR TXT -----------------------------
    // -------------------------------------------------------------------

    private void gerarArquivoTXT() {
        try {

            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd-MM-yy_HH-mm");
            String nomeArquivo = codigoLocal + "_" + sdfDataHora.format(new Date()) + ".txt";

            File arquivo = new File(pasta, nomeArquivo);
            FileOutputStream fos = new FileOutputStream(arquivo);

            for (String epc : listaTags) {

                // 🔥 Usa a tag exatamente como veio
                String codigoBarraFinal = epc;

                // formata campos
                String filialFmt = String.format("%03d", Integer.parseInt(codigoFilial));
                String localFmt = String.format("%04d", Integer.parseInt(codigoLocal));
                String matriculaFmt = String.format("%08d", Integer.parseInt(chapaFuncionario));

                String linha = filialFmt + " " + localFmt + "  " +
                        matriculaFmt + "                      " +
                        codigoBarraFinal + "\n";

                fos.write(linha.getBytes());
            }

            fos.close();

            Toast.makeText(this, "Arquivo gerado:\n" + arquivo.getAbsolutePath(), Toast.LENGTH_LONG).show();

            mostrarPopupEnvio(arquivo);

        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // -------------------------------------------------------------------
    // ------------------------ POPUP DE ENVIO ---------------------------
    // -------------------------------------------------------------------

    private void mostrarPopupEnvio(File arquivo) {
        android.widget.EditText inputEmail = new android.widget.EditText(this);
        inputEmail.setHint("Digite o e-mail do destinatário");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ação concluída")
                .setMessage("Deseja enviar o arquivo por e-mail?")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String email = inputEmail.getText().toString().trim();
                    if (!email.isEmpty()) enviarArquivoPorEmail(arquivo, email);
                    else Toast.makeText(this, "Digite um e-mail válido!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Fechar", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    // -------------------------------------------------------------------
    // --------------------- ENVIO DE E-MAIL SMTP ------------------------
    // -------------------------------------------------------------------

    private void enviarArquivoPorEmail(File arquivo, String destinatario) {
        String usuario = "smartmailbuilding@gmail.com";
        String senha = "ebzzwrvykwihempj";

        new Thread(() -> {
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
                message.setRecipients(MimeMessage.RecipientType.TO, destinatario);
                message.setSubject("Inventário RFID");

                MimeBodyPart texto = new MimeBodyPart();
                texto.setText("Segue em anexo o arquivo de inventário.");

                MimeBodyPart anexo = new MimeBodyPart();
                anexo.setDataHandler(new DataHandler(new FileDataSource(arquivo)));
                anexo.setFileName(arquivo.getName());

                MimeMultipart multipart = new MimeMultipart();
                multipart.addBodyPart(texto);
                multipart.addBodyPart(anexo);

                message.setContent(multipart);

                Transport.send(message);

                runOnUiThread(() ->
                        Toast.makeText(this, "E-mail enviado!", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Erro ao enviar e-mail: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}