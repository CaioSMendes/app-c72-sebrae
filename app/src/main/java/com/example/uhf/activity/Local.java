package com.example.uhf.model;

public class Local {
    private int id;
    private String localNome;
    private String codigoFilial;
    private String codigoLocal;

    public Local(int id, String localNome, String codigoFilial, String codigoLocal) {
        this.id = id;
        this.localNome = localNome;
        this.codigoFilial = codigoFilial;
        this.codigoLocal = codigoLocal;
    }

    public int getId() { return id; }
    public String getLocalNome() { return localNome; }
    public String getCodigoFilial() { return codigoFilial; }
    public String getCodigoLocal() { return codigoLocal; }

    public void setId(int id) { this.id = id; }
    public void setLocalNome(String localNome) { this.localNome = localNome; }
    public void setCodigoFilial(String codigoFilial) { this.codigoFilial = codigoFilial; }
    public void setCodigoLocal(String codigoLocal) { this.codigoLocal = codigoLocal; }
}
