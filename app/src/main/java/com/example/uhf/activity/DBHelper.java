package com.example.uhf.activity;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.uhf.model.Patrimonio; // Import necessário
import com.example.uhf.model.Usuario;    // Import necessário

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USUARIO);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATRIMONIO);
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
}