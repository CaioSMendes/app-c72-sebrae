package com.example.uhf.model;

public class Patrimonio {
    private int id;
    private String patrimonio;
    private String descricao;
    private String codigoBarra;

    public Patrimonio(int id, String patrimonio, String descricao, String codigoBarra) {
        this.id = id;
        this.patrimonio = patrimonio;
        this.descricao = descricao;
        this.codigoBarra = codigoBarra;
    }

    public int getId() { return id; }
    public String getPatrimonio() { return patrimonio; }
    public String getDescricao() { return descricao; }
    public String getCodigoBarra() { return codigoBarra; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setPatrimonio(String patrimonio) { this.patrimonio = patrimonio; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setCodigoBarra(String codigoBarra) { this.codigoBarra = codigoBarra; }
}