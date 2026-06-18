package com.example.uhf.activity;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.uhf.model.Patrimonio;
import com.example.uhf.model.Usuario;
import com.example.uhf.model.Local;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME    = "sebraeapp.db";
    private static final int    DATABASE_VERSION = 9;

    private static final String TABLE_USUARIO    = "usuarios";
    private static final String COL_NOME         = "nome";
    private static final String COL_MATRICULA    = "matricula";

    private static final String TABLE_PATRIMONIO    = "patrimonios";
    private static final String COL_ID              = "id";
    private static final String COL_PATRIMONIO      = "patrimonio";
    private static final String COL_DESCRICAO       = "descricao";
    private static final String COL_CODIGO_BARRA    = "codigo_barra";
    private static final String COL_DATA_AQUISICAO  = "data_aquisicao";
    private static final String COL_VALOR_AQUISICAO = "valor_aquisicao";
    private static final String COL_COD_LOCAL       = "cod_local";
    private static final String COL_NOME_LOCAL      = "nome_local";

    private static final String TABLE_LOCAL       = "locais";
    private static final String COL_LOCAL_NOME    = "local_nome";
    private static final String COL_CODIGO_FILIAL = "codigo_filial";
    private static final String COL_CODIGO_LOCAL  = "codigo_local";

    private static final String TABLE_HISTORICO = "historico";

    // =========================================================================
    // SINGLETON
    // =========================================================================

    private static DBHelper instance;

    public static synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBHelper(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public DBHelper(Context context) {
        super(context, getDatabaseName(context), null, DATABASE_VERSION);
    }

    // =========================================================================
    // NOME DO BANCO (por ambiente)
    // =========================================================================

    private static String getDatabaseName(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        String ambiente = prefs.getString("ambiente", "homologacao");
        return "producao".equals(ambiente) ? "sebraeapp_prod.db" : "sebraeapp_homolog.db";
    }

    // =========================================================================
    // CRIAÇÃO E MIGRAÇÃO
    // =========================================================================

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USUARIO + " (" +
                COL_NOME + " TEXT, " + COL_MATRICULA + " TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_PATRIMONIO + " (" +
                COL_ID              + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PATRIMONIO      + " TEXT, " +
                COL_DESCRICAO       + " TEXT, " +
                COL_CODIGO_BARRA    + " TEXT UNIQUE, " +
                COL_DATA_AQUISICAO  + " TEXT DEFAULT '', " +
                COL_VALOR_AQUISICAO + " TEXT DEFAULT '', " +
                COL_COD_LOCAL       + " TEXT DEFAULT '', " +
                COL_NOME_LOCAL      + " TEXT DEFAULT '')");

        db.execSQL("CREATE TABLE " + TABLE_LOCAL + " (" +
                COL_ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOCAL_NOME    + " TEXT, " +
                COL_CODIGO_FILIAL + " TEXT, " +
                COL_CODIGO_LOCAL  + " TEXT UNIQUE)");

        db.execSQL("CREATE TABLE " + TABLE_HISTORICO + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sessaoId TEXT, filial TEXT, localCodigo TEXT, " +
                "matricula TEXT, tag TEXT, tipo TEXT, dataHora TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 9) {
            try { db.execSQL("ALTER TABLE " + TABLE_PATRIMONIO + " ADD COLUMN " + COL_DATA_AQUISICAO  + " TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_PATRIMONIO + " ADD COLUMN " + COL_VALOR_AQUISICAO + " TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_PATRIMONIO + " ADD COLUMN " + COL_COD_LOCAL       + " TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_PATRIMONIO + " ADD COLUMN " + COL_NOME_LOCAL      + " TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_HISTORICO + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "sessaoId TEXT, filial TEXT, localCodigo TEXT, " +
                        "matricula TEXT, tag TEXT, tipo TEXT, dataHora TEXT)");
            } catch (Exception ignored) {}
        } else {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORICO);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATRIMONIO);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOCAL);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USUARIO);
            onCreate(db);
        }
    }

    // =========================================================================
    // HISTÓRICO
    // =========================================================================

    /**
     * Salva tag no histórico com tipo simples (compatibilidade com código legado).
     * Para os novos modos de inventário prefira salvarHistoricoComTipo().
     */
    public void salvarHistorico(String filial, String localCodigo,
                                String matricula, String tag, String tipo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("filial",      filial);
        cv.put("localCodigo", localCodigo);
        cv.put("matricula",   matricula);
        cv.put("tag",         tag);
        cv.put("tipo",        tipo);
        cv.put("dataHora",    new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));
        db.insert(TABLE_HISTORICO, null, cv);
        db.close();
    }

    /**
     * Salva tag no histórico com o tipo de inventário correto.
     * Use este nos 3 modos (LIVRE / LOCAL / CATEGORIA / CODBARRA).
     */
    public void salvarHistoricoComTipo(String filial, String localCodigo,
                                       String matricula, String tag,
                                       String tipoInventario) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("filial",      filial);
        cv.put("localCodigo", localCodigo);
        cv.put("matricula",   matricula);
        cv.put("tag",         tag);
        cv.put("tipo",        tipoInventario); // "LIVRE" | "LOCAL" | "CATEGORIA" | "CODBARRA"
        cv.put("dataHora",    new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));
        db.insert(TABLE_HISTORICO, null, cv);
        db.close();
    }

    public boolean inserirHistorico(String matricula, String codigoLocal, String tagEpc) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("matricula",   matricula);
        cv.put("localCodigo", codigoLocal);
        cv.put("tag",         tagEpc);
        cv.put("dataHora",    String.valueOf(System.currentTimeMillis()));
        long res = db.insert(TABLE_HISTORICO, null, cv);
        db.close();
        return res != -1;
    }

    /**
     * Histórico agrupado simples (compatibilidade — usado pelo código legado).
     */
    public Cursor listarHistoricoAgrupado() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT h.localCodigo, " +
                        "(SELECT local_nome    FROM locais WHERE codigo_local = h.localCodigo LIMIT 1) AS localNome, " +
                        "(SELECT codigo_filial FROM locais WHERE codigo_local = h.localCodigo LIMIT 1) AS filial, " +
                        "MAX(dataHora) AS ultimaData " +
                        "FROM " + TABLE_HISTORICO + " h " +
                        "GROUP BY h.localCodigo " +
                        "ORDER BY filial ASC, ultimaData DESC", null);
    }

    /**
     * Histórico agrupado com tipo de inventário e total de tags.
     * Usado pela nova HistoricoActivity.
     *
     * Colunas retornadas (por índice):
     *   0 → localCodigo
     *   1 → localNome
     *   2 → filial
     *   3 → tipoRecente   (tipo da leitura mais recente do local)
     *   4 → ultimaData
     *   5 → totalTags     (quantidade de registros no histórico)
     */
    public Cursor listarHistoricoAgrupadoComTipo() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT " +
                        "  h.localCodigo, " +
                        "  (SELECT local_nome    FROM locais WHERE codigo_local = h.localCodigo LIMIT 1) AS localNome, " +
                        "  (SELECT codigo_filial FROM locais WHERE codigo_local = h.localCodigo LIMIT 1) AS filial, " +
                        "  (SELECT tipo FROM historico WHERE localCodigo = h.localCodigo ORDER BY id DESC LIMIT 1) AS tipoRecente, " +
                        "  MAX(h.dataHora) AS ultimaData, " +
                        "  COUNT(h.id)     AS totalTags " +
                        "FROM historico h " +
                        "GROUP BY h.localCodigo " +
                        "ORDER BY ultimaData DESC",
                null);
    }

    public Cursor listarHistoricoPorSessao(String sessaoId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT filial, localCodigo, matricula, tag, tipo, dataHora " +
                        "FROM " + TABLE_HISTORICO + " WHERE sessaoId = ? " +
                        "ORDER BY datetime(" +
                        "  substr(dataHora,7,4)||'-'||substr(dataHora,4,2)||'-'||" +
                        "  substr(dataHora,1,2)||' '||substr(dataHora,12,8)) DESC",
                new String[]{ sessaoId });
    }

    public Cursor listarHistoricoPorLocal(String codigoLocal) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_HISTORICO +
                        " WHERE localCodigo = ? ORDER BY dataHora DESC",
                new String[]{ codigoLocal });
    }

    public Cursor getHistoricoPorLocal(String codigoLocal) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT matricula, localCodigo, tag FROM " + TABLE_HISTORICO +
                        " WHERE localCodigo = ? ORDER BY id DESC",
                new String[]{ codigoLocal });
    }

    public Cursor getDados(String localCodigo) {
        return getHistoricoPorLocal(localCodigo);
    }

    public void deletarPorLocal(String localCodigo) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORICO, "localCodigo = ?", new String[]{ localCodigo });
        db.close();
    }

    public boolean deletarPorTag(String tag) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_HISTORICO, "tag = ?", new String[]{ tag });
        db.close();
        return linhas > 0;
    }

    public void deletarPorId(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORICO, "id = ?", new String[]{ String.valueOf(id) });
        db.close();
    }

    // =========================================================================
    // USUÁRIOS
    // =========================================================================

    public boolean existeMatricula(String matricula) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_USUARIO + " WHERE " + COL_MATRICULA + "=? LIMIT 1",
                new String[]{ matricula });
        boolean existe = c.moveToFirst();
        c.close();
        db.close();
        return existe;
    }

    public boolean existeMatriculaNaTransacao(SQLiteDatabase db, String matricula) {
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_USUARIO +
                        " WHERE " + COL_MATRICULA + "=? LIMIT 1",
                new String[]{ matricula });
        boolean existe = c.moveToFirst();
        c.close();
        return existe;
    }

    public long salvarUsuarioNaTransacao(SQLiteDatabase db, Usuario usuario) {
        ContentValues values = new ContentValues();
        values.put(COL_NOME,      usuario.getNome());
        values.put(COL_MATRICULA, usuario.getMatricula());
        return db.insert(TABLE_USUARIO, null, values);
    }

    public boolean existeNome(String nome) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_USUARIO + " WHERE " + COL_NOME + "=? LIMIT 1",
                new String[]{ nome });
        boolean existe = c.moveToFirst();
        c.close();
        db.close();
        return existe;
    }

    public long salvarUsuario(Usuario usuario) {
        if (existeMatricula(usuario.getMatricula())) return -1;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOME,      usuario.getNome());
        values.put(COL_MATRICULA, usuario.getMatricula());
        long id = db.insert(TABLE_USUARIO, null, values);
        db.close();
        return id;
    }

    public boolean atualizarUsuario(String nomeAntigo, String matriculaAntiga,
                                    String novoNome,   String novaMatricula) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOME,      novoNome);
        values.put(COL_MATRICULA, novaMatricula);
        int linhas = db.update(TABLE_USUARIO, values,
                COL_NOME + "=? AND " + COL_MATRICULA + "=?",
                new String[]{ nomeAntigo, matriculaAntiga });
        db.close();
        return linhas > 0;
    }

    public boolean excluirUsuario(String nome, String matricula) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_USUARIO,
                COL_NOME + "=? AND " + COL_MATRICULA + "=?",
                new String[]{ nome, matricula });
        db.close();
        return linhas > 0;
    }

    public List<Usuario> listarUsuarios() {
        List<Usuario> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + COL_NOME + ", " + COL_MATRICULA + " FROM " + TABLE_USUARIO, null);
        if (c.moveToFirst()) {
            do { lista.add(new Usuario(c.getString(0), c.getString(1))); }
            while (c.moveToNext());
        }
        c.close();
        db.close();
        return lista;
    }

    public Usuario buscarUsuarioPorMatricula(String matricula) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_USUARIO, null,
                COL_MATRICULA + "=?", new String[]{ matricula }, null, null, null);
        Usuario user = null;
        if (c.moveToFirst()) {
            user = new Usuario(
                    c.getString(c.getColumnIndexOrThrow(COL_NOME)),
                    c.getString(c.getColumnIndexOrThrow(COL_MATRICULA))
            );
        }
        c.close();
        db.close();
        return user;
    }

    public Set<String> getTodasMatriculas() {
        Set<String> matriculas = new HashSet<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_MATRICULA + " FROM " + TABLE_USUARIO, null);
        if (c.moveToFirst()) {
            do { matriculas.add(c.getString(0)); } while (c.moveToNext());
        }
        c.close();
        db.close();
        return matriculas;
    }

    // =========================================================================
    // LOCAIS
    // =========================================================================

    public long salvarLocal(Local local) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_LOCAL_NOME,    local.getLocalNome()    != null ? local.getLocalNome()    : "");
        v.put(COL_CODIGO_FILIAL, local.getCodigoFilial() != null ? local.getCodigoFilial() : "");
        v.put(COL_CODIGO_LOCAL,  local.getCodigoLocal()  != null ? local.getCodigoLocal()  : "");
        long id = db.insert(TABLE_LOCAL, null, v);
        db.close();
        return id;
    }

    public boolean atualizarLocal(int id, Local local) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_LOCAL_NOME,    local.getLocalNome());
        v.put(COL_CODIGO_FILIAL, local.getCodigoFilial());
        v.put(COL_CODIGO_LOCAL,  local.getCodigoLocal());
        int linhas = db.update(TABLE_LOCAL, v, COL_ID + "=?", new String[]{ String.valueOf(id) });
        db.close();
        return linhas > 0;
    }

    public boolean excluirLocal(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_LOCAL, COL_ID + "=?", new String[]{ String.valueOf(id) });
        db.close();
        return linhas > 0;
    }

    public boolean existeCodigoLocal(String codigoLocal) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_LOCAL + " WHERE " + COL_CODIGO_LOCAL + "=? LIMIT 1",
                new String[]{ codigoLocal });
        boolean existe = c.moveToFirst();
        c.close();
        db.close();
        return existe;
    }

    public boolean existeCodigoLocalNaTransacao(SQLiteDatabase db, String codigoLocal) {
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_LOCAL +
                        " WHERE " + COL_CODIGO_LOCAL + "=? LIMIT 1",
                new String[]{ codigoLocal });
        boolean existe = c.moveToFirst();
        c.close();
        return existe;
    }

    public long salvarLocalNaTransacao(SQLiteDatabase db, Local local) {
        ContentValues v = new ContentValues();
        v.put(COL_LOCAL_NOME,    local.getLocalNome()    != null ? local.getLocalNome()    : "");
        v.put(COL_CODIGO_FILIAL, local.getCodigoFilial() != null ? local.getCodigoFilial() : "");
        v.put(COL_CODIGO_LOCAL,  local.getCodigoLocal()  != null ? local.getCodigoLocal()  : "");
        return db.insert(TABLE_LOCAL, null, v);
    }

    public List<Local> listarLocais() {
        List<Local> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_LOCAL + " ORDER BY id DESC", null);
        if (c.moveToFirst()) {
            do {
                lista.add(new Local(
                        c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_LOCAL_NOME)),
                        c.getString(c.getColumnIndexOrThrow(COL_CODIGO_FILIAL)),
                        c.getString(c.getColumnIndexOrThrow(COL_CODIGO_LOCAL))
                ));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return lista;
    }

    public Local buscarLocalPorId(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_LOCAL, null,
                COL_ID + "=?", new String[]{ String.valueOf(id) }, null, null, null);
        Local l = null;
        if (c.moveToFirst()) {
            l = new Local(
                    c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_LOCAL_NOME)),
                    c.getString(c.getColumnIndexOrThrow(COL_CODIGO_FILIAL)),
                    c.getString(c.getColumnIndexOrThrow(COL_CODIGO_LOCAL))
            );
        }
        c.close();
        db.close();
        return l;
    }

    public Local buscarLocalPorCodigo(String codigoLocal) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_LOCAL, null,
                COL_CODIGO_LOCAL + "=?", new String[]{ codigoLocal }, null, null, null);
        Local l = null;
        if (c.moveToFirst()) {
            l = new Local(
                    c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_LOCAL_NOME)),
                    c.getString(c.getColumnIndexOrThrow(COL_CODIGO_FILIAL)),
                    c.getString(c.getColumnIndexOrThrow(COL_CODIGO_LOCAL))
            );
        }
        c.close();
        db.close();
        return l;
    }

    // =========================================================================
    // PATRIMÔNIOS
    // =========================================================================

    public boolean inserirPatrimonio(String patrimonio, String descricao, String codigoBarra) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_PATRIMONIO +
                        " WHERE " + COL_CODIGO_BARRA + "=? LIMIT 1",
                new String[]{ codigoBarra });
        boolean existe = c.moveToFirst();
        c.close();
        if (existe) { db.close(); return false; }
        ContentValues values = new ContentValues();
        values.put(COL_PATRIMONIO,   patrimonio);
        values.put(COL_DESCRICAO,    descricao);
        values.put(COL_CODIGO_BARRA, codigoBarra);
        long result = db.insert(TABLE_PATRIMONIO, null, values);
        db.close();
        return result != -1;
    }

    public boolean existeCodigoBarra(String codigoBarra, SQLiteDatabase db) {
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + TABLE_PATRIMONIO +
                        " WHERE " + COL_CODIGO_BARRA + "=? LIMIT 1",
                new String[]{ codigoBarra });
        boolean existe = c.moveToFirst();
        c.close();
        return existe;
    }

    public boolean inserirPatrimonioNaTransacao(SQLiteDatabase db,
                                                String patrimonio,
                                                String descricao,
                                                String codigoBarra,
                                                String dataAquisicao,
                                                String valorAquisicao,
                                                String codLocal,
                                                String nomeLocal) {
        ContentValues values = new ContentValues();
        values.put(COL_PATRIMONIO,      patrimonio);
        values.put(COL_DESCRICAO,       descricao);
        values.put(COL_CODIGO_BARRA,    codigoBarra);
        values.put(COL_DATA_AQUISICAO,  dataAquisicao  != null ? dataAquisicao  : "");
        values.put(COL_VALOR_AQUISICAO, valorAquisicao != null ? valorAquisicao : "");
        values.put(COL_COD_LOCAL,       codLocal       != null ? codLocal       : "");
        values.put(COL_NOME_LOCAL,      nomeLocal      != null ? nomeLocal      : "");
        return db.insert(TABLE_PATRIMONIO, null, values) != -1;
    }

    public boolean atualizarPatrimonio(int id, String patrimonio,
                                       String descricao, String codigoBarra) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PATRIMONIO,   patrimonio);
        values.put(COL_DESCRICAO,    descricao);
        values.put(COL_CODIGO_BARRA, codigoBarra);
        int linhas = db.update(TABLE_PATRIMONIO, values,
                COL_ID + "=?", new String[]{ String.valueOf(id) });
        db.close();
        return linhas > 0;
    }

    public boolean excluirPatrimonio(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_PATRIMONIO,
                COL_ID + "=?", new String[]{ String.valueOf(id) });
        db.close();
        return linhas > 0;
    }

    public String getDescricaoPorTag(String codigoBarra) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + COL_DESCRICAO + " FROM " + TABLE_PATRIMONIO +
                        " WHERE " + COL_CODIGO_BARRA + "=? LIMIT 1",
                new String[]{ codigoBarra });
        String descricao = null;
        if (c.moveToFirst()) descricao = c.getString(0);
        c.close();
        return descricao;
    }

    public List<Patrimonio> listarPatrimonios() {
        List<Patrimonio> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + TABLE_PATRIMONIO + " ORDER BY id DESC", null);
        if (c.moveToFirst()) {
            do { lista.add(patrimonioFromCursor(c)); } while (c.moveToNext());
        }
        c.close();
        db.close();
        return lista;
    }

    public List<Patrimonio> listarPatrimoniosPorLocal(String codigoLocal) {
        List<Patrimonio> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + TABLE_PATRIMONIO +
                        " WHERE " + COL_COD_LOCAL + " = ? ORDER BY " + COL_DESCRICAO + " ASC",
                new String[]{ codigoLocal });
        if (c.moveToFirst()) {
            do { lista.add(patrimonioFromCursor(c)); } while (c.moveToNext());
        }
        c.close();
        db.close();
        return lista;
    }

    public Patrimonio buscarPatrimonioPorId(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_PATRIMONIO, null,
                COL_ID + "=?", new String[]{ String.valueOf(id) }, null, null, null);
        Patrimonio p = null;
        if (c.moveToFirst()) p = patrimonioFromCursor(c);
        c.close();
        db.close();
        return p;
    }

    private Patrimonio patrimonioFromCursor(Cursor c) {
        int idxData  = c.getColumnIndex(COL_DATA_AQUISICAO);
        int idxValor = c.getColumnIndex(COL_VALOR_AQUISICAO);
        int idxCod   = c.getColumnIndex(COL_COD_LOCAL);
        int idxNome  = c.getColumnIndex(COL_NOME_LOCAL);

        return new Patrimonio(
                c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_PATRIMONIO)),
                c.getString(c.getColumnIndexOrThrow(COL_DESCRICAO)),
                c.getString(c.getColumnIndexOrThrow(COL_CODIGO_BARRA)),
                idxData  >= 0 ? c.getString(idxData)  : "",
                idxValor >= 0 ? c.getString(idxValor) : "",
                idxCod   >= 0 ? c.getString(idxCod)   : "",
                idxNome  >= 0 ? c.getString(idxNome)  : ""
        );
    }
}