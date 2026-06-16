package com.example.uhf.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PatrimonioActivity extends Activity {

    // Campos do formulário
    private EditText edtPat, edtDesc, edtCod;
    private TextView txtStatus;
    private LinearLayout btnSalvar, btnExibir, btnSincronizar;
    private DBHelper db;
    private boolean editando = false;

    // Views do overlay de sync
    private FrameLayout  overlaySync;
    private ProgressBar  progressSpinner, progressBar;
    private TextView     txtSyncTitulo, txtSyncContador;
    private TextView     txtSyncNovos, txtSyncDuplicados, txtSyncErros;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrimonio);

        db = new DBHelper(this);

        // Formulário
        edtPat         = findViewById(R.id.edtPat);
        edtDesc        = findViewById(R.id.edtDesc);
        edtCod         = findViewById(R.id.edtCod);
        txtStatus      = findViewById(R.id.txtDadosSalvos);
        btnSalvar      = findViewById(R.id.btnSalvar);
        btnExibir      = findViewById(R.id.btnExibir);
        btnSincronizar = findViewById(R.id.btnUpload);

        // Overlay
        overlaySync       = findViewById(R.id.overlaySync);
        progressSpinner   = findViewById(R.id.progressSpinner);
        progressBar       = findViewById(R.id.progressBar);
        txtSyncTitulo     = findViewById(R.id.txtSyncTitulo);
        txtSyncContador   = findViewById(R.id.txtSyncContador);
        txtSyncNovos      = findViewById(R.id.txtSyncNovos);
        txtSyncDuplicados = findViewById(R.id.txtSyncDuplicados);
        txtSyncErros      = findViewById(R.id.txtSyncErros);

        configurarCampoNumerico8(edtPat);
        configurarCampoNumerico8(edtCod);

        btnSalvar.setOnClickListener(v -> salvarPatrimonio());
        btnExibir.setOnClickListener(v -> abrirLista());
        btnSincronizar.setOnClickListener(v -> sincronizarPatrimonios());
    }

    // -------------------------------------------------------------------------
    // Campo numérico 8 dígitos
    // -------------------------------------------------------------------------

    private void configurarCampoNumerico8(EditText campo) {
        campo.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(8) });
        campo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (editando) return;
                editando = true;
                String somenteNumeros = s.toString().replaceAll("[^0-9]", "");
                if (!somenteNumeros.equals(s.toString())) {
                    campo.setText(somenteNumeros);
                    campo.setSelection(somenteNumeros.length());
                }
                editando = false;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Salvar cadastro manual
    // -------------------------------------------------------------------------

    private void salvarPatrimonio() {
        String pat  = edtPat.getText().toString().trim();
        String desc = edtDesc.getText().toString().trim();
        String cod  = edtCod.getText().toString().trim();

        if (pat.isEmpty() || desc.isEmpty() || cod.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pat.length() != 8) {
            Toast.makeText(this, "O número de patrimônio deve ter 8 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cod.length() != 8) {
            Toast.makeText(this, "O código RFID deve ter 8 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sucesso = db.inserirPatrimonio(pat, desc, cod);
        if (sucesso) {
            txtStatus.setText("Ativo salvo com sucesso!");
            edtPat.setText("");
            edtDesc.setText("");
            edtCod.setText("");
        } else {
            txtStatus.setText("Erro: código RFID já existe!");
        }
    }

    // -------------------------------------------------------------------------
    // Abrir lista
    // -------------------------------------------------------------------------

    private void abrirLista() {
        startActivity(new Intent(this, ListaPatrimoniosActivity.class));
    }

    // -------------------------------------------------------------------------
    // Overlay: mostrar / esconder
    // -------------------------------------------------------------------------

    private void mostrarOverlay() {
        // Reseta contadores
        progressBar.setProgress(0);
        txtSyncTitulo.setText("Sincronizando...");
        txtSyncContador.setText("Conectando ao servidor...");
        txtSyncNovos.setText("0");
        txtSyncDuplicados.setText("0");
        txtSyncErros.setText("0");

        overlaySync.setVisibility(View.VISIBLE);
    }

    private void esconderOverlay() {
        overlaySync.setVisibility(View.GONE);
    }

    private void atualizarOverlay(int processados, int total,
                                  int salvos, int duplicados, int erros) {
        // Barra de progresso 0–100
        int pct = total > 0 ? (processados * 100 / total) : 0;
        progressBar.setProgress(pct);

        txtSyncContador.setText(processados + " / " + total + " itens  (" + pct + "%)");
        txtSyncNovos.setText(String.valueOf(salvos));
        txtSyncDuplicados.setText(String.valueOf(duplicados));
        txtSyncErros.setText(String.valueOf(erros));
    }

    // -------------------------------------------------------------------------
    // Sincronizar com API TOTVS
    // -------------------------------------------------------------------------

    private void sincronizarPatrimonios() {
        String baseUrl = SettingsActivity.getBaseUrl(this);
        if (baseUrl == null || baseUrl.isEmpty()) {
            Toast.makeText(this,
                    "Configure a URL base em Configurações antes de sincronizar.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Abre o overlay
        mostrarOverlay();

        Handler handler = new Handler(Looper.getMainLooper());
        ExecutorService exec = Executors.newSingleThreadExecutor();

        exec.execute(() -> {

            // Callback: atualiza overlay a cada 50 itens
            SyncApiHelper.ProgressCallback progressCallback =
                    (processados, total, salvos, duplicados, erros) ->
                            handler.post(() ->
                                    atualizarOverlay(processados, total, salvos, duplicados, erros)
                            );

            // Executa sync em background
            SyncApiHelper.SyncResult result =
                    SyncApiHelper.sincronizar(this, db, progressCallback);

            // Resultado final na UI thread
            handler.post(() -> {

                // Atualiza overlay com 100% antes de fechar
                atualizarOverlay(
                        result.salvos + result.duplicados + result.erros,
                        result.salvos + result.duplicados + result.erros,
                        result.salvos, result.duplicados, result.erros
                );
                progressBar.setProgress(100);
                txtSyncTitulo.setText(
                        result.mensagem.startsWith("Erro") ? "⚠️ Erro!" : "✅ Concluído!"
                );
                txtSyncContador.setText(result.mensagem);

                // Fecha o overlay após 1,5 s para o usuário ver o resultado
                handler.postDelayed(() -> {
                    esconderOverlay();

                    // Atualiza o txtStatus da tela principal
                    boolean temErro = result.erros > 0 || result.mensagem.startsWith("Erro");
                    txtStatus.setText(
                            (temErro ? "⚠️ " : "✅ ") + result.mensagem + "\n" +
                                    "Novos salvos: "  + result.salvos     + "\n" +
                                    "Duplicados: "    + result.duplicados + "\n" +
                                    "Erros: "         + result.erros
                    );
                }, 1500);
            });
        });

        exec.shutdown();
    }
}