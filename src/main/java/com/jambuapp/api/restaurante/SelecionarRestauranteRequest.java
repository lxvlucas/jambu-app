package com.jambuapp.api.restaurante;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record SelecionarRestauranteRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,1024}") String googlePlaceId,
                                         @NotNull UUID tokenSessao) {}
