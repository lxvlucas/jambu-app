package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.jambuapp.api.autenticacao.*;
import com.jambuapp.api.comum.Hash;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "jambu.auth.chave-repeticao=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "jambu.auth.origem-web=https://jambu.example.test",
    "jambu.limite-autenticacao-minuto=1000"
})
@ActiveProfiles("test")
class SessoesPersistentesTest {
    static final Instant INICIO = Instant.parse("2026-10-23T12:00:00Z");
    static final String SENHA = "Senha-ficticia-123!";
    @Autowired AutenticacaoService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired Relogio clock;
    @Autowired LimpezaTokens limpeza;
    @LocalServerPort int porta;
    final HttpClient cliente = HttpClient.newHttpClient();
    @BeforeEach void preparar() { clock.agora = INICIO; }
    @AfterEach void restaurar() { clock.agora = INICIO; }

    @Test void acessoCurtoRenovaAposExpirarSemEstenderLimiteAbsoluto() throws Exception {
        var login = login();
        assertThat(Instant.parse(login.path("expiraEm").asString())).isEqualTo(INICIO.plusSeconds(900));
        String acesso = login.path("token").asString(), refresh = login.path("refreshToken").asString();
        assertThat(refresh).hasSize(43).isNotEqualTo(acesso);
        assertThat(jdbc.queryForObject("select token_hash from tokens_renovacao where token_hash = ?", String.class,
                Hash.sha256(refresh))).isNotEqualTo(refresh);
        clock.agora = INICIO.plus(Duration.ofDays(7));
        assertThat(chamar("GET", "/api/usuarios/me", acesso, null).statusCode()).isEqualTo(401);
        var renovacao = renovar(refresh, UUID.randomUUID());
        assertThat(renovacao.statusCode()).isEqualTo(200);
        var nova = json.readTree(renovacao.body());
        assertThat(chamar("GET", "/api/usuarios/me", nova.path("token").asString(), null).statusCode()).isEqualTo(200);
        assertThat(Instant.parse(nova.path("expiraSessaoEm").asString())).isEqualTo(INICIO.plus(Duration.ofDays(37)));
        String atual = nova.path("refreshToken").asString();
        for (int dia = 35; dia <= 175; dia += 28) {
            clock.agora = INICIO.plus(Duration.ofDays(dia));
            var resposta = renovar(atual, UUID.randomUUID());
            assertThat(resposta.statusCode()).isEqualTo(200);
            atual = json.readTree(resposta.body()).path("refreshToken").asString();
        }
        clock.agora = INICIO.plus(Duration.ofDays(180));
        assertThat(renovar(atual, UUID.randomUUID()).statusCode()).isEqualTo(401);
    }

    @Test void refreshNaoServeComoBearerEAcessoNaoRenova() throws Exception {
        var login = login();
        assertThat(chamar("GET", "/api/usuarios/me", login.path("refreshToken").asString(), null).statusCode()).isEqualTo(401);
        assertThat(renovar(login.path("token").asString(), UUID.randomUUID()).statusCode()).isEqualTo(401);
        clock.agora = INICIO.plus(Duration.ofDays(30));
        assertThat(renovar(login.path("refreshToken").asString(), UUID.randomUUID()).statusCode()).isEqualTo(401);
    }

    @Test void repeticaoRecuperaMesmaRespostaEReplayRevogaSomenteSuaSessao() throws Exception {
        var primeiro = login();
        var conta = json.readTree(chamar("GET", "/api/usuarios/me", primeiro.path("token").asString(), null).body());
        var outroDispositivo = json.readTree(chamar("POST", "/api/auth/login", null,
                Map.of("identificador", conta.path("username").asString(), "senha", SENHA)).body());
        String refresh = primeiro.path("refreshToken").asString();
        UUID operacao = UUID.randomUUID();
        var a = renovar(refresh, operacao);
        assertThat(a.statusCode()).isEqualTo(200);
        assertThat(renovar(refresh, operacao).body()).isEqualTo(a.body());
        clock.agora = INICIO.plusSeconds(31);
        assertThat(renovar(refresh, UUID.randomUUID()).statusCode()).isEqualTo(401);
        var nova = json.readTree(a.body());
        assertThat(chamar("GET", "/api/usuarios/me", nova.path("token").asString(), null).statusCode()).isEqualTo(401);
        assertThat(renovar(nova.path("refreshToken").asString(), UUID.randomUUID()).statusCode()).isEqualTo(401);
        assertThat(chamar("GET", "/api/usuarios/me", outroDispositivo.path("token").asString(), null).statusCode()).isEqualTo(200);
    }

