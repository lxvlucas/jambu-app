package com.jambuapp.api.restaurante.places;

import java.util.List;
import java.util.UUID;

public interface ProvedorRestaurantes {
    List<SugestaoLugar> sugerir(String termo, UUID tokenSessao);
    // Token ausente na consulta independente de perfil; presente na confirmação da seleção.
    DadosLugar consultar(String placeId, UUID tokenSessao);
}
