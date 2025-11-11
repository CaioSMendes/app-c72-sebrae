package com.example.uhf.model;

public class Usuario {
    private String nome;
    private String matricula;
    private int imagemResId; // campo para imagem do usuário (opcional)

    public Usuario(String nome, String matricula) {
        this.nome = nome;
        this.matricula = matricula;
        this.imagemResId = 0; // nenhum por padrão
    }

    public Usuario(String nome, String matricula, int imagemResId) {
        this.nome = nome;
        this.matricula = matricula;
        this.imagemResId = imagemResId;
    }

    // Getters
    public String getNome() { return nome; }
    public String getMatricula() { return matricula; }
    public int getImagemResId() { return imagemResId; }

    // Setters
    public void setNome(String nome) { this.nome = nome; }
    public void setMatricula(String matricula) { this.matricula = matricula; }
    public void setImagemResId(int imagemResId) { this.imagemResId = imagemResId; }
}