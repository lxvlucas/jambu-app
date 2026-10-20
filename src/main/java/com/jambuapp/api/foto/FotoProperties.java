package com.jambuapp.api.foto;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jambu.fotos")
public record FotoProperties(
        @NotNull Path diretorioLocal,
        @NotBlank String storageTipo,
        URI supabaseUrl,
        String supabaseKey,
        String supabaseBucket,
        boolean moderacaoHabilitada,
        @NotBlank String moderacaoProvedor,
        URI moderacaoBaseUrl,
        String moderacaoApiKey,
        @NotNull Duration moderacaoTimeout,
        @NotBlank String versaoPolitica,
        @DecimalMin("1.0") @DecimalMax("100.0") double limiarRejeicao,
        @DecimalMin("1.0") @DecimalMax("100.0") double limiarRevisao,
        @Min(1) @Max(10) int maxTentativasModeracao,
        @NotNull Duration atrasoBaseModeracao,
        @NotNull Duration duracaoReservaModeracao,
        @Min(1) @Max(100) int loteModeracao,
        boolean agendadorModeracaoHabilitado,
        @NotNull Duration retencaoRejeitadas,
        @Min(1024) @Max(1048576) int tetoPrincipalBytes,
        @Min(1024) @Max(204800) int tetoMiniaturaBytes,
        Set<String> moderadores) {

    public FotoProperties {
        if (diretorioLocal == null) diretorioLocal = Path.of(".local/storage-fotos");
        if (storageTipo == null || storageTipo.isBlank()) storageTipo = "local";
        storageTipo = storageTipo.strip().toLowerCase(Locale.ROOT);
        if (!Set.of("local", "supabase").contains(storageTipo))
            throw new IllegalArgumentException("Tipo de storage de fotos deve ser local ou supabase.");
        if (supabaseUrl != null && supabaseUrl.toString().isBlank()) supabaseUrl = null;
        if (moderacaoBaseUrl != null && moderacaoBaseUrl.toString().isBlank()) moderacaoBaseUrl = null;
        if (moderacaoProvedor == null || moderacaoProvedor.isBlank()) moderacaoProvedor = "rekognition";
        moderacaoProvedor = moderacaoProvedor.strip().toLowerCase(Locale.ROOT);
        if (moderacaoTimeout == null) moderacaoTimeout = Duration.ofSeconds(5);
        if (moderacaoTimeout.isZero() || moderacaoTimeout.isNegative() || moderacaoTimeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Timeout de moderação deve ser positivo e de no máximo 30 segundos.");
        if (versaoPolitica == null || versaoPolitica.isBlank()) versaoPolitica = "2026-10-v1";
        if (limiarRejeicao <= 0) limiarRejeicao = 80.0;
        if (limiarRevisao <= 0) limiarRevisao = 40.0;
        if (limiarRevisao >= limiarRejeicao)
            throw new IllegalArgumentException("O limiar de revisão deve ser menor que o limiar de rejeição.");
        if (maxTentativasModeracao <= 0) maxTentativasModeracao = 3;
        if (atrasoBaseModeracao == null || atrasoBaseModeracao.isNegative() || atrasoBaseModeracao.isZero())
            atrasoBaseModeracao = Duration.ofSeconds(10);
        if (duracaoReservaModeracao == null || duracaoReservaModeracao.isNegative() || duracaoReservaModeracao.isZero())
            duracaoReservaModeracao = Duration.ofSeconds(60);
        if (loteModeracao <= 0) loteModeracao = 10;
        if (retencaoRejeitadas == null || retencaoRejeitadas.isNegative())
            retencaoRejeitadas = Duration.ofDays(7);
        if (tetoPrincipalBytes <= 0) tetoPrincipalBytes = 1_048_576;
        if (tetoMiniaturaBytes <= 0) tetoMiniaturaBytes = 204_800;
        if (moderadores == null) moderadores = Set.of();
        else moderadores = moderadores.stream()
                .filter(Objects::nonNull)
                .map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean ehModerador(String username) {
        return username != null && moderadores.contains(username.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "FotoProperties[credenciais omitidas]";
    }
}
