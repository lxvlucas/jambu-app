package com.jambuapp.api.autenticacao;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jambu.auth")
public record AuthProperties(Duration duracaoAcesso, Duration inatividade, Duration limiteAbsoluto,
        Duration janelaRepeticao, Duration autenticacaoRecente, String chaveRepeticao,
        String origemWeb, boolean cookieSeguro, int limiteRefreshMinuto, int limiteReautenticacaoMinuto) {
    public AuthProperties {
        for (Duration prazo : new Duration[]{duracaoAcesso, inatividade, limiteAbsoluto, janelaRepeticao, autenticacaoRecente})
            if (prazo == null || prazo.isNegative() || prazo.isZero()) throw new IllegalArgumentException("Prazo de autenticação inválido.");
        if (duracaoAcesso.compareTo(inatividade) > 0 || inatividade.compareTo(limiteAbsoluto) > 0
                || janelaRepeticao.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("Limites de autenticação inconsistentes.");
        if (limiteRefreshMinuto < 1 || limiteReautenticacaoMinuto < 1)
            throw new IllegalArgumentException("Limites de tentativas devem ser positivos.");
        if (origemWeb != null && !origemWeb.isBlank()) {
            var uri = java.net.URI.create(origemWeb);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || !(uri.getScheme().equals("https") || (!cookieSeguro && uri.getScheme().equals("http")
                        && (uri.getHost().equals("localhost") || uri.getHost().equals("127.0.0.1")))))
                throw new IllegalArgumentException("Origem web inválida; use HTTPS, sem caminho.");
        }
    }
}
