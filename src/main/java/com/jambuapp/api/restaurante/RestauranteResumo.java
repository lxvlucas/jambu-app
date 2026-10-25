package com.jambuapp.api.restaurante;

import java.util.UUID;

public record RestauranteResumo(UUID id, String nome, String bairro, String endereco, Double latitude, Double longitude,
        CategoriaRestaurante categoria, OrigemRestaurante origem, SituacaoRestaurante situacao,
        String googlePlaceId, String overtureId, String atribuicaoCatalogo, long versao) {
    public static RestauranteResumo de(Restaurante r) {
        return new RestauranteResumo(r.getId(), r.getNome(), r.getBairro(), r.getEndereco(), r.getLatitude(), r.getLongitude(),
                r.getCategoria(), r.getOrigem(), r.getSituacao(), r.getGooglePlaceId(), r.getOvertureId(),
                r.getAtribuicaoOverture(), r.getVersao());
    }
}
