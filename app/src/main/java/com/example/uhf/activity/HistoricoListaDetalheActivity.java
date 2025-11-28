//ESSE AQUI E UM TESTE E NAO FUNCINOU EXCLUIR DPS
package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
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

    // RECEBIDOS DE OUTRA ACTIVITY
    private ArrayList<String> listaTags = new ArrayList<>();
    private String codigoFilial, codigoLocal, chapaFuncionario;
    private Object userBanco, localBanco;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico_lista_detalhe);

        // ---- BOTÕES ----
        btnResumo = findViewById(R.id.btnResumo);
        btnConcluir = findViewById(R.id.btnConcluir);
        btnIncluir = findViewById(R.id.btnIncluir);

        listHistoricoLocais = findViewById(R.id.listHistoricoLocais);

        // LIGA OS BOTÕES NAS FUNÇÕES ----
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnConcluir.setOnClickListener(v -> gerarArquivoTXT());
        btnIncluir.setOnClickListener(v -> Toast.makeText(this, "Adicionar item (implementar)", Toast.LENGTH_SHORT).show());

        // RECEBE DADOS SE TIVER
        receberDados();
    }

    private void receberDados() {
        Intent i = getIntent();

        if (i.hasExtra("tags")) {
            listaTags = i.getStringArrayListExtra("tags");
        }

        codigoFilial = i.getStringExtra("codigoFilial");
        codigoLocal = i.getStringExtra("codigoLocal");
        chapaFuncionario = i.getStringExtra("chapaFuncionario");
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

            java.text.SimpleDateFormat sdfData = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.text.SimpleDateFormat sdfHora = new java.text.SimpleDateFormat("HH-mm");

            String data = sdfData.format(new java.util.Date());
            String hora = sdfHora.format(new java.util.Date());

            String nomeArquivo = codigoLocal + "_" + data + "_" + hora + ".txt";

            File arquivo = new File(pasta, nomeArquivo);
            FileOutputStream fos = new FileOutputStream(arquivo);

            for (String epc : listaTags) {

                // ----- Ajuste para TXT -----
                String epcSemZero = epc.replaceFirst("^0+", "");
                String cincoDigitos = epcSemZero.length() >= 5 ? epcSemZero.substring(0, 5) : epcSemZero;

                while (cincoDigitos.length() < 5) {
                    cincoDigitos += "0";
                }

                String codigoBarra = "040" + cincoDigitos;

                String filialFmt = String.format("%03d", Integer.parseInt(codigoFilial));
                String localFmt = String.format("%04d", Integer.parseInt(codigoLocal));
                String matriculaFmt = String.format("%08d", Integer.parseInt(chapaFuncionario));

                String linha = filialFmt + " " + localFmt + "  " + matriculaFmt +
                        "                      " + codigoBarra + "\n";

                fos.write(linha.getBytes());
            }

            fos.close();
            Toast.makeText(this, "TXT gerado:\n" + arquivo.getAbsolutePath(), Toast.LENGTH_LONG).show();
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
                .setTitle("Ação Concluída")
                .setMessage("Deseja enviar o arquivo por e-mail?")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String emailDestino = inputEmail.getText().toString().trim();
                    if (!emailDestino.isEmpty()) enviarArquivoPorEmail(arquivo, emailDestino);
                    else Toast.makeText(this, "E-mail não informado!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Concluir", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    // -------------------------------------------------------------------
    // --------------------- ENVIO DE EMAIL SMTP -------------------------
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
                message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(destinatario));
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
                        Toast.makeText(this, "E-mail enviado com sucesso!", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Erro ao enviar e-mail: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}