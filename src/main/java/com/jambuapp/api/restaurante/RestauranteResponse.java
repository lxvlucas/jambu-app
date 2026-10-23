package com.jambuapp.api.restaurante;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import com.jambuapp.api.restaurante.places.AtribuicaoLugar;

public record RestauranteResponse(UUID id, String googlePlaceId, String nome, String endereco,
        boolean dadosExternosDisponiveis, String origem, URI googleMapsUri, List<AtribuicaoLugar> atribuicoes,
        String atribuicaoGoogleMaps, BigDecimal notaMedia, long quantidadeAvaliacoes,
        SituacaoRestaurante situacao, Double latitude, Double longitude, CategoriaRestaurante categoria,
        String overtureId, String atribuicaoCatalogo, long versao) {}
