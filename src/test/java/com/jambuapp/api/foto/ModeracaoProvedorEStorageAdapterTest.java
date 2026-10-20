package com.jambuapp.api.foto;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class ModeracaoProvedorEStorageAdapterTest {
    HttpServer servidor;
    ExecutorService executor;
    JsonMapper json = JsonMapper.builder().build();
    volatile int status;
    volatile byte[] respostaBytes;
    volatile long atrasoMs;
    volatile String ultimoMetodo;
    volatile URI ultimaUri;
    volatile String ultimoTarget;
    volatile String ultimaAuth;
    volatile String ultimoCorpo;
    private final Map<String, byte[]> objetosStorage = new ConcurrentHashMap<>();

    @BeforeEach
    void iniciar() throws Exception {
        status = 200;
        atrasoMs = 0;
        respostaBytes = """
                {"ModerationLabels":[{"Name":"Explicit Nudity","Confidence":88.5},{"Name":"Suggestive","Confidence":52.0}],
                 "ModerationModelVersion":"7.0"}
                """.getBytes(StandardCharsets.UTF_8);
        objetosStorage.clear();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        servidor.setExecutor(executor);
        servidor.createContext("/moderacao", exchange -> {
            ultimoMetodo = exchange.getRequestMethod();
            ultimaUri = exchange.getRequestURI();
            ultimoTarget = exchange.getRequestHeaders().getFirst("X-Amz-Target");
            ultimaAuth = exchange.getRequestHeaders().getFirst("Authorization");
            ultimoCorpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (atrasoMs > 0) Thread.sleep(atrasoMs);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, respostaBytes.length);
                exchange.getResponseBody().write(respostaBytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        servidor.createContext("/storage/v1/object/jambu-privado/", exchange -> {
            ultimoMetodo = exchange.getRequestMethod();
            ultimaUri = exchange.getRequestURI();
            ultimaAuth = exchange.getRequestHeaders().getFirst("Authorization");
            String chave = ultimaUri.getPath().substring("/storage/v1/object/jambu-privado/".length());
            byte[] req = exchange.getRequestBody().readAllBytes();
            try {
                if ("POST".equals(ultimoMetodo)) {
                    if (objetosStorage.putIfAbsent(chave, req) != null) {
                        exchange.sendResponseHeaders(409, -1);
                    } else {
                        byte[] ok = "{\"Key\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, ok.length);
                        exchange.getResponseBody().write(ok);
                    }
                } else if ("GET".equals(ultimoMetodo)) {
                    byte[] atual = objetosStorage.get(chave);
                    if (atual == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                        exchange.sendResponseHeaders(200, atual.length);
                        exchange.getResponseBody().write(atual);
                    }
                } else if ("DELETE".equals(ultimoMetodo)) {
                    objetosStorage.remove(chave);
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } finally {
                exchange.close();
            }
        });
        servidor.start();
    }

    @AfterEach
    void encerrar() {
        if (servidor != null) servidor.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void rekognitionAdapterEnviaPayloadNormalizaCategoriasEExtraiVersaoModelo() {
        var adapter = new RekognitionAdapter(configModeracao(true, "segredo-moderacao", Duration.ofSeconds(2)), json);
        var resultado = adapter.analisar(new byte[]{1, 2, 3, 4});
        assertThat(ultimoMetodo).isEqualTo("POST");
        assertThat(ultimoTarget).isEqualTo("RekognitionService.DetectModerationLabels");
        assertThat(ultimaAuth).isEqualTo("Bearer segredo-moderacao");
        var arvore = json.readTree(ultimoCorpo);
        assertThat(arvore.path("Image").path("Bytes").asString()).isEqualTo(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}));
        assertThat(arvore.path("MinConfidence").asDouble()).isEqualTo(40.0);
        assertThat(resultado.provedor()).isEqualTo("rekognition");
        assertThat(resultado.versaoModelo()).isEqualTo("7.0");
        assertThat(resultado.sinais())
                .containsEntry("explicit_nudity", "88.50")
                .containsEntry("suggestive", "52.00");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void rekognitionAdapterTrataErrosExternosSemVazarSegredos(int codigoHttp) {
        status = codigoHttp;
        respostaBytes = "{\"message\":\"segredo-moderacao exposto\"}".getBytes(StandardCharsets.UTF_8);
        var adapter = new RekognitionAdapter(configModeracao(true, "segredo-moderacao", Duration.ofSeconds(2)), json);
        assertThatThrownBy(() -> adapter.analisar(new byte[]{1, 2}))
                .isInstanceOfSatisfying(ModeracaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("moderacao_indisponivel");
                    assertThat(ex.getMessage()).doesNotContain("segredo-moderacao");
                });
    }

    @Test
    void rekognitionAdapterControlaTimeoutRespostaMalformadaConfiguracaoETransacao() {
        var semChave = new RekognitionAdapter(configModeracao(true, "", Duration.ofSeconds(1)), json);
        assertThatThrownBy(() -> semChave.analisar(new byte[]{1}))
                .isInstanceOfSatisfying(ModeracaoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("moderacao_nao_configurada"));
        assertThat(ultimaUri).isNull();

        respostaBytes = "invalido".getBytes(StandardCharsets.UTF_8);
        var adapter = new RekognitionAdapter(configModeracao(true, "chave", Duration.ofSeconds(1)), json);
        assertThatThrownBy(() -> adapter.analisar(new byte[]{1}))
                .isInstanceOfSatisfying(ModeracaoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("moderacao_resposta_invalida"));

        atrasoMs = 800;
        var rapido = new RekognitionAdapter(configModeracao(true, "chave", Duration.ofMillis(100)), json);
        assertThatThrownBy(() -> rapido.analisar(new byte[]{1}))
                .isInstanceOfSatisfying(ModeracaoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("moderacao_timeout"));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> adapter.analisar(new byte[]{1})).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void politicaModeracaoClassificaAprovarRevisarERejeitarPelasFaixasConfiguradas() {
        var politica = new PoliticaModeracao(configModeracao(true, "chave", Duration.ofSeconds(2)));

        var aprovada = politica.avaliar(new ResultadoProvedor("rekognition", "7.0", Map.of("explicit_nudity", "12.00")));
        assertThat(aprovada.resultado()).isEqualTo(ResultadoModeracao.APROVAR);

        var revisaoCritica = politica.avaliar(new ResultadoProvedor("rekognition", "7.0", Map.of("graphic_violence", "55.00")));
        assertThat(revisaoCritica.resultado()).isEqualTo(ResultadoModeracao.REVISAR);

        var revisaoSensivel = politica.avaliar(new ResultadoProvedor("rekognition", "7.0", Map.of("suggestive", "85.00")));
        assertThat(revisaoSensivel.resultado()).isEqualTo(ResultadoModeracao.REVISAR);

        var rejeitada = politica.avaliar(new ResultadoProvedor("rekognition", "7.0", Map.of("explicit_nudity", "80.00")));
        assertThat(rejeitada.resultado()).isEqualTo(ResultadoModeracao.REJEITAR);
    }

    @Test
    void supabaseStorageAdapterGravaLeRemoveEBloqueiaTransacaoAtiva() {
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/");
        var storage = new SupabaseStorageAdapter(base, "token-supabase", "jambu-privado", Duration.ofSeconds(2));
        String chave = "fotos/10000000-0000-0000-0000-000000000001/principal.jpg";
        byte[] bytes = new byte[]{9, 8, 7, 6};

        storage.gravar(chave, bytes);
        assertThat(storage.existe(chave)).isTrue();
        assertThat(storage.ler(chave)).isEqualTo(bytes);
        assertThatThrownBy(() -> storage.gravar(chave, bytes)).isInstanceOf(RegraNegocioException.class);
        storage.apagar(chave);
        assertThat(storage.existe(chave)).isFalse();

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> storage.existe(chave)).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    private FotoProperties configModeracao(boolean habilitada, String chave, Duration timeout) {
        return new FotoProperties(
                null, "local", null, null, null,
                habilitada, "rekognition",
                URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/moderacao"),
                chave, timeout, "2026-10-v1", 80.0, 40.0, 3,
                Duration.ofSeconds(1), Duration.ofSeconds(30), 10, false, Duration.ofDays(7),
                1_048_576, 204_800, Set.of("admin_moderador"));
    }
}
