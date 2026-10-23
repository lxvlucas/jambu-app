package com.jambuapp.api.restaurante;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record RevisaoRestauranteRequest(@NotNull @PositiveOrZero Long versao,
        @NotNull SituacaoRestaurante situacao, @NotBlank @Size(max = 1000) String motivo,
        @Valid Correcao correcao) {
    public record Correcao(@NotBlank @Size(max = 150) String nome, @NotNull Double latitude,
            @NotNull Double longitude, @NotNull CategoriaRestaurante categoria) {}
}
