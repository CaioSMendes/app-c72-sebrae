package com.example.uhf.activity;

import android.util.Log;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeUtility;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import java.io.File;

public class EmailSender {

    public static void enviarEmail(String destinatario, String assunto, String corpo, File arquivoAnexo, String usuario, String senha) {

        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getDefaultInstance(props,
                        new Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(usuario, senha);
                            }
                        });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(usuario));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
                message.setSubject(assunto);

                // Corpo do e-mail
                MimeBodyPart texto = new MimeBodyPart();
                texto.setText(corpo);

                // Anexo
                MimeBodyPart anexo = new MimeBodyPart();
                anexo.setDataHandler(new DataHandler(new FileDataSource(arquivoAnexo)));
                anexo.setFileName(MimeUtility.encodeText(arquivoAnexo.getName()));

                MimeMultipart multipart = new MimeMultipart();
                multipart.addBodyPart(texto);
                multipart.addBodyPart(anexo);

                message.setContent(multipart);

                Transport.send(message);

                Log.d("EmailSender", "E-mail enviado com sucesso!");

            } catch (Exception e) {
                Log.e("EmailSender", "Erro ao enviar e-mail: " + e.getMessage(), e);
            }
        }).start();
    }
}