package com.example.uhf.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME      = "AppSettings";
    private static final String KEY_AMBIENTE    = "ambiente";       // "homologacao" | "producao"
    private static final String KEY_URL_HOMOLOG = "url_homologacao";
    private static final String KEY_URL_PROD    = "url_producao";

    private static final String URL_HOMOLOGACAO_DEFAULT =
            "https://api-ipaas.totvs.app/sync-hook/api/v1/integrations/";

    private RadioGroup  rgAmbiente;
    private RadioButton rbHomologacao;
    private RadioButton rbProducao;
    private View        cardBaseUrl;
    private EditText    edtBaseUrl;
    private TextView    txtUrlInfo;
    private Button      btnSalvar;
    private TextView    txtDadosSalvos;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        bindViews();
        loadSavedData();
        setupListeners();
    }

    // -------------------------------------------------------------------------
    // Bind
    // -------------------------------------------------------------------------

    private void bindViews() {
        rgAmbiente     = findViewById(R.id.rgAmbiente);
        rbHomologacao  = findViewById(R.id.rbHomologacao);
        rbProducao     = findViewById(R.id.rbProducao);
        cardBaseUrl    = findViewById(R.id.cardBaseUrl);
        edtBaseUrl     = findViewById(R.id.edtBaseUrl);
        txtUrlInfo     = findViewById(R.id.txtUrlInfo);
        btnSalvar      = findViewById(R.id.btnSalvar);
        txtDadosSalvos = findViewById(R.id.txtDadosSalvos);
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    private void loadSavedData() {
        String ambiente   = prefs.getString(KEY_AMBIENTE, "homologacao");
        String urlHomolog = prefs.getString(KEY_URL_HOMOLOG, URL_HOMOLOGACAO_DEFAULT);
        String urlProd    = prefs.getString(KEY_URL_PROD, "");

        if ("producao".equals(ambiente)) {
            rbProducao.setChecked(true);
            edtBaseUrl.setText(urlProd);
            edtBaseUrl.setEnabled(true);
            txtUrlInfo.setVisibility(View.GONE);
        } else {
            rbHomologacao.setChecked(true);
            edtBaseUrl.setText(urlHomolog);
            edtBaseUrl.setEnabled(false);
            txtUrlInfo.setVisibility(View.VISIBLE);
        }

        // Só mostra o card se já havia algo salvo
        boolean temDados = !prefs.getString(KEY_AMBIENTE, "").isEmpty();
        cardBaseUrl.setVisibility(temDados ? View.VISIBLE : View.GONE);

        atualizarResumo(ambiente, ambiente.equals("homologacao") ? urlHomolog : urlProd);
    }

    // -------------------------------------------------------------------------
    // Listeners
    // -------------------------------------------------------------------------

    private void setupListeners() {

        rgAmbiente.setOnCheckedChangeListener((group, checkedId) -> {
            cardBaseUrl.setVisibility(View.VISIBLE);

            if (checkedId == R.id.rbHomologacao) {
                edtBaseUrl.setText(URL_HOMOLOGACAO_DEFAULT);
                edtBaseUrl.setEnabled(false);
                txtUrlInfo.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rbProducao) {
                String urlProd = prefs.getString(KEY_URL_PROD, "");
                edtBaseUrl.setText(urlProd);
                edtBaseUrl.setEnabled(true);
                txtUrlInfo.setVisibility(View.GONE);
                edtBaseUrl.requestFocus();
            }
        });

        btnSalvar.setOnClickListener(v -> salvarConfiguracoes());
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    private void salvarConfiguracoes() {
        boolean isHomolog = rbHomologacao.isChecked();
        String url = edtBaseUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "Informe a Base URL antes de salvar.", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();

        if (isHomolog) {
            editor.putString(KEY_AMBIENTE,    "homologacao");
            editor.putString(KEY_URL_HOMOLOG, url);
        } else {
            editor.putString(KEY_AMBIENTE,  "producao");
            editor.putString(KEY_URL_PROD,  url);
        }

        editor.apply();

        String ambiente = isHomolog ? "homologacao" : "producao";
        atualizarResumo(ambiente, url);

        Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------------------
    // UI helper
    // -------------------------------------------------------------------------

    private void atualizarResumo(String ambiente, String url) {
        String label = "homologacao".equals(ambiente) ? "Homologação" : "Produção";
        txtDadosSalvos.setText("Ambiente: " + label + "\nURL: " + url);
    }

    // -------------------------------------------------------------------------
    // Utility — chame este método em qualquer lugar do app para obter a URL ativa
    // -------------------------------------------------------------------------

    public static String getBaseUrl(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String ambiente = p.getString(KEY_AMBIENTE, "homologacao");
        if ("producao".equals(ambiente)) {
            return p.getString(KEY_URL_PROD, "");
        }
        return p.getString(KEY_URL_HOMOLOG, URL_HOMOLOGACAO_DEFAULT);
    }
}