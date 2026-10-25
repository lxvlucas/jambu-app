package com.jambuapp.api.restaurante.places;

import java.util.List;
import java.util.UUID;

public interface ProvedorRestaurantes {
    List<SugestaoLugar> sugerir(String termo, UUID tokenSessao);
    // Token ausente na consulta independente de perfil; presente na confirmação da seleção.
    DadosLugar consultar(String placeId, UUID tokenSessao);
    /** T05: busca do Google que só devolve IDs, feita com o nome e o ponto do Jambu; gratuita, não gasta a cota. */
    List<String> buscarIds(String nome, double latitude, double longitude);
}
