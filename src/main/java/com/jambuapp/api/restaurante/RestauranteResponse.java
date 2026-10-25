package com.jambuapp.api.restaurante;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import com.jambuapp.api.restaurante.places.AtribuicaoLugar;

/**
 * Perfil montado só com dados do Jambu. Os campos de dados externos continuam no contrato
 * por compatibilidade e vêm vazios; googleMapsUri é um link Maps URLs, sem chamada à API.
 */
public record RestauranteResponse(UUID id, String googlePlaceId, String nome, String bairro, String endereco,
        boolean dadosExternosDisponiveis, String origem, URI googleMapsUri, List<AtribuicaoLugar> atribuicoes,
        String atribuicaoGoogleMaps, BigDecimal notaMedia, long quantidadeAvaliacoes,
        SituacaoRestaurante situacao, Double latitude, Double longitude, CategoriaRestaurante categoria,
        String overtureId, String atribuicaoCatalogo, long versao, Instant ultimaAvaliacaoEm) {}
