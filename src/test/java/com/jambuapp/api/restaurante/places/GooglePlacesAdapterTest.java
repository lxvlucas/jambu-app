package com.jambuapp.api.restaurante.places;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jambuapp.api.restaurante.CotaPlacesService;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class GooglePlacesAdapterTest {
    private static final String DETALHE = """
            {"id":"ChIJ_teste","displayName":{"text":"Nome externo"},"formattedAddress":"Endereço externo",
             "types":["restaurant","food"],"googleMapsUri":"https://maps.google.com/?cid=1",
             "attributions":[{"provider":"Fornecedor","providerUri":"https://example.test/atribuicao"}],
             "rating":4.99,"reviews":[{"text":"Não importar"}],"photos":[{"name":"Não importar"}]}
            """;
    HttpServer servidor;
    ExecutorService executor;
    JsonMapper json = JsonMapper.builder().build();
    volatile int status;
    volatile String resposta;
    volatile long atraso;
    volatile String metodo;
    volatile URI uri;
    volatile String chave;
    volatile String mascara;
    volatile String corpo;
    GooglePlacesAdapter adapter;
    CotaPlacesService cota = mock(CotaPlacesService.class);
    // Mesma área padrão da API: a ilha de São Luís (geografia/grande-sao-luis.geojson).
    com.jambuapp.api.restaurante.AreaCatalogo area = new com.jambuapp.api.restaurante.AreaCatalogo(json,
            new com.jambuapp.api.restaurante.CatalogoProperties(300, 300, 0.45, Set.of(), java.util.List.of("grande-sao-luis"), 5, 30));

    @BeforeEach
    void iniciar() throws Exception {
        status = 200;
        resposta = DETALHE;
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        servidor.setExecutor(executor);
        servidor.createContext("/", exchange -> {
            metodo = exchange.getRequestMethod();
            uri = exchange.getRequestURI();
            chave = exchange.getRequestHeaders().getFirst("X-Goog-Api-Key");
            mascara = exchange.getRequestHeaders().getFirst("X-Goog-FieldMask");
            corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (atraso > 0) Thread.sleep(atraso);
                byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        servidor.start();
        adapter = new GooglePlacesAdapter(config(true, "chave-ficticia", Duration.ofSeconds(2)), json, cota, area);
    }
    @AfterEach
    void encerrar() {
        if (servidor != null) servidor.stop(0);
        if (executor != null) executor.shutdownNow();
    }
    @Test
    void autocompleteUsaPostMascaraMinimaTokenEFiltroDeTipos() {
        resposta = """
                {"suggestions":[
                  {"placePrediction":{"placeId":"ChIJ_teste","text":{"text":"Restaurante de teste"},"types":["restaurant"]}},
                  {"placePrediction":{"placeId":"escola","text":{"text":"Escola"},"types":["school"]}},
                  {"queryPrediction":{"text":{"text":"Consulta livre"}}}]}
                """;
        UUID sessao = UUID.randomUUID();
        assertThat(adapter.sugerir(" arroz ", sessao)).containsExactly(new SugestaoLugar("ChIJ_teste", "Restaurante de teste"));
        assertThat(metodo).isEqualTo("POST");
        assertThat(uri.toString()).isEqualTo("/v1/places:autocomplete");
        assertThat(chave).isEqualTo("chave-ficticia");
        assertThat(mascara).isEqualTo(GooglePlacesAdapter.AUTOCOMPLETE_MASK).doesNotContain("*", "photos", "reviews", "rating");
        var body = json.readTree(corpo);
        assertThat(body.path("input").asString()).isEqualTo("arroz");
        assertThat(body.path("sessionToken").asString()).isEqualTo(sessao.toString());
        assertThat(body.path("languageCode").asString()).isEqualTo("pt-BR");
        assertThat(body.path("includedRegionCodes").get(0).asString()).isEqualTo("br");
        // T02: o próprio Google só devolve lugares dentro do retângulo da ilha.
        assertThat(body.has("locationBias")).isFalse();
        var retangulo = body.path("locationRestriction").path("rectangle");
        assertThat(retangulo.path("low").path("latitude").asDouble()).isBetween(-2.85, -2.70);
        assertThat(retangulo.path("high").path("latitude").asDouble()).isBetween(-2.45, -2.30);
        assertThat(retangulo.path("low").path("longitude").asDouble()).isBetween(-44.45, -44.30);
        assertThat(retangulo.path("high").path("longitude").asDouble()).isBetween(-44.05, -43.90);
    }
    @Test
    void detailsUsaGetTokenAtribuicoesESomenteDadosPermitidos() {
        UUID sessao = UUID.randomUUID();
        var lugar = adapter.consultar("ChIJ_teste", sessao);
        assertThat(metodo).isEqualTo("GET");
        assertThat(uri.getPath()).isEqualTo("/v1/places/ChIJ_teste");
        assertThat(uri.getQuery()).contains("sessionToken=" + sessao).doesNotContain("key");
        assertThat(mascara).isEqualTo(GooglePlacesAdapter.DETAILS_MASK).doesNotContain("rating", "photos", "reviews", "*");
        // T02: só id e types (SKU Essentials); nome e endereço do Google não são lidos.
        assertThat(GooglePlacesAdapter.DETAILS_MASK).isEqualTo("id,types");
        assertThat(lugar.tipos()).contains("restaurant");
        resposta = "{\"id\":\"ChIJ_teste\",\"types\":[\"restaurant\"]}";
        assertThat(adapter.consultar("ChIJ_teste", sessao).tipos()).containsExactly("restaurant");
        assertThat(json.writeValueAsString(lugar)).doesNotContain("rating", "reviews", "photos", "Não importar");
        adapter.consultar("ChIJ_teste", null);
        assertThat(uri.getQuery()).doesNotContain("sessionToken");
    }
    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void errosExternosSaoControladosSemVazarCorpoOuChave(int codigo) {
        status = codigo;
        resposta = "{\"error\":\"chave-ficticia e informações do provedor\"}";
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", UUID.randomUUID()))
                .isInstanceOfSatisfying(PlacesException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getMessage()).doesNotContain("chave-ficticia", "informações do provedor");
                });
    }
    @Test
    void lugarInexistenteRetornaErroControlado() {
        status = 404;
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOfSatisfying(PlacesException.class,
                ex -> assertThat(ex.getCodigo()).isEqualTo("lugar_invalido"));
    }
    @Test
    void rejeitaRespostaMalformadaOuOutroIdentificador() {
        resposta = "não é JSON";
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(PlacesException.class);
        resposta = DETALHE.replace("ChIJ_teste", "outro");
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(PlacesException.class);
        resposta = "{}";
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(PlacesException.class);
    }
    @Test
    void timeoutTemPrazoFinitoENaoRetornaSucessoSimulado() {
        atraso = 1000;
        adapter = new GooglePlacesAdapter(config(true, "chave-ficticia", Duration.ofMillis(100)), json, cota, area);
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOfSatisfying(PlacesException.class,
                ex -> assertThat(ex.getCodigo()).isEqualTo("places_timeout"));
    }
    @Test
    void configuracaoAusenteNaoDisparaHttp() {
        adapter = new GooglePlacesAdapter(config(true, "", Duration.ofSeconds(1)), json, cota, area);
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOfSatisfying(PlacesException.class,
                ex -> assertThat(ex.getCodigo()).isEqualTo("places_nao_configurado"));
        assertThat(uri).isNull();
    }
    @Test
    void naoSegueRedirecionamentoNemConsultaDentroDeTransacao() {
        status = 302;
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(PlacesException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.clear(); }
    }
    @Test
    void cotaEsgotadaImpedeHttpEChamadaComErroTambemReserva() {
        doThrow(new PlacesException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "places_cota_esgotada", "Esgotada")).when(cota).reservar();
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(PlacesException.class);
        assertThat(uri).isNull();
        reset(cota);
        status = 503;
        assertThatThrownBy(() -> adapter.consultar("ChIJ_teste", null)).isInstanceOf(PlacesException.class);
        verify(cota).reservar();
        assertThat(uri).isNotNull();
    }
    private PlacesProperties config(boolean habilitado, String key, Duration timeout) {
        return new PlacesProperties(habilitado, key, URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/"), timeout, Set.of("restaurant", "cafe"));
    }
}
