package com.jambuapp.api.restaurante.places;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jambu.places")
public record PlacesProperties(boolean habilitado, @NotNull String apiKey, @NotNull URI baseUrl,
        @NotNull Duration timeout, @NotEmpty Set<String> tiposAceitos) {
    public PlacesProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0))
            throw new IllegalArgumentException("Timeout Places deve ser positivo e de no máximo 30 segundos.");
        if (baseUrl != null) {
            boolean google = "https".equals(baseUrl.getScheme()) && "places.googleapis.com".equals(baseUrl.getHost());
            boolean simulador = "http".equals(baseUrl.getScheme()) && "127.0.0.1".equals(baseUrl.getHost());
            if ((!google && !simulador) || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                    || !baseUrl.getPath().equals("/v1/"))
                throw new IllegalArgumentException("Places aceita o endpoint oficial ou simulador em 127.0.0.1, com caminho /v1/.");
        }
        if (tiposAceitos != null) tiposAceitos = Set.copyOf(tiposAceitos);
    }
    @Override public String toString() { return "PlacesProperties[credencial omitida]"; }
}
