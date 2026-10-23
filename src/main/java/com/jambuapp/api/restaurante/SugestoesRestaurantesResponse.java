package com.jambuapp.api.restaurante;

import java.util.*;
import com.jambuapp.api.restaurante.places.SugestaoLugar;

public record SugestoesRestaurantesResponse(List<RestauranteResumo> locais, List<SugestaoLugar> itens,
        String estadoExterno, boolean podeVerMais, boolean podeAdicionar, UUID tokenSessao,
        String atribuicaoGoogleMaps) {}
