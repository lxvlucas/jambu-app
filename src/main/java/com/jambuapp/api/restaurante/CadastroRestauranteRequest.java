package com.jambuapp.api.restaurante;

import java.util.*;
import jakarta.validation.constraints.*;

public record CadastroRestauranteRequest(@NotBlank @Size(max = 150) String nome,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull CategoriaRestaurante categoria,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,1024}") String googlePlaceId,
        UUID tokenSessao, @Size(max = 100) Set<@NotNull UUID> distintosConfirmados) {}
