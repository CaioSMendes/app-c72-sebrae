package com.example.uhf.activity;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.uhf.model.Patrimonio; // Import necessário
import com.example.uhf.model.Usuario;    // Import necessário
import com.example.uhf.model.Local;    // Import necessário

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sebraeapp.db";
    private static final int DATABASE_VERSION = 3;

    private static final String TABLE_USUARIO = "usuarios";
    private static final String COL_NOME = "nome";
    private static final String COL_MATRICULA = "matricula";

    private static final String TABLE_PATRIMONIO = "patrimonios";
    private static final String COL_ID = "id";
    private static final String COL_PATRIMONIO = "patrimonio";
    private static final String COL_DESCRICAO = "descricao";
    private static final String COL_CODIGO_BARRA = "codigo_barra";

    private static final String TABLE_LOCAL = "locais"; // nova tabela
    private static final String COL_LOCAL_NOME = "local_nome";
    private static final String COL_CODIGO_FILIAL = "codigo_filial";
    private static final String COL_CODIGO_LOCAL = "codigo_local";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsuarios = "CREATE TABLE " + TABLE_USUARIO + " (" +
                COL_NOME + " TEXT," +
                COL_MATRICULA + " TEXT)";
        db.execSQL(createUsuarios);

        String createPatrimonios = "CREATE TABLE " + TABLE_PATRIMONIO + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PATRIMONIO + " TEXT, " +
                COL_DESCRICAO + " TEXT, " +
                COL_CODIGO_BARRA + " TEXT UNIQUE)";
        db.execSQL(createPatrimonios);

        String createLocais = "CREATE TABLE " + TABLE_LOCAL + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOCAL_NOME + " TEXT, " +
                COL_CODIGO_FILIAL + " TEXT, " +
                COL_CODIGO_LOCAL + " TEXT UNIQUE)";
        db.execSQL(createLocais);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USUARIO);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATRIMONIO);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOCAL);
        onCreate(db);
    }

    // USUÁRIOS
    public boolean existeMatricula(String matricula) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT matricula FROM usuarios WHERE matricula = ?", new String[]{matricula});
        boolean existe = cursor.moveToFirst();
        cursor.close();
        db.close();
        return existe;
    }

    public long salvarUsuario(Usuario usuario) {
        if (existeMatricula(usuario.getMatricula())) return -1;

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOME, usuario.getNome());
        values.put(COL_MATRICULA, usuario.getMatricula());
        long id = db.insert(TABLE_USUARIO, null, values);
        db.close();
        return id;
    }

    public boolean atualizarUsuario(String nomeAntigo, String matriculaAntiga, String novoNome, String novaMatricula) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOME, novoNome);
        values.put(COL_MATRICULA, novaMatricula);
        int linhas = db.update(TABLE_USUARIO, values, COL_NOME + "=? AND " + COL_MATRICULA + "=?",
                new String[]{nomeAntigo, matriculaAntiga});
        db.close();
        return linhas > 0;
    }

    public boolean excluirUsuario(String nome, String matricula) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_USUARIO, COL_NOME + "=? AND " + COL_MATRICULA + "=?",
                new String[]{nome, matricula});
        db.close();
        return linhas > 0;
    }

    public List<Usuario> listarUsuarios() {
        List<Usuario> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_NOME + ", " + COL_MATRICULA + " FROM " + TABLE_USUARIO, null);
        if (cursor.moveToFirst()) {
            do {
                lista.add(new Usuario(cursor.getString(0), cursor.getString(1)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return lista;
    }
    public Local buscarLocalPorCodigo(String codigoLocal) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("locais", null, "codigo_local = ?", new String[]{codigoLocal}, null, null, null);
        Local local = null;
        if(cursor.moveToFirst()) {
            local = new Local(
                    cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("local_nome")),
                    cursor.getString(cursor.getColumnIndexOrThrow("codigo_filial")),
                    cursor.getString(cursor.getColumnIndexOrThrow("codigo_local"))
            );
        }
        cursor.close();
        db.close();
        return local;
    }

    public Usuario buscarUsuarioPorMatricula(String matricula) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("usuarios", null, "matricula = ?", new String[]{matricula}, null, null, null);
        Usuario user = null;
        if(cursor.moveToFirst()) {
            user = new Usuario(
                    cursor.getString(cursor.getColumnIndexOrThrow("nome")),
                    cursor.getString(cursor.getColumnIndexOrThrow("matricula"))
            );
        }
        cursor.close();
        db.close();
        return user;
    }

    // PATRIMÔNIOS
    public boolean inserirPatrimonio(String patrimonio, String descricao, String codigoBarra) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query(TABLE_PATRIMONIO, null, COL_CODIGO_BARRA + "=?", new String[]{codigoBarra},
                null, null, null);
        if (cursor.getCount() > 0) {
            cursor.close();
            db.close();
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(COL_PATRIMONIO, patrimonio);
        values.put(COL_DESCRICAO, descricao);
        values.put(COL_CODIGO_BARRA, codigoBarra);
        long result = db.insert(TABLE_PATRIMONIO, null, values);
        cursor.close();
        db.close();
        return result != -1;
    }

    public boolean atualizarPatrimonio(int id, String patrimonio, String descricao, String codigoBarra) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PATRIMONIO, patrimonio);
        values.put(COL_DESCRICAO, descricao);
        values.put(COL_CODIGO_BARRA, codigoBarra);
        int linhas = db.update(TABLE_PATRIMONIO, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return linhas > 0;
    }

    public boolean excluirPatrimonio(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_PATRIMONIO, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return linhas > 0;
    }
    public String getDescricaoPorTag(String codigoPatrimonio) {
        SQLiteDatabase db = this.getReadableDatabase();
        String descricao = null;

        Cursor c = db.rawQuery(
                "SELECT " + COL_DESCRICAO + " FROM " + TABLE_PATRIMONIO +
                        " WHERE " + COL_CODIGO_BARRA + " = ?",
                new String[]{codigoPatrimonio}
        );

        if (c.moveToFirst()) {
            descricao = c.getString(0);
        }

        c.close();
        return descricao;
    }

    public List<Patrimonio> listarPatrimonios() {
        List<Patrimonio> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PATRIMONIO + " ORDER BY id DESC", null);
        if (cursor.moveToFirst()) {
            do {
                lista.add(new Patrimonio(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_PATRIMONIO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRICAO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO_BARRA))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return lista;
    }

    public Patrimonio buscarPatrimonioPorId(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PATRIMONIO, null, COL_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        Patrimonio p = null;
        if (cursor.moveToFirst()) {
            p = new Patrimonio(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_PATRIMONIO)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRICAO)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO_BARRA))
            );
        }
        cursor.close();
        db.close();
        return p;
    }

    // MÉTODOS PARA LOCAIS
    public long salvarLocal(Local local) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LOCAL_NOME, local.getLocalNome());
        values.put(COL_CODIGO_FILIAL, local.getCodigoFilial());
        values.put(COL_CODIGO_LOCAL, local.getCodigoLocal());
        long id = db.insert(TABLE_LOCAL, null, values);
        db.close();
        return id;
    }

    public boolean atualizarLocal(int id, Local local) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LOCAL_NOME, local.getLocalNome());
        values.put(COL_CODIGO_FILIAL, local.getCodigoFilial());
        values.put(COL_CODIGO_LOCAL, local.getCodigoLocal());
        int linhas = db.update(TABLE_LOCAL, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return linhas > 0;
    }

    public boolean excluirLocal(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhas = db.delete(TABLE_LOCAL, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return linhas > 0;
    }

    // Verifica se um local já existe pelo código
    public boolean existeCodigoLocal(String codigoLocal) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_CODIGO_LOCAL + " FROM " + TABLE_LOCAL + " WHERE " + COL_CODIGO_LOCAL + " = ?",
                new String[]{codigoLocal});
        boolean existe = cursor.moveToFirst();
        cursor.close();
        db.close();
        return existe;
    }

    public List<Local> listarLocais() {
        List<Local> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_LOCAL + " ORDER BY id DESC", null);
        if (cursor.moveToFirst()) {
            do {
                Local l = new Local(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_NOME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO_FILIAL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO_LOCAL))
                );
                lista.add(l);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return lista;
    }

    public Local buscarLocalPorId(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOCAL, null, COL_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        Local l = null;
        if (cursor.moveToFirst()) {
            l = new Local(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_NOME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO_FILIAL)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_CODIGO_LOCAL))
            );
        }
        cursor.close();
        db.close();
        return l;
    }
}