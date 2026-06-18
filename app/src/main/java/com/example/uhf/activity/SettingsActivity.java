package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME   = "AppSettings";
    private static final String KEY_AMBIENTE = "ambiente";

    private static final String KEY_CODCOLIGADA = "codcoligada";
    private static final String KEY_CODFILIAL   = "codfilial";
    private static final String KEY_ATIVO       = "ativo";
    private static final String KEY_API_KEY     = "api_key";

    // Endpoints fixos por ambiente
    private static final String BASE_URL         = "https://api-ipaas.totvs.app/sync-hook/api/v1/integrations/";
    private static final String ENDPOINT_HOMOLOG = "5ab32a7c-c8c1-43d3-b74c-1e12111e3161/execute";
    private static final String ENDPOINT_PROD    = "7c6d97f5-0e68-40b4-a571-38945a89ff4a/execute";

    // API Keys padrão por ambiente
    public static final String API_KEY_HOMOLOG = "26a979bf-63dc-46d1-b138-6af25138398a";
    public static final String API_KEY_PROD    = "a6e17557-3427-4a00-ac3d-a29e89b1a826";

    private RadioGroup  rgAmbiente;
    private RadioButton rbHomologacao;
    private RadioButton rbProducao;

    private EditText edtCodColigada;
    private EditText edtCodFilial;
    private EditText edtAtivo;
    private EditText edtApiKey;

    private TextView txtDadosSalvos;
    private Button   btnSalvar;

    private SharedPreferences prefs;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        bindViews();
        loadSavedData();
        setupListeners();
    }

    // =========================================================================
    // Bind
    // =========================================================================

    private void bindViews() {
        rgAmbiente    = findViewById(R.id.rgAmbiente);
        rbHomologacao = findViewById(R.id.rbHomologacao);
        rbProducao    = findViewById(R.id.rbProducao);

        edtCodColigada = findViewById(R.id.edtCodColigada);
        edtCodFilial   = findViewById(R.id.edtCodFilial);
        edtAtivo       = findViewById(R.id.edtAtivo);
        edtApiKey      = findViewById(R.id.edtApiKey);

        txtDadosSalvos = findViewById(R.id.txtDadosSalvos);
        btnSalvar      = findViewById(R.id.btnSalvar);
    }

    // =========================================================================
    // Load
    // =========================================================================

    private void loadSavedData() {
        String ambiente    = prefs.getString(KEY_AMBIENTE,    "homologacao");
        String codColigada = prefs.getString(KEY_CODCOLIGADA, "6");
        String codFilial   = prefs.getString(KEY_CODFILIAL,   "1");
        String ativo       = prefs.getString(KEY_ATIVO,       "1");

        // Carrega key salva; se não tiver, usa o padrão do ambiente
        String apiKeyPadrao = "producao".equals(ambiente) ? API_KEY_PROD : API_KEY_HOMOLOG;
        String apiKey = prefs.getString(KEY_API_KEY, apiKeyPadrao);

        if ("producao".equals(ambiente)) {
            rbProducao.setChecked(true);
        } else {
            rbHomologacao.setChecked(true);
        }

        edtCodColigada.setText(codColigada);
        edtCodFilial.setText(codFilial);
        edtAtivo.setText(ativo);
        edtApiKey.setText(apiKey);

        atualizarResumo(ambiente, codColigada, codFilial, ativo, apiKey);
    }

    // =========================================================================
    // Listeners
    // =========================================================================

    private void setupListeners() {

        // Ao trocar ambiente, preenche a API key padrão do ambiente automaticamente
        rgAmbiente.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbHomologacao) {
                edtApiKey.setText(API_KEY_HOMOLOG);
            } else if (checkedId == R.id.rbProducao) {
                edtApiKey.setText(API_KEY_PROD);
            }
        });

        btnSalvar.setOnClickListener(v -> salvarConfiguracoes());
    }

    // =========================================================================
    // Save
    // =========================================================================

    private void salvarConfiguracoes() {
        String ambienteAnterior = prefs.getString(KEY_AMBIENTE, "homologacao");

        boolean isHomolog   = rbHomologacao.isChecked();
        String  ambiente    = isHomolog ? "homologacao" : "producao";
        String  codColigada = edtCodColigada.getText().toString().trim();
        String  codFilial   = edtCodFilial.getText().toString().trim();
        String  ativo       = edtAtivo.getText().toString().trim();
        String  apiKey      = edtApiKey.getText().toString().trim();

        // Se o campo ficou vazio, usa o padrão do ambiente selecionado
        if (apiKey.isEmpty()) {
            apiKey = isHomolog ? API_KEY_HOMOLOG : API_KEY_PROD;
            edtApiKey.setText(apiKey);
        }

        prefs.edit()
                .putString(KEY_AMBIENTE,    ambiente)
                .putString(KEY_CODCOLIGADA, codColigada)
                .putString(KEY_CODFILIAL,   codFilial)
                .putString(KEY_ATIVO,       ativo)
                .putString(KEY_API_KEY,     apiKey)
                .apply();

        // Se trocou de ambiente, recria o DBHelper apontando pro banco correto
        if (!ambiente.equals(ambienteAnterior)) {
            DBHelper.resetInstance();
        }

        atualizarResumo(ambiente, codColigada, codFilial, ativo, apiKey);

        Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show();

        // Volta pra MainActivity com stack limpa
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Resumo
    // =========================================================================

    private void atualizarResumo(String ambiente, String codColigada,
                                 String codFilial, String ativo, String apiKey) {
        String label    = "homologacao".equals(ambiente) ? "Homologação" : "Produção";
        String endpoint = "homologacao".equals(ambiente) ? ENDPOINT_HOMOLOG : ENDPOINT_PROD;

        txtDadosSalvos.setText(
                "Ambiente: "    + label       + "\n" +
                        "Endpoint: "    + endpoint    + "\n" +
                        "CODCOLIGADA: " + codColigada + "\n" +
                        "CODFILIAL: "   + codFilial   + "\n" +
                        "ATIVO: "       + ativo       + "\n" +
                        "API Key: "     + apiKey
        );
    }

    // =========================================================================
    // Métodos utilitários estáticos
    // =========================================================================

    public static String getBaseUrl(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String ambiente = p.getString(KEY_AMBIENTE, "homologacao");
        String endpoint = "producao".equals(ambiente) ? ENDPOINT_PROD : ENDPOINT_HOMOLOG;
        return BASE_URL + endpoint;
    }

    public static String getApiKey(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String ambiente     = p.getString(KEY_AMBIENTE, "homologacao");
        String keyPadrao    = "producao".equals(ambiente) ? API_KEY_PROD : API_KEY_HOMOLOG;
        return p.getString(KEY_API_KEY, keyPadrao);
    }

    public static int getCodColigada(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return Integer.parseInt(p.getString(KEY_CODCOLIGADA, "1"));
    }

    public static int getCodFilial(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return Integer.parseInt(p.getString(KEY_CODFILIAL, "1"));
    }

    public static int getAtivo(android.content.Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return Integer.parseInt(p.getString(KEY_ATIVO, "1"));
    }
}