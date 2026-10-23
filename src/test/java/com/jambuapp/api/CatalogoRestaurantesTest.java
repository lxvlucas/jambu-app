package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.jambuapp.api.autenticacao.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.meal.*;
import com.jambuapp.api.restaurante.*;
import com.jambuapp.api.restaurante.overture.*;
import com.jambuapp.api.restaurante.places.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(JambuApiApplicationTests.TestConfig.class)
class CatalogoRestaurantesTest {
    private static final UUID REVISOR = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final String SENHA = "Teste-catalogo-123!";
    @Autowired AutenticacaoService auth;
    @Autowired CadastroRestauranteService cadastros;
    @Autowired RestauranteService service;
    @Autowired RestauranteRepository restaurantes;
    @Autowired MealService meals;
    @Autowired RevisaoRestauranteService revisao;
    @Autowired ImportacaoOvertureService importacao;
    @Autowired CotaPlacesService cota;
    @Autowired CatalogoProperties config;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired AreaCatalogo area;
    @Autowired JambuApiApplicationTests.PlacesSimulado places;
    @Autowired JambuApiApplicationTests.RelogioTeste clock;
    @LocalServerPort int porta;
    UUID autor;

    @BeforeEach void preparar() {
        places.falhar = false;
        places.chamadas.set(0);
        clock.agora = Instant.parse("2026-10-22T15:00:00Z");
        String nome = "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        autor = auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA)).id();
        jdbc.update("""
                insert into usuarios(id,username,email,celular,senha_hash,criado_em)
                values (?,'revisor_catalogo_fixture','revisor_catalogo@example.test','98999999999','hash-fixture',now())
                on conflict(id) do nothing
                """, REVISOR);
    }
    @AfterEach void restaurar() { places.falhar = false; }

    @Test void localPrimeiroVerMaisAmpliaMantemLocaisEControlaRepeticao() {
        String nome = "Aka " + UUID.randomUUID();
        var criado = cadastrar(nome);
        int antes = places.chamadas.get();
        var busca = service.buscar(nome, null, false, autor);
        assertThat(busca.locais()).extracting(RestauranteResumo::id).contains(criado.restaurante().id());
        assertThat(busca.podeVerMais()).isTrue();
        assertThat(busca.estadoExterno()).isEqualTo("NAO_SOLICITADO");
        assertThat(places.chamadas.get()).isEqualTo(antes);
        var ampliada = service.buscar(nome, busca.tokenSessao(), true, autor);
        assertThat(ampliada.locais()).isEqualTo(busca.locais());
        assertThat(ampliada.itens()).isNotEmpty();
        assertThat(ampliada.atribuicaoGoogleMaps()).isEqualTo("Google Maps");
        assertThat(places.chamadas.get()).isEqualTo(antes + 1);
        assertThat(service.buscar(nome, busca.tokenSessao(), true, autor).estadoExterno()).isEqualTo("AGUARDE");
        assertThat(places.chamadas.get()).isEqualTo(antes + 1);
    }

    @Test void buscaVaziaAmpliaSozinhaEFalhaNaoImpedeCadastroMealOuPerfilLocal() {
        places.falhar = true;
        var busca = service.buscar(UUID.randomUUID().toString(), null, false, autor);
        assertThat(busca.locais()).isEmpty();
        assertThat(busca.estadoExterno()).isEqualTo("INDISPONIVEL");
        assertThat(busca.podeAdicionar()).isTrue();
        assertThat(places.chamadas.get()).isEqualTo(1);
        var criado = cadastrar("Único " + UUID.randomUUID());
        assertThat(criado.restaurante().situacao()).isEqualTo(SituacaoRestaurante.PROVISORIO);
        var meal = publicar(criado.restaurante().id());
        assertThat(meal.restaurante().id()).isEqualTo(criado.restaurante().id());
        assertThat(service.consultar(criado.restaurante().id()).nome()).isEqualTo(criado.restaurante().nome());
        assertThat(places.chamadas.get()).isEqualTo(1);
    }

