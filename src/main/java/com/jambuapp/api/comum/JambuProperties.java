package com.jambuapp.api.comum;

import java.net.URI;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jambu")
public record JambuProperties(
        @Min(1) @Max(100) int limiteDishes,
        @Min(1) @Max(10000) int limiteReview,
        @Min(1) int limiteAutenticacaoMinuto,
        @NotNull URI urlCompartilhamento) {
}
