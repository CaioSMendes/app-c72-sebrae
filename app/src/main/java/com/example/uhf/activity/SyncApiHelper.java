package com.example.uhf.activity;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SyncApiHelper {

    private static final String TAG = "SyncApiHelper";

    public interface ProgressCallback {
        void onProgress(int processados, int total, int salvos, int duplicados, int erros);
    }

    public static class SyncResult {
        public int    salvos     = 0;
        public int    duplicados = 0;
        public int    erros      = 0;
        public String mensagem   = "";
    }

    public static SyncResult sincronizar(Context context, DBHelper db) {
        return sincronizar(context, db, null);
    }

    public static SyncResult sincronizar(Context context, DBHelper db,
                                         ProgressCallback progressCallback) {
        SyncResult result = new SyncResult();

        // getBaseUrl() já retorna a URL completa com o endpoint do ambiente ativo
        String fullUrl = SettingsActivity.getBaseUrl(context);
        if (fullUrl == null || fullUrl.isEmpty()) {
            result.mensagem = "Base URL não configurada. Acesse Configurações.";
            return result;
        }

        Log.d(TAG, "POST → " + fullUrl);

        try {
            URL url = new URL(fullUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apiKey", SettingsActivity.getApiKey(context));
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);

            // Parâmetros vindos das SharedPreferences
            JSONObject body = new JSONObject();
            body.put("CODCOLIGADA", SettingsActivity.getCodColigada(context));
            body.put("CODFILIAL",   SettingsActivity.getCodFilial(context));
            body.put("ATIVO",       SettingsActivity.getAtivo(context));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int httpStatus = conn.getResponseCode();
            Log.d(TAG, "HTTP " + httpStatus);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    httpStatus < 400 ? conn.getInputStream() : conn.getErrorStream(),
                    "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            if (httpStatus < 200 || httpStatus >= 300) {
                result.mensagem = "Erro HTTP " + httpStatus + ": " + sb;
                return result;
            }

            JSONObject responseJson = new JSONObject(sb.toString());
            JSONArray  items        = responseJson.optJSONArray("result");

            if (items == null || items.length() == 0) {
                result.mensagem = "Nenhum item retornado pela API.";
                return result;
            }

            // Loga o primeiro item completo para diagnóstico
            JSONObject primeiroItem = items.getJSONObject(0);
            Log.d(TAG, "Item[0] completo: " + primeiroItem.toString());

            int total = items.length();
            Log.d(TAG, "Total de itens: " + total);

            SQLiteDatabase sqlDB = db.getWritableDatabase();
            sqlDB.beginTransaction();

            try {
                for (int i = 0; i < total; i++) {
                    try {
                        JSONObject item = items.getJSONObject(i);

                        String patrimonio    = lerCampoComoString(item, "PATRIMONIO").trim();
                        String codigoBarra   = lerCampoComoString(item, "CODIGOBARRA").trim();
                        String descricao     = lerCampoComoString(item, "DESCRICAO").trim();
                        String dataAquisicao = optStringSafe(item, "DATAAQUISICAO");
                        String codLocal      = lerCampoComoString(item, "CODLOCAL");
                        String nomeLocal     = optStringSafe(item, "NOME_LOCAL");

                        String valorAquisicao = "";
                        if (!item.isNull("VALOR_AQUISICAO")) {
                            double v = item.optDouble("VALOR_AQUISICAO", -1);
                            if (v >= 0) valorAquisicao = String.format("%.2f", v);
                        }

                        if (i == 0 || i % 100 == 0) {
                            Log.d(TAG, "Item[" + i + "] → " +
                                    "PAT=" + patrimonio +
                                    " COD=" + codigoBarra +
                                    " DATA=" + dataAquisicao +
                                    " VALOR=" + valorAquisicao +
                                    " CODLOCAL=" + codLocal +
                                    " NOMELOCAL=" + nomeLocal);
                        }

                        if (patrimonio.isEmpty() || codigoBarra.isEmpty()) {
                            result.erros++;
                            Log.w(TAG, "Item " + i + " sem PAT/COD — PAT=["
                                    + patrimonio + "] COD=[" + codigoBarra + "]");
                            continue;
                        }

                        if (db.existeCodigoBarra(codigoBarra, sqlDB)) {
                            result.duplicados++;
                            continue;
                        }

                        boolean ok = db.inserirPatrimonioNaTransacao(
                                sqlDB, patrimonio, descricao, codigoBarra,
                                dataAquisicao, valorAquisicao, codLocal, nomeLocal);

                        if (ok) result.salvos++;
                        else    result.erros++;

                    } catch (Exception itemEx) {
                        result.erros++;
                        Log.e(TAG, "Erro item " + i + ": " + itemEx.getMessage(), itemEx);
                    }

                    if (progressCallback != null && (i % 50 == 0 || i == total - 1)) {
                        final int si = i + 1, ss = result.salvos,
                                sd = result.duplicados, se = result.erros;
                        progressCallback.onProgress(si, total, ss, sd, se);
                    }
                }

                sqlDB.setTransactionSuccessful();
                result.mensagem = "Sincronização concluída!";

            } finally {
                sqlDB.endTransaction();
                sqlDB.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro geral: " + e.getMessage(), e);
            result.mensagem = "Erro: " + e.getMessage();
        }

        return result;
    }

    /**
     * Lê um campo que pode ser STRING ou NUMERIC no JSON.
     * Quando o campo é número (ex: CODIGOBARRA: 4013525),
     * converte para string sem casas decimais.
     * Retorna "" se nulo ou ausente.
     */
    private static String lerCampoComoString(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return "";

        String strVal = obj.optString(key, "").trim();

        if (strVal.isEmpty() || "null".equalsIgnoreCase(strVal)) {
            double num = obj.optDouble(key, Double.NaN);
            if (!Double.isNaN(num)) {
                long lv = (long) num;
                return String.valueOf(lv);
            }
            return "";
        }

        return strVal;
    }

    /**
     * Lê string pura — retorna "" se o campo for null no JSON.
     */
    private static String optStringSafe(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return "";
        String val = obj.optString(key, "").trim();
        return "null".equalsIgnoreCase(val) ? "" : val;
    }
}