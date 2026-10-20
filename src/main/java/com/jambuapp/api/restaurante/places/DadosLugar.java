package com.jambuapp.api.restaurante.places;

import java.net.URI;
import java.util.List;

/** Conteúdo externo transitório. Não persistir, registrar em log ou usar em snapshots. */
public record DadosLugar(String googlePlaceId, String nome, String endereco, List<String> tipos,
                         URI googleMapsUri, List<AtribuicaoLugar> atribuicoes) {}
