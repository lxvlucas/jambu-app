package com.jambuapp.api.restaurante.places;

import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Nunca propaga corpo, URL com token ou mensagem de exceção do provedor. */
public class PlacesException extends RegraNegocioException {
    public PlacesException(HttpStatus status, String codigo, String mensagem) { super(status, codigo, mensagem); }
    public static PlacesException indisponivel() {
        return new PlacesException(HttpStatus.SERVICE_UNAVAILABLE, "places_indisponivel", "Consulta de restaurantes temporariamente indisponível.");
    }
    public static PlacesException respostaInvalida() {
        return new PlacesException(HttpStatus.BAD_GATEWAY, "places_resposta_invalida", "O provedor retornou uma resposta inválida.");
    }
}
