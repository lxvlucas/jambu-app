package com.jambuapp.api.restaurante.places;

import java.util.List;

/** Resposta do Place Details (T02): só o id e os tipos, para conferir se é lugar de comida. Não persistir. */
public record DadosLugar(String googlePlaceId, List<String> tipos) {}
