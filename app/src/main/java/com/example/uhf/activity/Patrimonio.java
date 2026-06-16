package com.example.uhf.model;

public class Patrimonio {

    private int    id;
    private String patrimonio;
    private String descricao;
    private String codigoBarra;

    // Campos novos vindos da API TOTVS
    private String dataAquisicao;
    private String valorAquisicao;
    private String codLocal;
    private String nomeLocal;

    // -------------------------------------------------------------------------
    // Construtores
    // -------------------------------------------------------------------------

    /** Construtor completo (sincronização via API). */
    public Patrimonio(int id, String patrimonio, String descricao, String codigoBarra,
                      String dataAquisicao, String valorAquisicao,
                      String codLocal, String nomeLocal) {
        this.id             = id;
        this.patrimonio     = patrimonio;
        this.descricao      = descricao;
        this.codigoBarra    = codigoBarra;
        this.dataAquisicao  = dataAquisicao  != null ? dataAquisicao  : "";
        this.valorAquisicao = valorAquisicao != null ? valorAquisicao : "";
        this.codLocal       = codLocal       != null ? codLocal       : "";
        this.nomeLocal      = nomeLocal      != null ? nomeLocal      : "";
    }

    /** Construtor legado (cadastro manual / planilha). */
    public Patrimonio(int id, String patrimonio, String descricao, String codigoBarra) {
        this(id, patrimonio, descricao, codigoBarra, "", "", "", "");
    }

    // -------------------------------------------------------------------------
    // Getters e Setters
    // -------------------------------------------------------------------------

    public int getId()                            { return id; }
    public void setId(int id)                     { this.id = id; }

    public String getPatrimonio()                 { return patrimonio; }
    public void setPatrimonio(String patrimonio)  { this.patrimonio = patrimonio; }

    public String getDescricao()                  { return descricao; }
    public void setDescricao(String descricao)    { this.descricao = descricao; }

    public String getCodigoBarra()                { return codigoBarra; }
    public void setCodigoBarra(String codigoBarra){ this.codigoBarra = codigoBarra; }

    public String getDataAquisicao()              { return dataAquisicao; }
    public void setDataAquisicao(String v)        { this.dataAquisicao = v; }

    public String getValorAquisicao()             { return valorAquisicao; }
    public void setValorAquisicao(String v)       { this.valorAquisicao = v; }

    public String getCodLocal()                   { return codLocal; }
    public void setCodLocal(String v)             { this.codLocal = v; }

    public String getNomeLocal()                  { return nomeLocal; }
    public void setNomeLocal(String v)            { this.nomeLocal = v; }
}