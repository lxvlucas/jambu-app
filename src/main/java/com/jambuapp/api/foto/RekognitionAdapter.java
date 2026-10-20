package com.jambuapp.api.foto;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class RekognitionAdapter implements ProvedorModeracaoImagem {
    private final FotoProperties config;
    private final JsonMapper json;
    private final HttpClient http;

    public RekognitionAdapter(FotoProperties config, JsonMapper json) {
        this.config = config;
        this.json = json;
        if (config.moderacaoBaseUrl() != null) {
            URI url = config.moderacaoBaseUrl();
            boolean awsHttps = "https".equals(url.getScheme()) && url.getHost() != null && url.getHost().endsWith(".amazonaws.com");
            boolean simulador = "http".equals(url.getScheme()) && "127.0.0.1".equals(url.getHost());
            if ((!awsHttps && !simulador) || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null)
                throw new IllegalArgumentException("Provedor de moderação aceita endpoint oficial HTTPS AWS ou simulador em 127.0.0.1.");
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.moderacaoTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public ResultadoProvedor analisar(byte[] conteudo) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Chamada ao provedor de moderação não pode ocorrer dentro de transação de banco.");
        if (!config.moderacaoHabilitada() || config.moderacaoBaseUrl() == null
                || config.moderacaoApiKey() == null || config.moderacaoApiKey().isBlank())
            throw ModeracaoException.naoConfigurada();
        if (conteudo == null || conteudo.length == 0)
            throw ModeracaoException.respostaInvalida();

        String payload = json.writeValueAsString(Map.of(
                "Image", Map.of("Bytes", Base64.getEncoder().encodeToString(conteudo)),
                "MinConfidence", config.limiarRevisao()));
        var request = HttpRequest.newBuilder(config.moderacaoBaseUrl())
                .timeout(config.moderacaoTimeout())
                .header("Content-Type", "application/x-amz-json-1.1")
                .header("X-Amz-Target", "RekognitionService.DetectModerationLabels")
                .header("Authorization", "Bearer " + config.moderacaoApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        var futuro = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> resposta;
        try {
            resposta = futuro.get(config.moderacaoTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            throw ModeracaoException.indisponivel();
        } catch (TimeoutException ex) {
            futuro.cancel(true);
            throw ModeracaoException.timeout();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof HttpTimeoutException) throw ModeracaoException.timeout();
            throw ModeracaoException.indisponivel();
        }

        if (resposta.statusCode() == 401 || resposta.statusCode() == 403 || resposta.statusCode() == 429 || resposta.statusCode() >= 500)
            throw ModeracaoException.indisponivel();
        if (resposta.statusCode() != 200)
            throw ModeracaoException.respostaInvalida();

        JsonNode node;
        try {
            node = json.readTree(resposta.body());
        } catch (RuntimeException ex) {
            throw ModeracaoException.respostaInvalida();
        }
        if (node == null || !node.isObject() || !node.has("ModerationLabels") || !node.path("ModerationLabels").isArray())
            throw ModeracaoException.respostaInvalida();

        Map<String, String> sinais = new LinkedHashMap<>();
        for (JsonNode item : node.path("ModerationLabels")) {
            String nome = item.path("Name").isString() ? item.path("Name").asString().strip() : null;
            JsonNode confiancaNode = item.path("Confidence");
            if (nome == null || nome.isEmpty() || !confiancaNode.isNumber())
                throw ModeracaoException.respostaInvalida();
            double confianca = confiancaNode.asDouble();
            if (Double.isNaN(confianca) || Double.isInfinite(confianca) || confianca < 0.0 || confianca > 100.0)
                throw ModeracaoException.respostaInvalida();
            String chave = normalizarCategoria(nome);
            double atual = sinais.containsKey(chave) ? Double.parseDouble(sinais.get(chave)) : -1.0;
            if (confianca > atual) sinais.put(chave, String.format(Locale.ROOT, "%.2f", confianca));
        }
        String versaoModelo = node.path("ModerationModelVersion").isString() && !node.path("ModerationModelVersion").asString().isBlank()
                ? node.path("ModerationModelVersion").asString().strip()
                : null;
        return new ResultadoProvedor("rekognition", versaoModelo, sinais);
    }

    static String normalizarCategoria(String nome) {
        return nome.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