    @Test void renovacoesConcorrentesNaoGeramDuasCadeias() throws Exception {
        var login = login();
        String refresh = login.path("refreshToken").asString();
        UUID operacao = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<HttpResponse<String>> chamada = () -> { inicio.await(); return renovar(refresh, operacao); };
            var a = executor.submit(chamada); var b = executor.submit(chamada); inicio.countDown();
            var ra = a.get(15, TimeUnit.SECONDS); var rb = b.get(15, TimeUnit.SECONDS);
            assertThat(ra.statusCode()).isEqualTo(200);
            assertThat(rb.statusCode()).isEqualTo(200);
            assertThat(ra.body()).isEqualTo(rb.body());
        }
        assertThat(renovar(refresh, UUID.randomUUID()).statusCode()).isEqualTo(409);
    }

    @Test void logoutComAcessoExpiradoEIsolamentoDeContas() throws Exception {
        var login = login(); var terceiro = login();
        assertThat(chamar("DELETE", "/api/auth/sessoes/" + login.path("sessaoId").asString(),
                terceiro.path("token").asString(), null).statusCode()).isEqualTo(404);
        assertThat(chamar("GET", "/api/auth/sessoes", login.path("token").asString(), null).body())
                .doesNotContain(login.path("token").asString(), login.path("refreshToken").asString(), "tokenHash");
        clock.agora = INICIO.plusSeconds(901);
        assertThat(chamar("POST", "/api/auth/logout", null,
                Map.of("refreshToken", login.path("refreshToken").asString())).statusCode()).isEqualTo(204);
        assertThat(renovar(login.path("refreshToken").asString(), UUID.randomUUID()).statusCode()).isEqualTo(401);
        assertThat(renovar(terceiro.path("refreshToken").asString(), UUID.randomUUID()).statusCode()).isEqualTo(200);
    }

    @Test void webProtegeCookieCsrfOrigemEIsolaCanalNativo() throws Exception {
        String nome = "web_" + UUID.randomUUID().toString().substring(0, 8);
        auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA));
        var credenciais = Map.of("identificador", nome, "senha", SENHA);
        assertThat(chamar("POST", "/api/auth/web/login", null, credenciais).statusCode()).isEqualTo(403);
        var csrf = chamar("GET", "/api/auth/web/csrf", null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        String cookieCsrf = csrf.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow().split(";", 2)[0];
        var cabecalhos = new HashMap<String, String>();
        cabecalhos.put("Cookie", cookieCsrf);
        cabecalhos.put("Origin", "https://jambu.example.test");
        assertThat(chamarHeaders("POST", "/api/auth/web/login", credenciais, cabecalhos).statusCode()).isEqualTo(403);
        cabecalhos.put(json.readTree(csrf.body()).path("headerName").asString(), json.readTree(csrf.body()).path("token").asString());
        var resposta = chamarHeaders("POST", "/api/auth/web/login", credenciais, cabecalhos);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).doesNotContain("refreshToken");
        String cookie = resposta.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("jambu_refresh="))
                .findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth/web", "Max-Age=")
                .doesNotContain("Domain=");
        String refresh = cookie.split(";", 2)[0].substring("jambu_refresh=".length());
        assertThat(renovar(refresh, UUID.randomUUID()).statusCode()).isEqualTo(401);
        cabecalhos.put("Cookie", cookieCsrf + "; " + cookie.split(";", 2)[0]);
        cabecalhos.put("Origin", "https://atacante.example");
        assertThat(chamarHeaders("POST", "/api/auth/web/refresh", Map.of("operacaoId", UUID.randomUUID()), cabecalhos).statusCode()).isEqualTo(403);
        cabecalhos.put("Origin", "https://jambu.example.test");
        cabecalhos.put("Authorization", "Bearer expirado");
        var renovada = chamarHeaders("POST", "/api/auth/web/refresh", Map.of("operacaoId", UUID.randomUUID()), cabecalhos);
        assertThat(renovada.statusCode()).isEqualTo(200);
        assertThat(renovada.body()).doesNotContain("refreshToken");
        assertThat(chamarHeaders("POST", "/api/auth/web/logout", null, cabecalhos).statusCode()).isEqualTo(204);
        assertThat(chamar("GET", "/api/usuarios/me", json.readTree(renovada.body()).path("token").asString(), null).statusCode()).isEqualTo(401);
    }

    @Test void reautenticarProtegeAlteracaoDeEmailENaoProlongaSessao() throws Exception {
        var login = login();
        clock.agora = INICIO.plusSeconds(601);
        var conta = json.readTree(chamar("GET", "/api/usuarios/me", login.path("token").asString(), null).body());
        var alteracao = Map.of("username", conta.path("username").asString(), "email", "novo_" + UUID.randomUUID() + "@example.test", "celular", "98999999999");
        assertThat(chamar("PUT", "/api/usuarios/me", login.path("token").asString(), alteracao).statusCode()).isEqualTo(403);
        assertThat(chamar("POST", "/api/auth/reautenticar", login.path("token").asString(), Map.of("senha", "incorreta")).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/auth/reautenticar", login.path("token").asString(), Map.of("senha", SENHA)).statusCode()).isEqualTo(204);
        assertThat(chamar("PUT", "/api/usuarios/me", login.path("token").asString(), alteracao).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select absoluta_em from sessoes where id = ?", java.sql.Timestamp.class,
                UUID.fromString(login.path("sessaoId").asString())).toInstant()).isEqualTo(INICIO.plus(Duration.ofDays(180)));
    }

    @Test void logoutTodosRevogaTodosOsDispositivosDaConta() throws Exception {
        String nome = "multi_" + UUID.randomUUID().toString().substring(0, 8);
        auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA));
        var a = json.readTree(chamar("POST", "/api/auth/login", null, Map.of("identificador", nome, "senha", SENHA)).body());
        var b = json.readTree(chamar("POST", "/api/auth/login", null, Map.of("identificador", nome, "senha", SENHA)).body());
        var outra = login();
        assertThat(json.readTree(chamar("GET", "/api/auth/sessoes", a.path("token").asString(), null).body()).size()).isEqualTo(2);
        assertThat(chamar("DELETE", "/api/auth/sessoes/" + b.path("sessaoId").asString(), a.path("token").asString(), null).statusCode()).isEqualTo(204);
        assertThat(chamar("GET", "/api/usuarios/me", a.path("token").asString(), null).statusCode()).isEqualTo(200);
        assertThat(chamar("GET", "/api/usuarios/me", b.path("token").asString(), null).statusCode()).isEqualTo(401);
        assertThat(chamar("POST", "/api/auth/logout-todos", a.path("token").asString(), null).statusCode()).isEqualTo(204);
        assertThat(renovar(a.path("refreshToken").asString(), UUID.randomUUID()).statusCode()).isEqualTo(401);
        assertThat(chamar("GET", "/api/usuarios/me", outra.path("token").asString(), null).statusCode()).isEqualTo(200);
    }

    @Test void respostaTemporariaEstaCifradaEPodeSerEliminadaSemPerderDeteccaoDeReplay() throws Exception {
        var login = login(); var operacao = UUID.randomUUID();
        var resposta = renovar(login.path("refreshToken").asString(), operacao);
        var nova = json.readTree(resposta.body());
        String cifrada = jdbc.queryForObject("select resposta_cifrada from tokens_renovacao where token_hash = ?", String.class,
                Hash.sha256(login.path("refreshToken").asString()));
        assertThat(cifrada).doesNotContain(login.path("refreshToken").asString(), nova.path("refreshToken").asString(), nova.path("token").asString());
        clock.agora = INICIO.plusSeconds(31);
        limpeza.limpar();
        assertThat(jdbc.queryForObject("select resposta_cifrada from tokens_renovacao where token_hash = ?", String.class,
                Hash.sha256(login.path("refreshToken").asString()))).isNull();
        assertThat(renovar(login.path("refreshToken").asString(), operacao).statusCode()).isEqualTo(401);
        assertThat(chamar("GET", "/api/usuarios/me", nova.path("token").asString(), null).statusCode()).isEqualTo(401);
    }

    @Test void bearerExpiradoNaoBloqueiaRenovacaoEReautenticacaoTemLimiteProprio() throws Exception {
        var login = login();
        clock.agora = INICIO.plusSeconds(901);
        var expirada = chamar("GET", "/api/usuarios/me", login.path("token").asString(), null);
        assertThat(expirada.body()).contains("acesso_expirado");
        assertThat(chamar("POST", "/api/auth/refresh", login.path("token").asString(),
                Map.of("refreshToken", login.path("refreshToken").asString(), "operacaoId", UUID.randomUUID())).statusCode()).isEqualTo(200);
        clock.agora = INICIO.plusSeconds(120);
        for (int i = 0; i < 5; i++)
            assertThat(chamar("POST", "/api/auth/reautenticar", login.path("token").asString(), Map.of("senha", "incorreta")).statusCode()).isEqualTo(401);
        var limitada = chamar("POST", "/api/auth/reautenticar", login.path("token").asString(), Map.of("senha", SENHA));
        assertThat(limitada.statusCode()).isEqualTo(429);
        assertThat(limitada.headers().firstValue("Retry-After")).hasValue("60");
    }

    @Test void logoutLegadoAceitaFormularioVazioSemAutenticarPorFormulario() throws Exception {
        var jsonVazio = login();
        assertThat(chamarHeaders("POST", "/api/auth/logout", null, Map.of("Authorization", "Bearer " + jsonVazio.path("token").asString(),
                "Content-Type", "application/json")).statusCode()).isEqualTo(204);
        var login = login();
        var resposta = chamarHeaders("POST", "/api/auth/logout", null, Map.of("Authorization", "Bearer " + login.path("token").asString(),
                "Content-Type", "application/x-www-form-urlencoded"));
        assertThat(resposta.statusCode()).isEqualTo(204);
        assertThat(chamar("GET", "/api/usuarios/me", login.path("token").asString(), null).statusCode()).isEqualTo(401);
        assertThat(chamarHeaders("POST", "/api/auth/logout", null,
                Map.of("Content-Type", "application/x-www-form-urlencoded")).statusCode()).isEqualTo(401);
    }

    JsonNode login() throws Exception {
        String nome = "sess_" + UUID.randomUUID().toString().substring(0, 8);
        auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA));
        var resposta = chamar("POST", "/api/auth/login", null, Map.of("identificador", nome, "senha", SENHA));
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Cache-Control")).hasValue("no-store");
        return json.readTree(resposta.body());
    }
    HttpResponse<String> renovar(String refresh, UUID operacao) throws Exception {
        return chamar("POST", "/api/auth/refresh", null, Map.of("refreshToken", refresh, "operacaoId", operacao));
    }
    HttpResponse<String> chamar(String metodo, String caminho, String token, Object corpo) throws Exception {
        return chamarHeaders(metodo, caminho, corpo, token == null ? Map.of() : Map.of("Authorization", "Bearer " + token));
    }
    HttpResponse<String> chamarHeaders(String metodo, String caminho, Object corpo, Map<String, String> headers) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho));
        headers.forEach(req::header);
        if (corpo != null) req.header("Content-Type", "application/json");
        return cliente.send(req.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo))).build(), HttpResponse.BodyHandlers.ofString());
    }
    @TestConfiguration static class Config {
        @Bean @Primary Relogio relogio() { return new Relogio(); }
    }
    static class Relogio extends Clock {
        volatile Instant agora = INICIO;
        @Override public Instant instant() { return agora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zona) { return Clock.fixed(agora, zona); }
    }
}
