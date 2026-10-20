package com.jambuapp.api.restaurante;

import java.util.List;
import com.jambuapp.api.restaurante.places.SugestaoLugar;

public record SugestoesRestaurantesResponse(List<SugestaoLugar> itens, String atribuicaoGoogleMaps) {}
