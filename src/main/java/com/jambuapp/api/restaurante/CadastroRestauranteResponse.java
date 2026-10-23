package com.jambuapp.api.restaurante;

import java.util.List;

public record CadastroRestauranteResponse(String situacao, RestauranteResumo restaurante, List<Candidato> candidatos) {
    public record Candidato(RestauranteResumo restaurante, long distanciaMetros, double similaridade) {}
    public static CadastroRestauranteResponse selecionado(String situacao, Restaurante r) {
        r.exigirSelecionavel();
        return new CadastroRestauranteResponse(situacao, RestauranteResumo.de(r), List.of());
    }
}
