package com.jambuapp.api.restaurante;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** areas: nomes dos arquivos em /geografia (ex.: grande-sao-luis); nada de área fixa no código. */
@Validated
@ConfigurationProperties("jambu.restaurantes")
public record CatalogoProperties(@Min(0) @Max(100000) int limiteGoogleDia,
        @Min(50) @Max(1000) int raioDuplicataMetros,
        @DecimalMin("0.1") @DecimalMax("1.0") double similaridadeMinima,
        @NotNull Set<UUID> revisores,
        @NotEmpty List<@Pattern(regexp = "[a-z0-9-]{1,40}") String> areas,
        @Min(1) @Max(100) int limiteCadastrosDia) {
    public CatalogoProperties {
        if (revisores != null) revisores = Set.copyOf(revisores);
        if (areas != null) areas = List.copyOf(areas);
    }
}
