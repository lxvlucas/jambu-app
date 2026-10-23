package com.jambuapp.api.restaurante;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jambu.restaurantes")
public record CatalogoProperties(@Min(0) @Max(100000) int limiteGoogleDia,
        @Min(50) @Max(1000) int raioDuplicataMetros,
        @DecimalMin("0.1") @DecimalMax("1.0") double similaridadeMinima,
        @NotNull Set<UUID> revisores) {
    public CatalogoProperties { if (revisores != null) revisores = Set.copyOf(revisores); }
}
