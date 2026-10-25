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

    @Test void googleSoComTresLetrasOuMais() {
        // T02: a busca local começa com 2 letras; o Google, só com 3.
        int antes = places.chamadas.get();
        var curta = service.buscar("qx", null, false, autor);
        assertThat(curta.estadoExterno()).isEqualTo("TERMO_CURTO");
        assertThat(curta.itens()).isEmpty();
        assertThat(service.buscar("qx", curta.tokenSessao(), true, autor).estadoExterno()).isEqualTo("TERMO_CURTO");
        assertThat(places.chamadas.get()).isEqualTo(antes);
        assertThat(service.buscar("qxw" + UUID.randomUUID().toString().substring(0, 4), null, false, autor).estadoExterno())
                .isNotEqualTo("TERMO_CURTO");
        assertThat(places.chamadas.get()).isEqualTo(antes + 1);
    }

    @Test void buscaAmpliadaLimitadaPorPessoaPorDia() {
        // T02: até 30 buscas no Google por pessoa por dia; depois, só a busca local e "Adicionar".
        for (int i = 0; i < 30; i++) {
            clock.agora = clock.agora.plusSeconds(2);
            assertThat(service.buscar("sem local " + UUID.randomUUID(), null, false, autor).estadoExterno()).isEqualTo("DISPONIVEL");
        }
        int antes = places.chamadas.get();
        clock.agora = clock.agora.plusSeconds(2);
        var bloqueada = service.buscar("sem local " + UUID.randomUUID(), null, false, autor);
        assertThat(bloqueada.estadoExterno()).isEqualTo("LIMITE_DIARIO");
        assertThat(bloqueada.podeAdicionar()).isTrue();
        assertThat(places.chamadas.get()).isEqualTo(antes);
        clock.agora = clock.agora.plusSeconds(86400);
        assertThat(service.buscar("sem local " + UUID.randomUUID(), null, false, autor).estadoExterno()).isEqualTo("DISPONIVEL");
        assertThat(config.limiteGoogleDia()).isEqualTo(300);
    }

    @Test void buscaToleraErroDeDigitacaoSemChamarGoogle() {
        // T06: trigramas (pg_trgm) perdoam "burguer" no lugar de "burger".
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        var criado = cadastros.cadastrar(autor, UUID.randomUUID().toString(), manual("Burger Night " + sufixo, -2.531, -44.301, Set.of()));
        var busca = service.buscar("burguer nigth " + sufixo, null, false, autor);
        assertThat(busca.locais()).extracting(RestauranteResumo::id).contains(criado.restaurante().id());
        assertThat(busca.estadoExterno()).isEqualTo("NAO_SOLICITADO");
        assertThat(places.chamadas.get()).isZero();
    }

    @Test void buscaAchaRestaurantePeloNomeDePratoJaAvaliado() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        var criado = cadastros.cadastrar(autor, UUID.randomUUID().toString(), manual("Casa Paraense " + sufixo, -2.532, -44.302, Set.of()));
        meals.publicar(autor, UUID.randomUUID().toString(), new MealRequest(criado.restaurante().id(),
                List.of(new DishRequest(null, "Tacacá " + sufixo, new BigDecimal("4.5"))), null, true, null, null, null));
        var busca = service.buscar("tacaca " + sufixo, null, false, autor);
        assertThat(busca.locais()).extracting(RestauranteResumo::id).containsExactly(criado.restaurante().id());
        assertThat(places.chamadas.get()).isZero();
    }

    @Test void provisoriosAparecemDepoisDosVerificados() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        var provisorio = cadastros.cadastrar(autor, UUID.randomUUID().toString(), manual("Ponto " + sufixo + " Centro", -2.533, -44.303, Set.of()));
        var verificado = cadastros.cadastrar(autor, UUID.randomUUID().toString(), manual("Ponto " + sufixo + " Praia", -2.50, -44.27, Set.of()));
        jdbc.update("update restaurantes set situacao = 'ATIVO' where id = ?", verificado.restaurante().id());
        var busca = service.buscar("ponto " + sufixo, null, false, autor);
        assertThat(busca.locais()).extracting(RestauranteResumo::id)
                .containsExactly(verificado.restaurante().id(), provisorio.restaurante().id());
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
        // O teto vem da configuração (300 desde a T02); 20 tentativas a mais precisam ser negadas.
        int teto = config.limiteGoogleDia();
        var resultados = paralelo(teto + 20, i -> {
            try { cota.reservar(); return "reservada"; }
            catch (PlacesException ex) { assertThat(ex.getCodigo()).isEqualTo("places_cota_esgotada"); return "negada"; }
        });
        assertThat(resultados.stream().filter("reservada"::equals).count()).isEqualTo(teto);
        var outraInstancia = new CotaPlacesService(jdbc, clock, config);
        assertThatThrownBy(outraInstancia::reservar).isInstanceOf(PlacesException.class);
        clock.agora = dia.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        jdbc.update("delete from consumo_places_diario where dia = ?", dia.plusDays(1));
        cota.reservar();
        assertThat(jdbc.queryForObject("select chamadas from consumo_places_diario where dia = ?", Integer.class, dia)).isEqualTo(teto);
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

    @Test void cadastroDeLugarNovoLimitadoACincoPorDiaCom429() throws Exception {
        // T09: até 5 lugares novos por pessoa por dia (fuso de São Luís); o 6º recebe 429 com Retry-After.
        String username = jdbc.queryForObject("select username from usuarios where id = ?", String.class, autor);
        String token = auth.login(new LoginRequest(username, SENHA)).token();
        for (int i = 0; i < 5; i++)
            assertThat(cadastros.cadastrar(autor, "lim-" + i, manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of())).situacao())
                    .isEqualTo("CRIADO");
        assertThatThrownBy(() -> cadastros.cadastrar(autor, "lim-6", manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of())))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(429);
                    assertThat(ex.getCodigo()).isEqualTo("limite_cadastros");
                });
        var http = http("POST", "/api/restaurantes/cadastros", token,
                json.writeValueAsString(manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of())), "lim-http");
        assertThat(http.statusCode()).isEqualTo(429);
        // 15:00Z = 12:00 em São Luís; faltam 12 h para a meia-noite local.
        assertThat(http.headers().firstValue("Retry-After")).hasValue("43200");
        assertThat(http.body()).contains("limite_cadastros");
        // T02: acima do limite, a escolha de um resultado do Google é recusada antes de chamar o Google.
        int chamadasAntes = places.chamadas.get();
        assertThatThrownBy(() -> cadastros.cadastrar(autor, "lim-google", new CadastroRestauranteRequest(UUID.randomUUID().toString(),
                -2.53, -44.30, CategoriaRestaurante.RESTAURANTE, "limite_" + UUID.randomUUID(), UUID.randomUUID(), Set.of())))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("limite_cadastros"));
        assertThat(places.chamadas.get()).isEqualTo(chamadasAntes);
        clock.agora = clock.agora.plusSeconds(43200);
        assertThat(cadastros.cadastrar(autor, "lim-amanha", manual(UUID.randomUUID().toString(), -2.53, -44.30, Set.of())).situacao())
                .isEqualTo("CRIADO");
    }

    @Test void categoriasSaoSoAsSeisDoEnum() throws Exception {
        // O enum é a lista de categorias: fora dele, a API, o banco e a importação recusam.
        assertThat(CategoriaRestaurante.values()).extracting(Enum::name)
                .containsExactly("RESTAURANTE", "CAFE", "PADARIA", "BAR", "LANCHONETE", "SORVETERIA");
        String username = jdbc.queryForObject("select username from usuarios where id = ?", String.class, autor);
        String token = auth.login(new LoginRequest(username, SENHA)).token();
        var corpo = (tools.jackson.databind.node.ObjectNode) json.valueToTree(manual("Praça " + UUID.randomUUID(), -2.53, -44.30, Set.of()));
        corpo.put("categoria", "PRACA_ALIMENTACAO");
        var recusado = http("POST", "/api/restaurantes/cadastros", token, json.writeValueAsString(corpo), "praca-http");
        assertThat(recusado.statusCode()).isEqualTo(400);
        assertThat(recusado.body()).contains("requisicao_invalida");
        var criado = cadastros.cadastrar(autor, "categoria-banco", manual("Lugar " + UUID.randomUUID(), -2.53, -44.30, Set.of()));
        assertThatThrownBy(() -> jdbc.update("update restaurantes set categoria = 'PRACA_ALIMENTACAO' where id = ?", criado.restaurante().id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var praca = (tools.jackson.databind.node.ObjectNode) feature(UUID.randomUUID().toString(), "Praça " + UUID.randomUUID(), 1);
        ((tools.jackson.databind.node.ObjectNode) praca.path("properties")).put("basic_category", "food_court");
        ((tools.jackson.databind.node.ObjectNode) praca.path("properties")).set("taxonomy",
                json.valueToTree(Map.of("primary", "food_court", "hierarchy", List.of("food_and_drink", "food_court"))));
        assertThat(importacao.importar(REVISOR, lote("2026-10-21.0", praca)).itens().getFirst().resultado()).isEqualTo("IGNORADO_CATEGORIA");
    }

    @Test void segundaContaConfirmaOuSugereCorrecaoDoLugarProvisorio() throws Exception {
        // T08 (RN-57, R20): só outra conta com Meal ativa no lugar responde "Nome e bairro estão certos?".
        var lugar = cadastrar("Lugar provisório " + UUID.randomUUID()).restaurante().id();
        String caminho = "/api/restaurantes/" + lugar + "/retornos";
        String confirma = "{\"tipo\":\"CONFIRMACAO\"}";
        String corrige = "{\"tipo\":\"CORRECAO\",\"nomeSugerido\":\"Nome certo\",\"observacao\":\"O nome mudou\"}";
        publicar(lugar);
        String tokenAutor = token(autor);
        UUID semMeal = conta(), segunda = conta(), terceira = conta(), quarta = conta();
        meals.publicar(segunda, UUID.randomUUID().toString(), mealEm(lugar));
        meals.publicar(terceira, UUID.randomUUID().toString(), mealEm(lugar));
        meals.publicar(quarta, UUID.randomUUID().toString(), mealEm(lugar));
        assertThat(http("POST", caminho, null, confirma, null).statusCode()).isEqualTo(401);
        assertThat(codigo(http("POST", caminho, tokenAutor, confirma, null))).isEqualTo("acesso_negado");
        assertThat(codigo(http("POST", caminho, token(semMeal), confirma, null))).isEqualTo("acesso_negado");
        assertThat(codigo(http("POST", caminho, token(segunda), "{\"tipo\":\"CORRECAO\"}", null))).isEqualTo("dados_invalidos");
        assertThat(codigo(http("POST", "/api/restaurantes/" + UUID.randomUUID() + "/retornos", token(segunda), confirma, null)))
                .isEqualTo("recurso_inexistente");
        // "Corrigir" mantém provisório; repetir a mesma resposta é seguro; mudar de ideia é conflito.
        var correcao = http("POST", caminho, token(segunda), corrige, null);
        assertThat(correcao.statusCode()).isEqualTo(200);
        assertThat(json.readTree(correcao.body()).path("situacaoRestaurante").asString()).isEqualTo("PROVISORIO");
        assertThat(http("POST", caminho, token(segunda), corrige, null).statusCode()).isEqualTo(200);
        assertThat(codigo(http("POST", caminho, token(segunda), confirma, null))).isEqualTo("conflito");
        assertThat(restaurantes.findById(lugar).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.PROVISORIO);
        // "Sim" de outra conta verifica o lugar; depois disso não há mais o que confirmar.
        var sim = http("POST", caminho, token(terceira), confirma, null);
        assertThat(sim.statusCode()).isEqualTo(200);
        assertThat(json.readTree(sim.body()).path("situacaoRestaurante").asString()).isEqualTo("ATIVO");
        assertThat(http("POST", caminho, token(terceira), confirma, null).statusCode()).isEqualTo(200);
        assertThat(codigo(http("POST", caminho, token(quarta), confirma, null))).isEqualTo("conflito");
        assertThat(restaurantes.findById(lugar).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.ATIVO);
        assertThat(jdbc.queryForList("select usuario_id || ':' || tipo from retornos_restaurantes where restaurante_id = ?",
                String.class, lugar)).containsExactlyInAnyOrder(segunda + ":CORRECAO", terceira + ":CONFIRMACAO");
    }

    @Test void perguntaNomeEBairroSoParaSegundaContaQueAindaNaoRespondeu() throws Exception {
        // T08 (R20): quem cadastrou e quem não postou não veem a pergunta; ela some depois da resposta.
        var lugar = cadastrar("Lugar da pergunta " + UUID.randomUUID()).restaurante().id();
        String caminho = "/api/restaurantes/" + lugar + "/confirmacao";
        publicar(lugar);
        UUID semMeal = conta(), segunda = conta();
        assertThat(http("GET", caminho, null, null, null).statusCode()).isEqualTo(401);
        assertThat(podeConfirmar(caminho, token(autor))).isFalse();
        assertThat(podeConfirmar(caminho, token(semMeal))).isFalse();
        assertThat(podeConfirmar(caminho, token(segunda))).isFalse();
        meals.publicar(segunda, UUID.randomUUID().toString(), mealEm(lugar));
        assertThat(podeConfirmar(caminho, token(segunda))).isTrue();
        http("POST", "/api/restaurantes/" + lugar + "/retornos", token(segunda), "{\"tipo\":\"CORRECAO\",\"observacao\":\"Fechou\"}", null);
        assertThat(podeConfirmar(caminho, token(segunda))).isFalse();
        assertThat(codigo(http("GET", "/api/restaurantes/" + UUID.randomUUID() + "/confirmacao", token(segunda), null, null)))
                .isEqualTo("recurso_inexistente");
    }

    @Test void correcaoSugeridaEntraNaFilaDosFoundersESaiNaRevisao() throws Exception {
        // T08 (RN-58): "Corrigir" vai para a revisão dos founders; a revisão do lugar resolve a correção.
        var lugar = cadastrar("Lugar com nome errado " + UUID.randomUUID()).restaurante().id();
        UUID segunda = conta();
        meals.publicar(segunda, UUID.randomUUID().toString(), mealEm(lugar));
        http("POST", "/api/restaurantes/" + lugar + "/retornos", token(segunda),
                "{\"tipo\":\"CORRECAO\",\"nomeSugerido\":\"Nome certo\",\"observacao\":\"Na placa está outro nome\"}", null);
        assertThat(http("GET", "/api/moderacao/restaurantes/retornos", token(segunda), null, null).statusCode()).isEqualTo(403);
        var fila = revisao.correcoesPendentes(REVISOR, 0, 50);
        var item = fila.itens().stream().filter(c -> c.restauranteId().equals(lugar)).findFirst().orElseThrow();
        assertThat(item.nomeSugerido()).isEqualTo("Nome certo");
        assertThat(item.observacao()).isEqualTo("Na placa está outro nome");
        assertThat(item.usuarioId()).isEqualTo(segunda);
        assertThat(item.nomeAtual()).startsWith("Lugar com nome errado");
        var r = restaurantes.findById(lugar).orElseThrow();
        assertThat(item.versaoRestaurante()).isEqualTo(r.getVersao());
        revisao.revisar(REVISOR, lugar, new RevisaoRestauranteRequest(r.getVersao(), SituacaoRestaurante.ATIVO, "Nome conferido",
                new RevisaoRestauranteRequest.Correcao("Nome certo", -2.53, -44.30, CategoriaRestaurante.RESTAURANTE)));
        assertThat(revisao.correcoesPendentes(REVISOR, 0, 50).itens()).noneMatch(c -> c.restauranteId().equals(lugar));
        assertThat(jdbc.queryForObject("select resolvido_em is not null from retornos_restaurantes where restaurante_id = ?",
                Boolean.class, lugar)).isTrue();
    }

    @Test void importacaoSeparaOvertureEmFaixasDeConfianca() {
        // T04 (RN-43, R16): >= 0,7 verificado; 0,5-0,7 provisório; 0,3-0,5 escondido, fora da busca; < 0,3 fora.
        var ids = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var confiancas = List.of(0.8, 0.7, 0.5, 0.3, 0.29);
        var nomes = ids.stream().map(i -> "x" + i.replace("-", "")).toList();
        List<JsonNode> features = new ArrayList<>();
        for (int i = 0; i < 5; i++) features.add(comConfianca(feature(ids.get(i), nomes.get(i), 1), confiancas.get(i)));
        var lote = new ImportacaoOvertureRequest("2026-10-21.0", List.of("CDLA-Permissive-2.0"), features);
        assertThat(importacao.importar(REVISOR, lote).itens()).extracting(ImportacaoOvertureService.Item::resultado)
                .containsExactly("CRIADO", "CRIADO", "CRIADO_PROVISORIO", "ESCONDIDO", "IGNORADO_BAIXA_CONFIANCA");
        assertThat(restaurantes.findByOvertureId(ids.get(0)).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.ATIVO);
        assertThat(restaurantes.findByOvertureId(ids.get(1)).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.ATIVO);
        assertThat(restaurantes.findByOvertureId(ids.get(2)).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.PROVISORIO);
        assertThat(restaurantes.findByOvertureId(ids.get(3))).isEmpty();
        assertThat(restaurantes.findByOvertureId(ids.get(4))).isEmpty();
        assertThat(jdbc.queryForObject("select nome from referencias_overture where overture_id = ?", String.class, ids.get(3)))
                .isEqualTo(nomes.get(3));
        assertThat(jdbc.queryForObject("select count(*) from referencias_overture where overture_id = ?", Integer.class, ids.get(4))).isZero();
        // Confiança logo abaixo de 0,5 continua escondida, sem arredondar (caso real: 0,49973 derrubava o lote).
        String quase = UUID.randomUUID().toString();
        assertThat(importacao.importar(REVISOR, new ImportacaoOvertureRequest("2026-10-21.0", List.of("CDLA-Permissive-2.0"),
                List.of(comConfianca(feature(quase, "x" + quase.replace("-", ""), 1), 0.4997337758541107)))).itens().getFirst().resultado())
                .isEqualTo("ESCONDIDO");
        assertThat(jdbc.queryForObject("select confianca from referencias_overture where overture_id = ?", Double.class, quase))
                .isEqualTo(0.4997337758541107);
        // O escondido não aparece na busca nem como possível duplicata.
        assertThat(service.buscar(nomes.get(3), null, false, autor).locais()).isEmpty();
        // Reimportar o mesmo lote não duplica nada.
        assertThat(importacao.importar(REVISOR, lote).itens()).extracting(ImportacaoOvertureService.Item::resultado)
                .containsExactly("INALTERADO", "INALTERADO", "INALTERADO", "ESCONDIDO", "IGNORADO_BAIXA_CONFIANCA");
        assertThat(jdbc.queryForObject("select count(*) from referencias_overture where overture_id = ?", Integer.class, ids.get(3))).isEqualTo(1);
        // Se a confiança sobe numa release nova, o escondido vira restaurante provisório e sai da tabela escondida.
        var subiu = new ImportacaoOvertureRequest("2026-11-19.0", List.of("CDLA-Permissive-2.0"),
                List.of(comConfianca(feature(ids.get(3), nomes.get(3), 2), 0.6)));
        assertThat(importacao.importar(REVISOR, subiu).itens().getFirst().resultado()).isEqualTo("CRIADO_PROVISORIO");
        assertThat(restaurantes.findByOvertureId(ids.get(3)).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.PROVISORIO);
        assertThat(jdbc.queryForObject("select count(*) from referencias_overture where overture_id = ?", Integer.class, ids.get(3))).isZero();
        // Um restaurante que já existe não é escondido se a confiança cair.
        var caiu = new ImportacaoOvertureRequest("2026-12-17.0", List.of("CDLA-Permissive-2.0"),
                List.of(comConfianca(feature(ids.get(0), nomes.get(0), 2), 0.4)));
        assertThat(importacao.importar(REVISOR, caiu).itens().getFirst().resultado()).isEqualTo("IGNORADO_BAIXA_CONFIANCA");
        assertThat(restaurantes.findByOvertureId(ids.get(0)).orElseThrow().getSituacao()).isEqualTo(SituacaoRestaurante.ATIVO);
    }

    @Test void selecaoDoGoogleCasaComLugarEscondidoDoOverture() {
        // T05 (RN-50 caso b, RN-51, R21): o lugar escolhido no Google que existe escondido no Overture entra com o
        // nome e o pin do Overture, provisório; o Google só confirma o place_id, buscado com o nosso nome e ponto.
        String sufixo = UUID.randomUUID().toString().replace("-", "");
        String overtureId = UUID.randomUUID().toString(), nome = "Casa escondida " + sufixo, placeId = "ChIJ_casa_" + sufixo;
        assertThat(importacao.importar(REVISOR, new ImportacaoOvertureRequest("2026-10-21.0", List.of("CDLA-Permissive-2.0"),
                List.of(comConfianca(feature(overtureId, nome, 1), 0.4)))).itens().getFirst().resultado()).isEqualTo("ESCONDIDO");
        places.idsPorNome.put(nome, List.of("ChIJ_outro", placeId));
        long cotaAntes = consumoGoogleHoje();
        int detalhesAntes = places.chamadas.get();
        // A pessoa digitou com erro; os trigramas acham o escondido mesmo assim.
        var escolhido = service.selecionar(new SelecionarRestauranteRequest(placeId, UUID.randomUUID(), "casa escondda " + sufixo));
        assertThat(escolhido.nome()).isEqualTo(nome);
        assertThat(escolhido.origem()).isEqualTo("OVERTURE");
        assertThat(escolhido.situacao()).isEqualTo(SituacaoRestaurante.PROVISORIO);
        assertThat(escolhido.googlePlaceId()).isEqualTo(placeId);
        assertThat(escolhido.overtureId()).isEqualTo(overtureId);
        assertThat(escolhido.latitude()).isEqualTo(-2.53);
        assertThat(places.ultimoPonto).containsExactly(-2.53, -44.30);
        assertThat(jdbc.queryForObject("select count(*) from referencias_overture where overture_id = ?", Integer.class, overtureId)).isZero();
        assertThat(consumoGoogleHoje()).isEqualTo(cotaAntes);
        assertThat(places.chamadas.get()).isEqualTo(detalhesAntes);
        // Escolher de novo cai no caso (a): o mesmo restaurante, sem buscar no Google.
        int buscas = places.buscasIds.get();
        assertThat(service.selecionar(new SelecionarRestauranteRequest(placeId, UUID.randomUUID(), nome)).id()).isEqualTo(escolhido.id());
        assertThat(places.buscasIds.get()).isEqualTo(buscas);
    }

    @Test void selecaoSemCasamentoContinuaPedindoCadastro() {
        // T05: sem texto, sem candidato que case ou com o Google fora, a seleção continua 422 cadastro_necessario.
        String sufixo = UUID.randomUUID().toString().replace("-", "");
        String overtureId = UUID.randomUUID().toString(), nome = "Outra escondida " + sufixo;
        importacao.importar(REVISOR, new ImportacaoOvertureRequest("2026-10-21.0", List.of("CDLA-Permissive-2.0"),
                List.of(comConfianca(feature(overtureId, nome, 1), 0.35))));
        places.idsPorNome.put(nome, List.of("ChIJ_lugar_diferente"));
        String placeId = "ChIJ_sem_casamento_" + sufixo;
        int buscas = places.buscasIds.get();
        assertThatThrownBy(() -> service.selecionar(new SelecionarRestauranteRequest(placeId, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("cadastro_necessario"));
        assertThat(places.buscasIds.get()).isEqualTo(buscas);
        assertThatThrownBy(() -> service.selecionar(new SelecionarRestauranteRequest(placeId, UUID.randomUUID(), nome)))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("cadastro_necessario"));
        assertThat(places.buscasIds.get()).isGreaterThan(buscas);
        places.falhar = true;
        assertThatThrownBy(() -> service.selecionar(new SelecionarRestauranteRequest(placeId, UUID.randomUUID(), nome)))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("cadastro_necessario"));
        places.falhar = false;
        assertThat(jdbc.queryForObject("select count(*) from referencias_overture where overture_id = ?", Integer.class, overtureId)).isEqualTo(1);
        assertThat(restaurantes.findByOvertureId(overtureId)).isEmpty();
    }

    @Test void selecoesSimultaneasDoMesmoLugarNaoDuplicam() throws Exception {
        String sufixo = UUID.randomUUID().toString().replace("-", "");
        String overtureId = UUID.randomUUID().toString(), nome = "Concorrida " + sufixo, placeId = "ChIJ_concorrida_" + sufixo;
        importacao.importar(REVISOR, new ImportacaoOvertureRequest("2026-10-21.0", List.of("CDLA-Permissive-2.0"),
                List.of(comConfianca(feature(overtureId, nome, 1), 0.45))));
        places.idsPorNome.put(nome, List.of(placeId));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var largada = new CountDownLatch(1);
            List<Future<UUID>> futuros = new ArrayList<>();
            for (int i = 0; i < 4; i++) futuros.add(executor.submit(() -> {
                largada.await();
                return service.selecionar(new SelecionarRestauranteRequest(placeId, UUID.randomUUID(), nome)).id();
            }));
            largada.countDown();
            Set<UUID> ids = new HashSet<>();
            for (var f : futuros) ids.add(f.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from restaurantes where google_place_id = ?", Integer.class, placeId)).isEqualTo(1);
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

    private JsonNode comConfianca(JsonNode feature, double confianca) {
        ((tools.jackson.databind.node.ObjectNode) feature.path("properties")).put("confidence", confianca);
        return feature;
    }
    private long consumoGoogleHoje() {
        return jdbc.queryForObject("select coalesce(sum(chamadas), 0) from consumo_places_diario", Long.class);
    }
    private UUID conta() {
        String nome = "ret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA)).id();
    }
    private String token(UUID usuario) {
        String username = jdbc.queryForObject("select username from usuarios where id = ?", String.class, usuario);
        return auth.login(new LoginRequest(username, SENHA)).token();
    }
    private MealRequest mealEm(UUID restauranteId) {
        return new MealRequest(restauranteId, List.of(new DishRequest(null, "Prato", new BigDecimal("4"))), null, null, null, null, null);
    }
    private boolean podeConfirmar(String caminho, String token) throws Exception {
        var resposta = http("GET", caminho, token, null, null);
        assertThat(resposta.statusCode()).isEqualTo(200);
        return json.readTree(resposta.body()).path("podeConfirmar").asBoolean();
    }
    private String codigo(HttpResponse<String> resposta) {
        return json.readTree(resposta.body()).path("codigo").asString();
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
