package com.jambuapp.api.restaurante;

import java.util.UUID;
import jakarta.validation.constraints.*;

/** textoBusca (T05, opcional): o que a pessoa digitou, para achar o lugar escondido do Overture. */
public record SelecionarRestauranteRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,1024}") String googlePlaceId,
                                         @NotNull UUID tokenSessao, @Size(max = 100) String textoBusca) {
    public SelecionarRestauranteRequest(String googlePlaceId, UUID tokenSessao) {
        this(googlePlaceId, tokenSessao, null);
    }
}
