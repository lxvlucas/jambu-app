package com.jambuapp.api.restaurante;

import java.util.List;

public record RestaurantesProximosResponse(List<Item> itens) {
    public record Item(RestauranteResumo restaurante, long distanciaMetros) {}
}
