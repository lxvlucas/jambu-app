package com.jambuapp.api.restaurante;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Compatibilidade restrita à leitura de snapshots antigos: nome/endereço não são reemitidos.
// Os DTOs de entrada HTTP continuam rejeitando campos desconhecidos.
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestauranteReferenciaResponse(UUID id, String googlePlaceId) {
    public static RestauranteReferenciaResponse de(Restaurante restaurante) {
        return new RestauranteReferenciaResponse(restaurante.getId(), restaurante.getGooglePlaceId());
    }
}
