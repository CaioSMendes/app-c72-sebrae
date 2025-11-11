package com.example.uhf.activity;

import android.content.Intent;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.adapter.SimpleTagAdapter;
import com.example.uhf.model.Local;
import com.example.uhf.model.Usuario;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class ConsultaTagActivity extends AppCompatActivity {

    private static final String TAG = "ConsultaTagActivity";

    private RFIDWithUHFUART mReader;
    private boolean isReading = false;

    private final Handler handler = new Handler();
    private final Handler readerHandler = new Handler();
    private Runnable leituraRunnable;

    private ToneGenerator toneGen;

    private List<String> tagsLidas = new ArrayList<>();
    private List<String> listaTags = new ArrayList<>();
    private SimpleTagAdapter adapter;

    private TextView tvTagCount;
    private ListView listViewTags;
    private LinearLayout btnLerTags, btnLimparTags, btnDistancia, btnResumo, btnConcluir;
    private TextView txtBotao;
    private TextView txtInfoTopo, txtInfoUser;

    private RadioButton rbLoop, rbSingle;
    private boolean modoSingle = false;

    private DBHelper dbHelper;

    private String codigoFilial;
    private String codigoLocal;
    private String chapaFuncionario;
    private Local localBanco;
    private Usuario userBanco;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_tag);

        dbHelper = new DBHelper(this);

        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        tvTagCount = findViewById(R.id.tvTagCount);
        listViewTags = findViewById(R.id.listViewTags);
        btnLerTags = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnDistancia = findViewById(R.id.btnDistancia);
        btnResumo = findViewById(R.id.btnResumo);
        btnConcluir = findViewById(R.id.btnConcluir);

        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtInfoTopo = findViewById(R.id.txtInfoTopo);
        txtInfoUser = findViewById(R.id.txtInfoUser);

        rbLoop = findViewById(R.id.rbLoop);
        rbSingle = findViewById(R.id.rbSingle);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        adapter = new SimpleTagAdapter(this, listaTags, dbHelper);
        listViewTags.setAdapter(adapter);

        rbLoop.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = false;
        });

        rbSingle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = true;
        });

        btnResumo.setOnClickListener(v -> abrirResumo());
        btnConcluir.setOnClickListener(v -> gerarArquivoTXT());
        btnLimparTags.setOnClickListener(v -> limparTags());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);

        if (localBanco != null && userBanco != null)
            txtInfoTopo.setText(localBanco.getLocalNome() + " | " + userBanco.getNome());
        else
            txtInfoTopo.setText("Dados não encontrados.");

        inicializarLeitor();

        btnLerTags.setOnClickListener(v -> {
            if (isReading) pararLeitura();
            else iniciarLeitura();
        });
    }

    private void inicializarLeitor() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && mReader.init(this)) {
                Toast.makeText(this, "Leitor conectado!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Falha ao conectar leitor!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro inicializando leitor", e);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293;
        if (event.getKeyCode() == triggerKeyCode) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (modoSingle) {
                    if (!isReading) {
                        iniciarLeitura();
                        handler.postDelayed(this::pararLeitura, 250);
                    }
                } else {
                    if (!isReading) iniciarLeitura();
                    else pararLeitura();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void iniciarLeitura() {
        isReading = true;
        txtBotao.setText("Parar Leitura");
        tagsLidas.clear();
        listaTags.clear();
        adapter.notifyDataSetChanged();
        tvTagCount.setText("Tags lidas: 0");

        readerHandler.post(() -> {
            try {
                mReader.startInventoryTag();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar inventário", e);
            }
        });

        leituraRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isReading) return;
                try {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                    if (tagInfo != null) {
                        String epc = tagInfo.getEPC();

                        if (!tagsLidas.contains(epc)) {
                            tagsLidas.add(epc);

                            // REGRA FINAL: pegar 5 primeiros dígitos e montar "040xxxxx"
                            String numeros = epc.replaceAll("[^0-9]", "");

                            if (numeros.length() < 5)
                                return;

                            String cinco = numeros.substring(0, 5);

                            String epcCorrigido = "040" + cinco;

                            if (epcCorrigido.length() != 8)
                                return;

                            listaTags.add(epcCorrigido);

                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

                            runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                tvTagCount.setText("Tags lidas: " + listaTags.size());
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro leitura tag", e);
                }
                handler.postDelayed(this, 80);
            }
        };
        handler.post(leituraRunnable);
    }

    private void pararLeitura() {
        isReading = false;
        txtBotao.setText("Ler Tags");
        handler.removeCallbacks(leituraRunnable);

        readerHandler.post(() -> {
            try { mReader.stopInventory(); }
            catch (Exception e) { Log.e(TAG, "Erro parar leitura", e); }
        });
    }

    private void limparTags() {
        if (isReading) pararLeitura();
        tagsLidas.clear();
        listaTags.clear();
        adapter.notifyDataSetChanged();
        tvTagCount.setText("Tags lidas: 0");
        Toast.makeText(this, "Lista limpa!", Toast.LENGTH_SHORT).show();
    }

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    try {
                        if (mReader.setPower(power)) {
                            Toast.makeText(this, "Potência ajustada: " + power, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

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
        intent.putExtra("nomeUsuario", userBanco != null ? userBanco.getNome() : "");
        intent.putExtra("nomeLocal", localBanco != null ? localBanco.getLocalNome() : "");
        startActivity(intent);
    }

    private void gerarArquivoTXT() {
        if (listaTags.isEmpty()) {
            Toast.makeText(this, "Nenhuma tag lida!", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            String local = codigoLocal;

            java.text.SimpleDateFormat sdfData = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.text.SimpleDateFormat sdfHora = new java.text.SimpleDateFormat("HH-mm");

            String data = sdfData.format(new java.util.Date());
            String hora = sdfHora.format(new java.util.Date());

            String nomeArquivo = local + "_" + data + "_" + hora + ".txt";

            File arquivo = new File(pasta, nomeArquivo);
            FileOutputStream fos = new FileOutputStream(arquivo);

            for (String tag5 : listaTags) {
                String codigoBarra = tag5;

                String filialFmt = String.format("%03d", Integer.parseInt(codigoFilial));
                String localFmt = String.format("%04d", Integer.parseInt(codigoLocal));
                String matriculaFmt = String.format("%08d", Integer.parseInt(chapaFuncionario));

                String linha = filialFmt + " " + localFmt + "  " + matriculaFmt
                        + "                      " + codigoBarra + "\n";

                fos.write(linha.getBytes());
            }

            fos.close();
            Toast.makeText(this, "TXT gerado:\n" + arquivo.getAbsolutePath(), Toast.LENGTH_LONG).show();

            mostrarPopupEnvio(arquivo);

        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarPopupEnvio(File arquivo) {
        android.widget.EditText inputEmail = new android.widget.EditText(this);
        inputEmail.setHint("Digite o e-mail do destinatário");
        inputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Ação Concluída")
                .setMessage("Deseja enviar o arquivo por e-mail?")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String emailDestino = inputEmail.getText().toString().trim();
                    if (!emailDestino.isEmpty()) {
                        enviarArquivoPorEmail(arquivo, emailDestino);
                    } else {
                        Toast.makeText(this, "E-mail não informado!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Concluir", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void enviarArquivoPorEmail(File arquivo, String destinatario) {
        String usuario = "smartmailbuilding@gmail.com";
        String senha = "ebzzwrvykwihempj";
        String assunto = "Inventário RFID";
        String corpo = "Segue em anexo o arquivo de inventário.";

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mReader != null) mReader.free();
        } catch (Exception ignored) {}
    }
}