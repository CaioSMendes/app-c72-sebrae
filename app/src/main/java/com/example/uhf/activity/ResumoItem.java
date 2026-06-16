package com.example.uhf.activity;

public class ResumoItem {

    private String descricao;
    private int quantidade;

    public ResumoItem(String descricao, int quantidade) {
        this.descricao = descricao;
        this.quantidade = quantidade;
    }

    public String getDescricao() { return descricao; }
    public int getQuantidade() { return quantidade; }
}