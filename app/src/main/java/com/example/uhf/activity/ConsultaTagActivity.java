package com.example.uhf.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.barcode.BarcodeFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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


public class ConsultaTagActivity extends AppCompatActivity {

    private RFIDWithUHFUART mReader;
    private BarcodeDecoder barcodeDecoder;

    // Estados de controle otimizados
    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D = false;
    private volatile boolean modoRfid = true;
    private volatile boolean modo2D = false;

    // Threads dedicadas e isoladas
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler rfidHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService rfidExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGen;
    private List<String> listaTags = new ArrayList<>();
    private List<String> tagsLidas = new ArrayList<>();

    private SimpleTagAdapter adapter;
    private TextView tvTagCount;
    private ListView listViewTags;
    private LinearLayout btnLerTags, btnLimparTags, btnDistancia, btnHistorico, btnResumo, btnConcluir;
    private LinearLayout btnRfid, btnCodBar;
    private TextView txtBotao, txtInfoTopo, txtInfoUser, txtRfid, txtCodBar;
    private RadioButton rbLoop, rbSingle;
    private DBHelper dbHelper;
    private String codigoFilial, codigoLocal, chapaFuncionario;
    private Local localBanco;
    private Usuario userBanco;
    private long ultimoUpdateUI = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consulta_tag);

        inicializarComponentes();
        inicializarLeitores();
        configurarListeners();
    }

    private void inicializarComponentes() {
        dbHelper = new DBHelper(this);
        codigoFilial = getIntent().getStringExtra("codigoFilial");
        codigoLocal = getIntent().getStringExtra("codigoLocal");
        chapaFuncionario = getIntent().getStringExtra("chapaFuncionario");

        localBanco = dbHelper.buscarLocalPorCodigo(codigoLocal);
        userBanco = dbHelper.buscarUsuarioPorMatricula(chapaFuncionario);

        // Inicializar views
        tvTagCount = findViewById(R.id.tvTagCount);
        listViewTags = findViewById(R.id.listViewTags);
        btnLerTags = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnDistancia = findViewById(R.id.btnDistancia);
        btnResumo = findViewById(R.id.btnResumo);
        btnConcluir = findViewById(R.id.btnConcluir);
        btnRfid = findViewById(R.id.btnRfid);
        btnCodBar = findViewById(R.id.btnCodBar);
        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtInfoTopo = findViewById(R.id.txtInfoTopo);
        txtInfoUser = findViewById(R.id.txtInfoUser);
        txtRfid = findViewById(R.id.txtRfid);
        txtCodBar = findViewById(R.id.txtCodBar);
        rbLoop = findViewById(R.id.rbLoop);
        rbSingle = findViewById(R.id.rbSingle);
        btnHistorico = findViewById(R.id.btnHistorico);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        adapter = new SimpleTagAdapter(this, listaTags, dbHelper);
        listViewTags.setAdapter(adapter);

        txtInfoUser.setText(codigoFilial + " | " + codigoLocal + " | " + chapaFuncionario);
        txtInfoTopo.setText(localBanco != null && userBanco != null ?
                localBanco.getLocalNome() + " | " + userBanco.getNome() : "Dados não encontrados.");
    }

    private void inicializarLeitores() {
        // RFID em background
        rfidExecutor.execute(() -> inicializarRFID());

        // Barcode em background separado
        barcodeExecutor.execute(() -> inicializarBarcode2D());
    }

    private void inicializarRFID() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && mReader.init(this)) {
                mainHandler.post(() -> Toast.makeText(this, "Leitor RFID conectado!", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e("RFID", "Erro inicializando RFID", e);
        }
    }

    private void inicializarBarcode2D() {
        try {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            if (barcodeDecoder.open(this)) {
                configurarCallback2D();
            } else {
                mainHandler.post(() -> Toast.makeText(this, "Falha ao abrir leitor 2D", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e("Barcode", "Erro inicializando 2D", e);
        }
    }

    private void configurarCallback2D() {
        barcodeDecoder.setDecodeCallback(barcodeEntity -> {
            if (barcodeEntity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) return;

            String code = barcodeEntity.getBarcodeData();
            rfidExecutor.execute(() -> adicionarTagSegura(code));
        });
    }

    private void configurarListeners() {
        rbLoop.setOnCheckedChangeListener((b, c) -> {/* modoSingle = false; */});
        rbSingle.setOnCheckedChangeListener((b, c) -> {/* modoSingle = true; */});

        btnLerTags.setOnClickListener(v -> alternarLeituraPrincipal());
        btnLimparTags.setOnClickListener(v -> limparTags());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnResumo.setOnClickListener(v -> abrirResumo());
        btnConcluir.setOnClickListener(v -> gerarArquivoTXT());
        btnHistorico.setOnClickListener(v -> abrirHistorico());

        // Botões de modo - ISOLAMENTO TOTAL
        btnRfid.setOnClickListener(v -> trocarModo(true));
        btnCodBar.setOnClickListener(v -> trocarModo(false));
    }

    private void abrirHistorico() {
        Intent intent = new Intent(this, HistoricoActivity.class);
        intent.putExtra("codigoFilial", codigoFilial);
        intent.putExtra("codigoLocal", codigoLocal);
        intent.putExtra("chapaFuncionario", chapaFuncionario);
        startActivity(intent);
    }


    private void trocarModo(boolean paraRfid) {
        if (paraRfid) {
            modoRfid = true;
            modo2D = false;
            pararLeitura2D();
        } else {
            modoRfid = false;
            modo2D = true;
            pararLeituraRFID();
        }
        atualizarEstadoBotoes();
    }

    private void alternarLeituraPrincipal() {
        if (modoRfid) {
            if (isReadingRFID) pararLeituraRFID();
            else iniciarLeituraRFID();
        } else {
            if (isReading2D) pararLeitura2D();
            else iniciarLeitura2D();
        }
    }

    // ========== RFID OTIMIZADO ==========
    private void iniciarLeituraRFID() {
        if (isReadingRFID) return;
        isReadingRFID = true;

        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));

        rfidExecutor.execute(() -> {
            try {
                mReader.startInventoryTag();
                executarLoopRFID();
            } catch (Exception e) {
                Log.e("RFID", "Erro iniciando RFID", e);
                pararLeituraRFID();
            }
        });
    }

    private void executarLoopRFID() {
        rfidExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isReadingRFID) return;

                try {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                    if (tagInfo != null) {
                        String epc = tagInfo.getEPC();
                        adicionarTagSegura(formatarEPCExibicao(epc));
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                    }
                } catch (Exception e) {
                    Log.e("RFID", "Erro loop RFID", e);
                }

                if (isReadingRFID) {
                    mainHandler.postDelayed(this, 80);
                }
            }
        });
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        mainHandler.post(() -> txtBotao.setText("Ler Tags"));

        rfidExecutor.execute(() -> {
            try {
                if (mReader != null) mReader.stopInventory();
            } catch (Exception e) {
                Log.e("RFID", "Erro parando RFID", e);
            }
        });
    }

    // ========== BARCODE OTIMIZADO ==========
    private void iniciarLeitura2D() {
        if (isReading2D || barcodeDecoder == null) return;
        isReading2D = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));

        try {
            barcodeDecoder.startScan();
        } catch (Exception e) {
            Log.e("Barcode", "Erro iniciando 2D", e);
            isReading2D = false;
        }
    }

    private void pararLeitura2D() {
        if (barcodeDecoder == null) return;
        isReading2D = false;
        mainHandler.post(() -> txtBotao.setText("Ler Tags"));

        try {
            barcodeDecoder.stopScan();
        } catch (Exception e) {
            Log.e("Barcode", "Erro parando 2D", e);
        }
    }

    // ========== UTILITÁRIOS THREAD-SAFE ==========
    private synchronized void adicionarTagSegura(String tag) {
        long agora = System.currentTimeMillis();
        if (agora - ultimoUpdateUI < 100) return;  // throttle de UI
        ultimoUpdateUI = agora;

        if (!tagsLidas.contains(tag) && !listaTags.contains(tag)) {
            tagsLidas.add(tag);
            listaTags.add(tag);

            //SALVA NO HISTÓRICO AUTOMATICAMENTE
            dbHelper.salvarHistorico(
                    codigoFilial,      // → filial atual selecionada
                    codigoLocal,       // → local atual selecionado
                    chapaFuncionario,  // → matrícula do funcionário
                    tag,               // → tag lida
                    modoRfid ? "RFID" : "CODBARRAS"  // tipo da leitura
            );

            // Atualiza UI
            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                tvTagCount.setText("Tags lidas: " + listaTags.size());
            });
        }
    }


    private String formatarEPCExibicao(String epc) {
        String semZero = epc.replaceFirst("^0+", "");
        String resultado = semZero.length() >= 8 ? semZero.substring(0, 8) : semZero;
        while (resultado.length() < 8) resultado += "0";
        return resultado;
    }

    private void atualizarEstadoBotoes() {
        mainHandler.post(() -> {
            if (modoRfid) {
                btnRfid.setBackgroundTintList(ColorStateList.valueOf(0xFF0288D1));
                txtRfid.setTextColor(Color.WHITE);
                btnCodBar.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
                txtCodBar.setTextColor(Color.BLACK);
            } else {
                btnCodBar.setBackgroundTintList(ColorStateList.valueOf(0xFF388E3C));
                txtCodBar.setTextColor(Color.WHITE);
                btnRfid.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
                txtRfid.setTextColor(Color.BLACK);
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293;
        if (event.getKeyCode() == triggerKeyCode && event.getAction() == KeyEvent.ACTION_DOWN) {
            alternarLeituraPrincipal();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void limparTags() {
        pararLeituraRFID();
        pararLeitura2D();
        synchronized (this) {
            tagsLidas.clear();
            listaTags.clear();
            adapter.notifyDataSetChanged();
            tvTagCount.setText("Tags lidas: 0");
        }
        Toast.makeText(this, "Lista limpa!", Toast.LENGTH_SHORT).show();
    }

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    rfidExecutor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power)) {
                                mainHandler.post(() ->
                                        Toast.makeText(this, "Potência: " + power + " dBm", Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            Log.e("RFID", "Erro potência", e);
                        }
                    });
                }).show();
    }

    // Mantém os métodos abrirResumo() e gerarArquivoTXT() originais...
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pararLeituraRFID();
        pararLeitura2D();
        rfidExecutor.shutdown();
        barcodeExecutor.shutdown();
        try {
            if (barcodeDecoder != null) barcodeDecoder.close();
        } catch (Exception ignored) {}
    }

    private void gerarArquivoTXT() {
        try {
            // 1️⃣ Pasta de export
            File pasta = new File(getExternalFilesDir(null), "export");
            if (!pasta.exists()) pasta.mkdirs();

            // 2️⃣ Nome do arquivo no formato desejado: <codigoLocal>_dd-MM-yy_HH-mm.txt
            SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd-MM-yy_HH-mm");
            String dataHora = sdfDataHora.format(new Date());
            String nomeArquivo = codigoLocal + "_" + dataHora + ".txt";

            File arquivo = new File(pasta, nomeArquivo);

            // 3️⃣ Criar conteúdo do arquivo
            FileOutputStream fos = new FileOutputStream(arquivo);
            for (String epc : listaTags) {

                String epcSemZero = epc.replaceFirst("^0+", "");
                String cincoDigitos = epcSemZero.length() >= 5 ? epcSemZero.substring(0, 5) : epcSemZero;
                while (cincoDigitos.length() < 5) cincoDigitos += "0";

                String codigoBarra = "040" + cincoDigitos;

                String filialFmt = String.format("%03d", Integer.parseInt(codigoFilial));
                String localFmt = String.format("%04d", Integer.parseInt(codigoLocal));
                String matriculaFmt = String.format("%08d", Integer.parseInt(chapaFuncionario));

                String linha = filialFmt + " " + localFmt + "  " + matriculaFmt +
                        "                      " + codigoBarra + "\n";

                fos.write(linha.getBytes());
            }
            fos.close();

            // 4️⃣ Mostrar mensagem de sucesso
            Toast.makeText(this, "TXT gerado:\n" + arquivo.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // 5️⃣ Mostrar popup para envio de e-mail (mesmo arquivo)
            mostrarPopupEnvio(arquivo);

        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

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

    private void enviarArquivoPorEmail(File arquivo, String destinatario) {
        String usuario = "smartmailbuilding@gmail.com";
        String senha = "ebzzwrvykwihempj";

        rfidExecutor.execute(() -> {
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
                        return new PasswordAuthentication(usuario, senha);  // ✅ CORRETO
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(usuario));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
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

                mainHandler.post(() ->
                        Toast.makeText(ConsultaTagActivity.this, "E-mail enviado com sucesso!", Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(ConsultaTagActivity.this, "Erro ao enviar e-mail: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }
}