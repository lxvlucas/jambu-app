package com.jambuapp.api.restaurante.places;

import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.restaurante.AreaCatalogo;
import com.jambuapp.api.restaurante.CotaPlacesService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@EnableConfigurationProperties(PlacesProperties.class)
public class GooglePlacesAdapter implements ProvedorRestaurantes {
    public static final String AUTOCOMPLETE_MASK = "suggestions.placePrediction.placeId,suggestions.placePrediction.text.text,suggestions.placePrediction.types";
    /** Só id e types: SKU Essentials; nome e endereço do Google não podem ser guardados nem são usados (T02). */
    public static final String DETAILS_MASK = "id,types";
    private final PlacesProperties config;
    private final JsonMapper json;
    private final HttpClient http;
    private final CotaPlacesService cota;
    private final AreaCatalogo area;
    public GooglePlacesAdapter(PlacesProperties config, JsonMapper json, CotaPlacesService cota, AreaCatalogo area) {
        this.config = config;
        this.json = json;
        this.cota = cota;
        this.area = area;
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override
    public List<SugestaoLugar> sugerir(String termo, UUID tokenSessao) {
        if (termo == null || termo.strip().length() < 2 || termo.length() > 100 || tokenSessao == null)
            throw RegraNegocioException.invalido("Informe termo de 2 a 100 caracteres e tokenSessao UUID do Places.");
        var r = area.retangulo();
        var corpo = Map.of("input", termo.strip(), "sessionToken", tokenSessao.toString(),
                "languageCode", "pt-BR", "regionCode", "BR", "includedRegionCodes", List.of("br"),
                "includeQueryPredictions", false,
                // Restrição (não só viés) ao retângulo da área do catálogo: o Google não sugere lugares de fora.
                "locationRestriction", Map.of("rectangle", Map.of(
                        "low", Map.of("latitude", r.sul(), "longitude", r.oeste()),
                        "high", Map.of("latitude", r.norte(), "longitude", r.leste()))));
        var node = executar("places:autocomplete", AUTOCOMPLETE_MASK, json.writeValueAsString(corpo));
        if (!node.isObject() || (node.has("suggestions") && !node.path("suggestions").isArray())) throw PlacesException.respostaInvalida();
        List<SugestaoLugar> sugestoes = new ArrayList<>();
        for (var item : node.path("suggestions")) {
            var prediction = item.path("placePrediction");
            if (prediction.isMissingNode()) continue;
            String id = texto(prediction, "placeId");
            String descricao = texto(prediction.path("text"), "text");
            if (id == null || descricao == null) throw PlacesException.respostaInvalida();
            if (tipos(prediction).stream().anyMatch(config.tiposAceitos()::contains)) sugestoes.add(new SugestaoLugar(id, descricao));
        }
        return List.copyOf(sugestoes);
    }
    @Override
    public DadosLugar consultar(String placeId, UUID tokenSessao) {
        validarPlaceId(placeId);
        String caminho = "places/" + placeId + "?languageCode=pt-BR&regionCode=BR"
                + (tokenSessao == null ? "" : "&sessionToken=" + tokenSessao);
        var node = executar(caminho, DETAILS_MASK, null);
        if (!placeId.equals(texto(node, "id")) || tipos(node).isEmpty())
            throw PlacesException.respostaInvalida();
        // Só serve para conferir se é lugar de comida; nada daqui é guardado além do place_id.
        return new DadosLugar(placeId, tipos(node));
    }
    public static void validarPlaceId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,1024}")) throw RegraNegocioException.invalido("googlePlaceId inválido.");
    }
    private JsonNode executar(String caminho, String mascara, String corpo) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Chamada Places não pode ocorrer dentro de transação de banco.");
        if (!config.habilitado() || config.apiKey().isBlank())
            throw new PlacesException(HttpStatus.SERVICE_UNAVAILABLE, "places_nao_configurado", "Integração de restaurantes não configurada.");
        // O prefixo relativo impede que o ':' de places:autocomplete seja interpretado como esquema URI.
        var request = HttpRequest.newBuilder(config.baseUrl().resolve("./" + caminho)).timeout(config.timeout())
                .header("X-Goog-Api-Key", config.apiKey()).header("X-Goog-FieldMask", mascara)
                .header("Content-Type", "application/json");
        if (corpo == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(corpo));
        cota.reservar();
        var futuro = http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> resposta;
        try { resposta = futuro.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException ex) { futuro.cancel(true); Thread.currentThread().interrupt(); throw PlacesException.indisponivel(); }
        catch (TimeoutException ex) { futuro.cancel(true); throw new PlacesException(HttpStatus.GATEWAY_TIMEOUT, "places_timeout", "O provedor de restaurantes não respondeu a tempo."); }
        catch (ExecutionException ex) {
            if (ex.getCause() instanceof HttpTimeoutException)
                throw new PlacesException(HttpStatus.GATEWAY_TIMEOUT, "places_timeout", "O provedor de restaurantes não respondeu a tempo.");
            throw PlacesException.indisponivel();
        }
        if (resposta.statusCode() == 404 || resposta.statusCode() == 400)
            throw new PlacesException(HttpStatus.UNPROCESSABLE_CONTENT, "lugar_invalido", "Lugar ou seleção não reconhecido pelo provedor.");
        if (resposta.statusCode() == 429 || resposta.statusCode() >= 500 || resposta.statusCode() == 401 || resposta.statusCode() == 403)
            throw PlacesException.indisponivel();
        if (resposta.statusCode() != 200) throw PlacesException.respostaInvalida();
        try { return json.readTree(resposta.body()); }
        catch (RuntimeException ex) { throw PlacesException.respostaInvalida(); }
    }
    private static String texto(JsonNode node, String campo) {
        var valor = node.path(campo);
        return valor.isString() && !valor.asString().isBlank() ? valor.asString() : null;
    }
    private static List<String> tipos(JsonNode node) {
        List<String> tipos = new ArrayList<>();
        if (!node.path("types").isArray()) return tipos;
        for (var tipo : node.path("types")) if (tipo.isString()) tipos.add(tipo.asString());
        return List.copyOf(tipos);
    }
}
