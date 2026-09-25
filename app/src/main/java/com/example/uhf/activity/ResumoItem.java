package com.example.uhf.activity;

public class ResumoItem {

    private String descricao;
    private int quantidade;
    private boolean pertenceAoLocal;

    public ResumoItem(
            String descricao,
            int quantidade,
            boolean pertenceAoLocal
    ) {
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.pertenceAoLocal = pertenceAoLocal;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public boolean isPertenceAoLocal() {
        return pertenceAoLocal;
    }
}