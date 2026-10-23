package com.jambuapp.api.restaurante;

/**
 * Seis categorias do MVP. PRACA_ALIMENTACAO só existe para ler dados antigos: categoria descreve
 * o tipo do estabelecimento, não onde ele fica, e não é aceita em cadastro, revisão ou importação.
 */
public enum CategoriaRestaurante { RESTAURANTE, CAFE, PADARIA, BAR, LANCHONETE, SORVETERIA, PRACA_ALIMENTACAO;
    public static final String MENSAGEM_CATEGORIAS =
            "Escolha uma das categorias: restaurante, café, padaria, bar, lanchonete ou sorveteria.";
    public boolean aceitaCadastro() { return this != PRACA_ALIMENTACAO; }
}