    @Test void nomeSemAcentoEPrefixosEncontramImportadosELocais() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        var criado = cadastrar("Café Açúcar " + sufixo);
        var busca = service.buscar("cafe acu " + sufixo, null, false, autor);
        assertThat(busca.locais()).extracting(RestauranteResumo::id).contains(criado.restaurante().id());
        assertThat(places.chamadas.get()).isZero();
    }

    @Test void duplicataExigeConfirmacaoEOutraUnidadePodeSerCriada() {
        String nome = "Sabor " + UUID.randomUUID();
        var primeiro = cadastrar(nome);
        String chave = UUID.randomUUID().toString();
        var pedido = manual(nome, -2.5301, -44.30, Set.of());
        var possivel = cadastros.cadastrar(autor, chave, pedido);
        assertThat(possivel.situacao()).isEqualTo("CONFIRMAR_DUPLICATAS");
        assertThat(possivel.restaurante()).isNull();
        assertThat(possivel.candidatos()).extracting(c -> c.restaurante().id()).contains(primeiro.restaurante().id());
        var confirmados = new HashSet<UUID>();
        possivel.candidatos().forEach(c -> confirmados.add(c.restaurante().id()));
        var outra = cadastros.cadastrar(autor, chave, manual(nome, -2.5301, -44.30, confirmados));
        assertThat(outra.situacao()).isEqualTo("CRIADO");
        assertThat(outra.restaurante().id()).isNotEqualTo(primeiro.restaurante().id());
        assertThat(outra.restaurante().situacao()).isEqualTo(SituacaoRestaurante.PROVISORIO);
    }

    @Test void distanciaSozinhaNaoFundeNomesDiferentesENomesIguaisLongeNaoColidem() {
        String nome = UUID.randomUUID().toString();
        cadastrar(nome);
        assertThat(cadastros.cadastrar(autor, "longe", manual(nome, -2.54, -44.29, Set.of())).situacao()).isEqualTo("CRIADO");
        assertThat(cadastrar(UUID.randomUUID().toString()).situacao()).isEqualTo("CRIADO");
    }

    @Test void cadastroConcorrenteIdempotenteMantemUmUuidEOutraChaveRecebeDuplicata() throws Exception {
        var pedido = manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of());
        var resultados = paralelo(8, i -> cadastros.cadastrar(autor, "mesma-chave", pedido).restaurante().id().toString());
        assertThat(new HashSet<>(resultados)).hasSize(1);
        assertThat(cadastros.cadastrar(autor, "outra-chave", pedido).situacao()).isEqualTo("CONFIRMAR_DUPLICATAS");
        assertThatThrownBy(() -> cadastros.cadastrar(autor, "mesma-chave",
                manual("Nome modificado", -2.53, -44.30, Set.of()))).isInstanceOf(RegraNegocioException.class);
    }

    @Test void chavesDiferentesSimultaneasRepetemAntiduplicataDentroDaTransacao() throws Exception {
        var pedido = manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of());
        var resultados = paralelo(8, i -> cadastros.cadastrar(autor, "corrida-" + i, pedido).situacao());
        assertThat(resultados.stream().filter("CRIADO"::equals).count()).isEqualTo(1);
        assertThat(resultados.stream().filter("CONFIRMAR_DUPLICATAS"::equals).count()).isEqualTo(7);
    }

    @Test void googleGuardaSomenteIdEReutilizaReferenciaSemRevalidar() {
        String placeId = "validado_" + UUID.randomUUID();
        var pedido = new CadastroRestauranteRequest("Nome próprio " + UUID.randomUUID(), -2.53, -44.30,
                CategoriaRestaurante.RESTAURANTE, placeId, UUID.randomUUID(), Set.of());
        var criado = cadastros.cadastrar(autor, "google", pedido);
        assertThat(criado.restaurante().googlePlaceId()).isEqualTo(placeId);
        assertThat(criado.restaurante().origem()).isEqualTo(OrigemRestaurante.USUARIO);
        assertThat(jdbc.queryForObject("select nome from restaurantes where id = ?", String.class, criado.restaurante().id()))
                .isEqualTo(pedido.nome()).doesNotContain("externo");
        assertThat(jdbc.queryForObject("select endereco from restaurantes where id = ?", String.class, criado.restaurante().id())).isNull();
        int chamadas = places.chamadas.get();
        var repetido = cadastros.cadastrar(autor, "google-outro-pedido", pedido);
        assertThat(repetido.restaurante().id()).isEqualTo(criado.restaurante().id());
        assertThat(places.chamadas.get()).isEqualTo(chamadas);
        publicar(criado.restaurante().id());
        String snapshot = jdbc.queryForObject("select resposta_json from publicacoes_idempotentes where autor_id = ?", String.class, autor);
        assertThat(snapshot).doesNotContain("Nome externo", "Endereço externo", "googleMapsUri");
    }

    @Test void referenciaGoogleConcorrenteTemUnicoUuid() throws Exception {
        String id = "conc_" + UUID.randomUUID();
        var pedido = new CadastroRestauranteRequest(UUID.randomUUID().toString(), -2.53, -44.30,
                CategoriaRestaurante.RESTAURANTE, id, UUID.randomUUID(), Set.of());
        var resultados = paralelo(8, i -> cadastros.cadastrar(autor, "google-conc-" + i, pedido).restaurante().id().toString());
        assertThat(new HashSet<>(resultados)).hasSize(1);
    }

    @Test void cotaAtomicaPersisteEntreInstanciasResetaSomenteNoDiaSeguinte() throws Exception {
        var dia = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Sao_Paulo"));
        jdbc.update("delete from consumo_places_diario where dia = ?", dia);
        var resultados = paralelo(120, i -> {
            try { cota.reservar(); return "reservada"; }
            catch (PlacesException ex) { assertThat(ex.getCodigo()).isEqualTo("places_cota_esgotada"); return "negada"; }
        });
        assertThat(resultados.stream().filter("reservada"::equals).count()).isEqualTo(100);
        var outraInstancia = new CotaPlacesService(jdbc, clock, config);
        assertThatThrownBy(outraInstancia::reservar).isInstanceOf(PlacesException.class);
        clock.agora = dia.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        jdbc.update("delete from consumo_places_diario where dia = ?", dia.plusDays(1));
        cota.reservar();
        assertThat(jdbc.queryForObject("select chamadas from consumo_places_diario where dia = ?", Integer.class, dia)).isEqualTo(100);
        assertThat(jdbc.queryForObject("select chamadas from consumo_places_diario where dia = ?", Integer.class, dia.plusDays(1))).isEqualTo(1);
    }

    @Test void importaGeoJsonDoClienteOficialSemTipoNasPropriedades() {
        String gers = UUID.randomUUID().toString();
        var feature = featureClienteOficial(gers);
        var item = importacao.importar(REVISOR, lote("2026-10-21.0", feature)).itens().getFirst();
        assertThat(item.restauranteId()).isNotNull();
        assertThat(restaurantes.findByOvertureId(gers)).isPresent();
        assertThat(importacao.importar(REVISOR, lote("2026-10-21.0", feature)).itens().getFirst().resultado())
                .isEqualTo("INALTERADO");
    }

    @Test void importacaoContinuaIgnorandoTipoExplicitamenteDiferenteDePlace() {
        String gers = UUID.randomUUID().toString();
        var feature = json.readTree(featureClienteOficial(gers).toString()
                .replace("\"basic_category\":", "\"type\":\"building\",\"basic_category\":"));
        assertThat(importacao.importar(REVISOR, lote("2026-10-21.0", feature)).itens().getFirst().resultado())
                .isEqualTo("IGNORADO_TIPO");
        assertThat(restaurantes.findByOvertureId(gers)).isEmpty();
    }

    @Test void importacaoRepetivelAtualizaSemApagarCorrecaoOuRejeicao() {
        String gers = UUID.randomUUID().toString();
        var feature = feature(gers, "Restaurante Overture " + UUID.randomUUID(), 1);
        var lote = lote("2026-10-21.0", feature);
        var primeiro = importacao.importar(REVISOR, lote).itens().getFirst();
        UUID id = primeiro.restauranteId();
        assertThat(id).isNotNull();
        assertThat(importacao.importar(REVISOR, lote).itens().getFirst().resultado()).isEqualTo("INALTERADO");
        var r = restaurantes.findById(id).orElseThrow();
        revisao.revisar(REVISOR, id, new RevisaoRestauranteRequest(r.getVersao(),
                SituacaoRestaurante.REJEITADO, "Revisão de teste", new RevisaoRestauranteRequest.Correcao(
                "Nome corrigido próprio", -2.53, -44.30, CategoriaRestaurante.CAFE)));
        var novo = feature(gers, "Nome atualizado pela fonte", 2);
        assertThat(importacao.importar(REVISOR, lote("2026-11-18.0", novo)).itens().getFirst().restauranteId()).isEqualTo(id);
        var atualizado = restaurantes.findById(id).orElseThrow();
        assertThat(atualizado.getNome()).isEqualTo("Nome corrigido próprio");
        assertThat(atualizado.getSituacao()).isEqualTo(SituacaoRestaurante.REJEITADO);
        assertThat(atualizado.getOvertureRelease()).isEqualTo("2026-11-18.0");
        assertThat(atualizado.getFontesOverture()).contains("dataset");
        assertThat(atualizado.getLicencasOverture()).contains("CDLA-Permissive-2.0");
        assertThat(importacao.importar(REVISOR, lote).itens().getFirst().resultado()).isEqualTo("IGNORADO_ANTIGO");
    }

    @Test void importacaoConservaMealsERejeitaLoteMalformadoAtomicamente() {
        String gers = UUID.randomUUID().toString();
        var primeiro = importacao.importar(REVISOR, lote("2026-10-21.0", feature(gers, UUID.randomUUID().toString(), 1)))
                .itens().getFirst();
        var meal = publicar(primeiro.restauranteId());
        importacao.importar(REVISOR, lote("2026-11-18.0", feature(gers, "Atualizado " + UUID.randomUUID(), 2)));
        assertThat(jdbc.queryForObject("select restaurante_id from meals where id = ?", UUID.class, meal.id())).isEqualTo(primeiro.restauranteId());
        String novo = UUID.randomUUID().toString();
        var invalido = json.readTree("{\"type\":\"Feature\",\"properties\":{}}");
        assertThatThrownBy(() -> importacao.importar(REVISOR,
                new ImportacaoOvertureRequest("2026-10-21.0", List.of("CDLA-Permissive-2.0"),
                        List.of(feature(novo, UUID.randomUUID().toString(), 1), invalido)))).isInstanceOf(RegraNegocioException.class);
        assertThat(restaurantes.findByOvertureId(novo)).isEmpty();
    }

    @Test void revisaoExigePermissaoEVersaoImpedeNovaMealSemApagarHistorico() {
        var r = cadastrar(UUID.randomUUID().toString()).restaurante();
        var meal = publicar(r.id());
        var pedido = new RevisaoRestauranteRequest(r.versao(), SituacaoRestaurante.REJEITADO, "Não é restaurante", null);
        assertThatThrownBy(() -> revisao.revisar(autor, r.id(), pedido)).isInstanceOf(RegraNegocioException.class);
        revisao.revisar(REVISOR, r.id(), pedido);
        assertThatThrownBy(() -> publicar(r.id())).isInstanceOf(RegraNegocioException.class);
        assertThat(jdbc.queryForObject("select count(*) from meals where id = ?", Integer.class, meal.id())).isEqualTo(1);
        assertThat(service.buscar(r.nome(), null, false, autor).locais()).isEmpty();
        assertThatThrownBy(() -> revisao.revisar(REVISOR, r.id(), pedido)).isInstanceOf(RegraNegocioException.class);
        assertThat(jdbc.queryForObject("select count(*) from revisoes_restaurantes where restaurante_id = ?", Integer.class, r.id())).isEqualTo(1);
    }

    @Test void malhaRecusaPinForaDaCidadeOuNaoFinito() {
        assertThat(area.contem(-2.53, -44.30)).isTrue();
        assertThat(area.contem(-23.55, -46.63)).isFalse();
        assertThat(area.contem(Double.NaN, -44.30)).isFalse();
        assertThatThrownBy(() -> cadastros.cadastrar(autor, "fora", manual("Fora", -23.55, -46.63, Set.of())))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test void ilhaInteiraAceitaOsQuatroMunicipiosERecusaForaComCodigoProprio() {
        // T03: São Luís, São José de Ribamar, Paço do Lumiar e Raposa (malha IBGE máxima).
        assertThat(area.contem(-2.49908, -44.24030)).as("Coisas do Pará, São Luís").isTrue();
        assertThat(area.contem(-2.5617, -44.0542)).as("centro de São José de Ribamar").isTrue();
        assertThat(area.contem(-2.5320, -44.1078)).as("centro de Paço do Lumiar").isTrue();
        assertThat(area.contem(-2.4254, -44.0973)).as("centro de Raposa").isTrue();
        assertThat(area.contem(-2.4700, -44.2050)).as("Araçagi").isTrue();
        assertThat(area.contem(-3.7319, -38.5267)).as("Fortaleza").isFalse();
        assertThat(area.nome()).isEqualTo("Grande São Luís");
        assertThatThrownBy(() -> cadastros.cadastrar(autor, "fortaleza", manual("Fora " + autor, -3.7319, -38.5267, Set.of())))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("fora_da_area");
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getMessage()).isEqualTo("O Jambu ainda está só na Grande São Luís.");
                });
        assertThat(cadastros.cadastrar(autor, "ribamar", manual("Ribamar " + autor, -2.5617, -44.0542, Set.of())).situacao())
                .isEqualTo("CRIADO");
    }

    @Test void nomeDeUsuarioNaoConcedeNemRemovePermissaoDeRevisor() {
        jdbc.update("update usuarios set username = ? where id = ?", "rev_" + UUID.randomUUID().toString().substring(0, 8), REVISOR);
        jdbc.update("update usuarios set username = 'revisor_catalogo_fixture' where id = ?", autor);
        assertThatThrownBy(() -> revisao.listar(autor, SituacaoRestaurante.PROVISORIO, 0, 20))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(revisao.listar(REVISOR, SituacaoRestaurante.PROVISORIO, 0, 20)).isNotNull();
        jdbc.update("update usuarios set username = ? where id = ?", "comum_" + UUID.randomUUID().toString().substring(0, 8), autor);
    }

    @Test void contratoHttpProtegeCadastroEAdministracao() throws Exception {
        String username = jdbc.queryForObject("select username from usuarios where id = ?", String.class, autor);
        String token = auth.login(new LoginRequest(username, SENHA)).token();
        String corpo = json.writeValueAsString(manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of()));
        assertThat(http("POST", "/api/restaurantes/cadastros", null, corpo, "http").statusCode()).isEqualTo(401);
        assertThat(http("POST", "/api/restaurantes/cadastros", token, corpo, null).statusCode()).isEqualTo(400);
        var criado = http("POST", "/api/restaurantes/cadastros", token, corpo, "http");
        assertThat(criado.statusCode()).isEqualTo(200);
        assertThat(criado.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(http("GET", "/api/moderacao/restaurantes", token, null, null).statusCode()).isEqualTo(403);
        assertThat(http("POST", "/api/moderacao/restaurantes/importacoes/overture", token,
                json.writeValueAsString(lote("2026-10-21.0", feature(UUID.randomUUID().toString(), "Importação", 1))), null).statusCode()).isEqualTo(403);
        var extra = corpo.substring(0, corpo.length() - 1) + ",\"origem\":\"OVERTURE\"}";
        assertThat(http("POST", "/api/restaurantes/cadastros", token, extra, "forjado").statusCode()).isEqualTo(400);
    }

    private CadastroRestauranteResponse cadastrar(String nome) {
        return cadastros.cadastrar(autor, UUID.randomUUID().toString(), manual(nome, -2.53, -44.30, Set.of()));
    }
    private CadastroRestauranteRequest manual(String nome, double lat, double lon, Set<UUID> confirmados) {
        return new CadastroRestauranteRequest(nome, lat, lon, CategoriaRestaurante.RESTAURANTE, null, null, confirmados);
    }
    private MealResponse publicar(UUID restauranteId) {
        return meals.publicar(autor, UUID.randomUUID().toString(), new MealRequest(restauranteId,
                List.of(new DishRequest(null, "Prato", new BigDecimal("4.5"))), null, true, null, null, null));
    }
    private ImportacaoOvertureRequest lote(String release, JsonNode feature) {
        return new ImportacaoOvertureRequest(release, List.of("CDLA-Permissive-2.0"), List.of(feature));
    }
    private JsonNode feature(String id, String nome, int versao) {
        return json.valueToTree(Map.of("type", "Feature", "geometry", Map.of("type", "Point", "coordinates", List.of(-44.30, -2.53)),
                "properties", Map.of("id", id, "type", "place", "version", versao, "names", Map.of("primary", nome),
                        "basic_category", "casual_eatery", "taxonomy", Map.of("primary", "casual_eatery", "hierarchy", List.of("food_and_drink", "restaurant")),
                        "sources", List.of(Map.of("dataset", "meta", "record_id", "fixture")), "confidence", 0.95)));
    }
    private JsonNode featureClienteOficial(String id) {
        // Estrutura do export overturemaps 1.0.2, com dados inteiramente fictícios.
        return json.valueToTree(Map.of("type", "Feature", "id", id,
                "geometry", Map.of("type", "Point", "coordinates", List.of(-44.30, -2.53)),
                "properties", Map.of("version", 1, "names", Map.of("primary", id),
                        "basic_category", "restaurant", "sources", List.of(Map.of(
                        "dataset", "meta", "license", "CDLA-Permissive-2.0", "record_id", "fixture")))));
    }
    private <T> List<T> paralelo(int quantidade, java.util.function.IntFunction<T> operacao) throws Exception {
        try (var executor = Executors.newFixedThreadPool(Math.min(12, quantidade))) {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) { final int n = i; futuros.add(executor.submit(() -> operacao.apply(n))); }
            List<T> valores = new ArrayList<>();
            for (var f : futuros) valores.add(f.get(30, TimeUnit.SECONDS));
            return valores;
        }
    }
    private HttpResponse<String> http(String metodo, String path, String token, String corpo, String chave) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + path)).header("Content-Type", "application/json");
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (chave != null) req.header("Idempotency-Key", chave);
        req.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
        try (var client = HttpClient.newHttpClient()) { return client.send(req.build(), HttpResponse.BodyHandlers.ofString()); }
    }
}
