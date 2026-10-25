package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import com.jambuapp.api.autenticacao.*;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.foto.*;
import com.jambuapp.api.meal.*;
import com.jambuapp.api.restaurante.*;
import com.jambuapp.api.restaurante.places.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.jambuapp.api.seguimento.*;
import com.jambuapp.api.usuario.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JambuApiApplicationTests {
    private static final String SENHA = "Senha-ficticia-123!";
    private static final Instant AGORA = Instant.parse("2026-10-19T15:00:00Z");
    private static final byte[] JPEG_TESTE = gerarJpegTeste(600, 800, Color.GRAY);
    @Autowired AutenticacaoService auth;
    @Autowired UsuarioService usuarios;
    @Autowired MealService meals;
    @Autowired ConsultaMealService consultas;
    @Autowired SeguimentoService seguimentos;
    @Autowired RestauranteRepository restaurantes;
    @Autowired RestauranteService restauranteService;
    @Autowired CadastroRestauranteService cadastros;
    @Autowired FotoService fotoService;
    @Autowired ModeracaoService moderacaoService;
    @Autowired ModeracaoTransacionalService moderacaoTransacional;
    @Autowired ProcessadorModeracao processadorModeracao;
    @Autowired ArmazenamentoFotos armazenamentoFotos;
    @Autowired ModeracaoSimulada moderacaoSimulada;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired RelogioTeste clock;
    @Autowired PlacesSimulado places;
    @LocalServerPort int porta;
    UUID restaurante;
    @BeforeEach
    void preparar() {
        clock.agora = AGORA;
        places.falhar = false;
        places.chamadas.set(0);
        moderacaoSimulada.falhar = false;
        moderacaoSimulada.sinais = Map.of();
        moderacaoSimulada.chamadas.set(0);
        restaurante = selecionar("teste_" + UUID.randomUUID()).id();
    }
    @AfterEach
    void restaurarRelogio() { clock.agora = AGORA; }

    @Test
    void cadastroNormalizaDadosProtegeHashERejeitaDuplicidades() {
        String nome = "Teste_" + UUID.randomUUID().toString().substring(0, 8);
        var conta = auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA));
        assertThat(conta.username()).isEqualTo(nome.toLowerCase(Locale.ROOT));
        assertThat(conta.email()).isEqualTo((nome + "@example.test").toLowerCase(Locale.ROOT));
        String hash = jdbc.queryForObject("select senha_hash from usuarios where id = ?", String.class, conta.id());
        assertThat(hash).startsWith("{pbkdf2}").doesNotContain(SENHA);
        assertThatThrownBy(() -> auth.cadastrar(new CadastroRequest(nome.toUpperCase(Locale.ROOT), "outro@example.test", "98999999999", SENHA)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> auth.cadastrar(new CadastroRequest("outro_" + UUID.randomUUID().toString().substring(0, 8), conta.email(), "98999999999", SENHA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessaoExpiraRevogaEGuardaSomenteHashDoToken() {
        var conta = conta();
        var sessao = auth.login(new LoginRequest(conta.email(), SENHA));
        var principal = auth.autenticar(sessao.token()).orElseThrow();
        String hash = jdbc.queryForObject("select token_hash from tokens_acesso where sessao_id = ?", String.class, principal.sessaoId());
        assertThat(hash).isEqualTo(Hash.sha256(sessao.token())).isNotEqualTo(sessao.token());
        clock.agora = sessao.expiraEm();
        assertThat(auth.autenticar(sessao.token())).isEmpty();
        clock.agora = AGORA;
        auth.revogar(principal.sessaoId());
        assertThat(auth.autenticar(sessao.token())).isEmpty();
        assertThatThrownBy(() -> auth.login(new LoginRequest(conta.username(), "incorreta")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void publicaPersisteIdempotenciaAposEdicaoEExclusao() {
        var autor = conta();
        var pedido = pedido(autor.id(), "5", "4", "1");
        var original = meals.publicar(autor.id(), "rede-instavel", pedido);
        assertThat(consultas.consultar(original.id())).isEqualTo(original);
        assertThat(original.notaOverall()).isEqualByComparingTo("3.33");
        assertThat(original.dataRefeicao()).isEqualTo(LocalDate.of(2026, 10, 19));
        var editado = editar(original, restaurante, "0.5");
        meals.editar(autor.id(), original.id(), editado);
        assertThat(meals.publicar(autor.id(), "rede-instavel", pedido)).isEqualTo(original);
        assertThatThrownBy(() -> meals.publicar(autor.id(), "rede-instavel", pedido(autor.id(), "1")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("idempotência");
        meals.excluir(autor.id(), original.id());
        assertThat(meals.publicar(autor.id(), "rede-instavel", pedido)).isEqualTo(original);
        assertThatThrownBy(() -> consultas.consultar(original.id())).isInstanceOf(RegraNegocioException.class);
        assertThat(totalMeals(autor.id())).isEqualTo(1);
    }

    @Test
    void dishGuardaComeriaDeNovoComoMarcacaoOpcionalEPrecoOpcional() {
        // "Comeria de novo" é uma marcação: marcado (true) ou desmarcado (false). Desmarcado não quer dizer
        // "não comeria" (a pessoa pode ter esquecido), então ausente vira false e nunca fica nulo. Preço continua opcional.
        UUID autor = conta().id();
        var pedido = new MealRequest(restaurante, List.of(
                new DishRequest(null, "Tacacá", new BigDecimal("4.5"), true, new BigDecimal("32.5")),
                new DishRequest(null, "Pato no tucupi", new BigDecimal("4"), false, null),
                new DishRequest(null, "Suco de cupuaçu", new BigDecimal("5"), null, null)),
                List.of(foto(autor)), null, null, null, null, null);
        var meal = meals.publicar(autor, "dish-completo", pedido);
        assertThat(meal.comeriaNovamente()).isFalse();
        assertThat(meal.dishes()).extracting(MealResponse.DishResponse::comeriaNovamente).containsExactly(true, false, false);
        assertThat(meal.dishes()).extracting(MealResponse.DishResponse::preco)
                .containsExactly(new BigDecimal("32.50"), null, null);
        assertThat(consultas.consultar(meal.id())).isEqualTo(meal);
        var linhas = jdbc.queryForList("select nome, comeria_novamente, preco from dishes where meal_id = ? order by ordem", meal.id());
        assertThat(linhas.get(1).get("comeria_novamente")).isEqualTo(false);
        assertThat(linhas.get(2).get("comeria_novamente")).isEqualTo(false);
        // O banco também não aceita nulo: as duas colunas são NOT NULL com padrão false (V7).
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.columns
                where column_name = 'comeria_novamente' and is_nullable = 'NO' and column_default = 'false'
                order by table_name""", String.class)).containsExactly("dishes", "meals");
        assertThat(linhas.get(1).get("preco")).isNull();
        // Pedido sem os campos novos continua com o mesmo JSON de antes (hash de idempotência estável).
        assertThat(json.writeValueAsString(new DishRequest(null, "Prato", new BigDecimal("4.5"))))
                .doesNotContain("comeriaNovamente", "preco");
        assertThatThrownBy(() -> meals.publicar(autor, "preco-negativo", new MealRequest(restaurante,
                List.of(new DishRequest(null, "Prato", new BigDecimal("4"), null, new BigDecimal("-1"))), List.of(foto(autor)),
                null, null, null, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void curtirEhUmaVezPorPessoaEDescurtirRemove() throws Exception {
        // T20: PUT e DELETE idempotentes; só contas autenticadas; Meal excluída não recebe curtida.
        var autor = conta();
        var leitor = conta();
        var meal = publicar(autor.id(), "4");
        String tokenAutor = auth.login(new LoginRequest(autor.username(), SENHA)).token();
        String tokenLeitor = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        String caminho = "/api/meals/" + meal.id() + "/curtida";
        assertThat(http("PUT", caminho, null, null, null).statusCode()).isEqualTo(401);
        assertThat(http("PUT", caminho, tokenLeitor, null, null).statusCode()).isEqualTo(204);
        assertThat(http("PUT", caminho, tokenLeitor, null, null).statusCode()).isEqualTo(204);
        assertThat(json.readTree(http("GET", "/api/meals/" + meal.id() + "/curtidas", tokenLeitor, null, null).body()).path("quantidade").asInt())
                .isEqualTo(1);
        assertThat(http("PUT", caminho, tokenAutor, null, null).statusCode()).isEqualTo(204);
        var resumoAutor = json.readTree(http("GET", "/api/meals/" + meal.id() + "/curtidas", tokenAutor, null, null).body());
        assertThat(resumoAutor.path("quantidade").asInt()).isEqualTo(2);
        assertThat(resumoAutor.path("curtidaPorMim").asBoolean()).isTrue();
        assertThat(http("DELETE", caminho, tokenLeitor, null, null).statusCode()).isEqualTo(204);
        assertThat(http("DELETE", caminho, tokenLeitor, null, null).statusCode()).isEqualTo(204);
        var resumoLeitor = json.readTree(http("GET", "/api/meals/" + meal.id() + "/curtidas", tokenLeitor, null, null).body());
        assertThat(resumoLeitor.path("quantidade").asInt()).isEqualTo(1);
        assertThat(resumoLeitor.path("curtidaPorMim").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from curtidas where meal_id = ?", Integer.class, meal.id())).isEqualTo(1);
        meals.excluir(autor.id(), meal.id());
        assertThat(http("PUT", caminho, tokenLeitor, null, null).statusCode()).isEqualTo(404);
        assertThat(http("PUT", "/api/meals/" + UUID.randomUUID() + "/curtida", tokenLeitor, null, null).statusCode()).isEqualTo(404);
    }

    @Test
    void feedTemAbasParaVoceESeguindoSemOsPropriosPosts() throws Exception {
        // T19 (RN-30 a RN-32, AC10, F1, F2): "Para você" mostra todos os outros; "Seguindo", só quem eu sigo;
        // nenhuma aba mostra os meus posts.
        var eu = conta();
        var seguido = conta();
        var outro = conta();
        var semSeguir = conta();
        seguimentos.seguir(eu.id(), seguido.id());
        var meu = publicar(eu.id(), "4");
        String nomeLugar = "Lugar do feed " + UUID.randomUUID();
        UUID lugar = cadastros.cadastrar(seguido.id(), UUID.randomUUID().toString(), new CadastroRestauranteRequest(nomeLugar,
                -2.53, -44.30, CategoriaRestaurante.RESTAURANTE, null, null, Set.of())).restaurante().id();
        var doSeguido = meals.publicar(seguido.id(), UUID.randomUUID().toString(), new MealRequest(lugar,
                List.of(new DishRequest(null, "Prato do feed", new BigDecimal("4.5"))), List.of(foto(seguido.id())),
                null, null, null, null, null));
        var doOutro = publicar(outro.id(), "3");
        String token = auth.login(new LoginRequest(eu.username(), SENHA)).token();
        assertThat(http("GET", "/api/feed?aba=PARA_VOCE", null, null, null).statusCode()).isEqualTo(401);
        assertThat(http("GET", "/api/feed", token, null, null).statusCode()).isEqualTo(400);
        assertThat(http("GET", "/api/feed?aba=TODOS", token, null, null).statusCode()).isEqualTo(400);
        assertThat(http("GET", "/api/feed?aba=PARA_VOCE&limite=999", token, null, null).statusCode()).isEqualTo(400);
        var paraVoce = feedCompleto(token, "PARA_VOCE");
        assertThat(paraVoce).contains(doSeguido.id(), doOutro.id()).doesNotContain(meu.id());
        assertThat(paraVoce).doesNotHaveDuplicates();
        assertThat(feedCompleto(token, "SEGUINDO")).containsExactly(doSeguido.id());
        // Quem não segue ninguém recebe a aba vazia; o app mostra "Siga pessoas para ver os posts delas aqui".
        String tokenSemSeguir = auth.login(new LoginRequest(semSeguir.username(), SENHA)).token();
        assertThat(feedCompleto(tokenSemSeguir, "SEGUINDO")).isEmpty();
        // O card traz a Meal e o nome do restaurante, que vem do catálogo do Jambu.
        var item = json.readTree(http("GET", "/api/feed?aba=SEGUINDO", token, null, null).body()).path("itens").get(0);
        assertThat(item.path("meal").path("id").asString()).isEqualTo(doSeguido.id().toString());
        assertThat(item.path("nomeRestaurante").asString()).isEqualTo(nomeLugar);
        // Post excluído sai do feed.
        meals.excluir(seguido.id(), doSeguido.id());
        assertThat(feedCompleto(token, "SEGUINDO")).isEmpty();
        assertThat(feedCompleto(token, "PARA_VOCE")).doesNotContain(doSeguido.id());
    }

    @Test
    void feedMostraTotalDeCurtidasECurtidaPorMim() throws Exception {
        // T19: o card do feed traz o total de curtidas e se a pessoa que vê já curtiu (RN-33: tocar de novo descurte).
        var leitor = conta();
        var autor = conta();
        var fa = conta();
        var post = publicar(autor.id(), "4");
        var semCurtidas = publicar(autor.id(), "3");
        String tokenLeitor = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        String tokenFa = auth.login(new LoginRequest(fa.username(), SENHA)).token();
        http("PUT", "/api/meals/" + post.id() + "/curtida", tokenLeitor, null, null);
        http("PUT", "/api/meals/" + post.id() + "/curtida", tokenFa, null, null);
        var item = itemDoFeed(tokenLeitor, post.id());
        assertThat(item.path("curtidas").asLong()).isEqualTo(2);
        assertThat(item.path("curtidaPorMim").asBoolean()).isTrue();
        var vazio = itemDoFeed(tokenLeitor, semCurtidas.id());
        assertThat(vazio.path("curtidas").asLong()).isZero();
        assertThat(vazio.path("curtidaPorMim").isBoolean()).isTrue();
        assertThat(vazio.path("curtidaPorMim").asBoolean()).isFalse();
        http("DELETE", "/api/meals/" + post.id() + "/curtida", tokenLeitor, null, null);
        var depois = itemDoFeed(tokenLeitor, post.id());
        assertThat(depois.path("curtidas").asLong()).isEqualTo(1);
        assertThat(depois.path("curtidaPorMim").asBoolean()).isFalse();
    }

    @Test
    void comentarEListarComentariosDeUmPost() throws Exception {
        // T21 (RN-34): qualquer conta comenta post ativo; a lista vai do mais antigo ao mais novo.
        var autor = conta();
        var leitor = conta();
        var post = publicar(autor.id(), "4");
        String tokenAutor = auth.login(new LoginRequest(autor.username(), SENHA)).token();
        String tokenLeitor = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        String caminho = "/api/meals/" + post.id() + "/comentarios";
        assertThat(http("POST", caminho, null, "{\"texto\":\"Oi\"}", "c1").statusCode()).isEqualTo(401);
        assertThat(http("POST", caminho, tokenLeitor, "{\"texto\":\"Oi\"}", null).statusCode()).isEqualTo(400);
        assertThat(http("POST", caminho, tokenLeitor, "{\"texto\":\"   \"}", "c-vazio").statusCode()).isEqualTo(400);
        assertThat(http("POST", caminho, tokenLeitor, "{\"texto\":\"" + "a".repeat(1001) + "\"}", "c-longo").statusCode()).isEqualTo(400);
        assertThat(http("POST", "/api/meals/" + UUID.randomUUID() + "/comentarios", tokenLeitor, "{\"texto\":\"Oi\"}", "c-404").statusCode())
                .isEqualTo(404);
        var primeiro = http("POST", caminho, tokenLeitor, "{\"texto\":\"  Que tacacá bonito!  \"}", "c1");
        assertThat(primeiro.statusCode()).isEqualTo(201);
        var corpo = json.readTree(primeiro.body());
        assertThat(corpo.path("texto").asString()).isEqualTo("Que tacacá bonito!");
        assertThat(corpo.path("autor").path("id").asString()).isEqualTo(leitor.id().toString());
        // Reenviar com a mesma chave devolve o mesmo comentário; outra mensagem com a mesma chave é conflito.
        var repetido = http("POST", caminho, tokenLeitor, "{\"texto\":\"Que tacacá bonito!\"}", "c1");
        assertThat(repetido.statusCode()).isEqualTo(201);
        assertThat(json.readTree(repetido.body()).path("id").asString()).isEqualTo(corpo.path("id").asString());
        assertThat(http("POST", caminho, tokenLeitor, "{\"texto\":\"Outro texto\"}", "c1").statusCode()).isEqualTo(409);
        clock.agora = clock.agora.plusSeconds(1);
        assertThat(http("POST", caminho, tokenAutor, "{\"texto\":\"Obrigado!\"}", "c2").statusCode()).isEqualTo(201);
        clock.agora = clock.agora.plusSeconds(1);
        assertThat(http("POST", caminho, tokenLeitor, "{\"texto\":\"Volto lá\"}", "c3").statusCode()).isEqualTo(201);
        var pagina1 = json.readTree(http("GET", caminho + "?limite=2", tokenLeitor, null, null).body());
        assertThat(pagina1.path("itens")).extracting(i -> i.path("texto").asString()).containsExactly("Que tacacá bonito!", "Obrigado!");
        var pagina2 = json.readTree(http("GET", caminho + "?limite=2&cursor=" + pagina1.path("proximoCursor").asString(), tokenLeitor, null, null).body());
        assertThat(pagina2.path("itens")).extracting(i -> i.path("texto").asString()).containsExactly("Volto lá");
        assertThat(pagina2.path("proximoCursor").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from comentarios where meal_id = ?", Integer.class, post.id())).isEqualTo(3);
        // Post excluído: sem comentários novos nem lista.
        meals.excluir(autor.id(), post.id());
        assertThat(http("GET", caminho, tokenLeitor, null, null).statusCode()).isEqualTo(404);
        assertThat(http("POST", caminho, tokenLeitor, "{\"texto\":\"Oi\"}", "c4").statusCode()).isEqualTo(404);
    }

    @Test
    void comentarioSoEhExcluidoPeloAutorDeleOuPeloDonoDoPost() throws Exception {
        // T21 (RN-34, AC13, F7): autor do comentário e dono do post excluem; uma terceira conta não.
        var dono = conta();
        var comentarista = conta();
        var terceira = conta();
        var post = publicar(dono.id(), "4");
        var outroPost = publicar(dono.id(), "3");
        String tokenDono = auth.login(new LoginRequest(dono.username(), SENHA)).token();
        String tokenComentarista = auth.login(new LoginRequest(comentarista.username(), SENHA)).token();
        String tokenTerceira = auth.login(new LoginRequest(terceira.username(), SENHA)).token();
        String base = "/api/meals/" + post.id() + "/comentarios";
        String c1 = json.readTree(http("POST", base, tokenComentarista, "{\"texto\":\"Primeiro\"}", "x1").body()).path("id").asString();
        String c2 = json.readTree(http("POST", base, tokenComentarista, "{\"texto\":\"Segundo\"}", "x2").body()).path("id").asString();
        String c3 = json.readTree(http("POST", base, tokenTerceira, "{\"texto\":\"Terceiro\"}", "x3").body()).path("id").asString();
        assertThat(http("DELETE", base + "/" + c1, null, null, null).statusCode()).isEqualTo(401);
        assertThat(http("DELETE", base + "/" + c1, tokenTerceira, null, null).statusCode()).isEqualTo(403);
        assertThat(http("DELETE", base + "/" + UUID.randomUUID(), tokenDono, null, null).statusCode()).isEqualTo(404);
        // O comentário precisa ser daquele post.
        assertThat(http("DELETE", "/api/meals/" + outroPost.id() + "/comentarios/" + c1, tokenDono, null, null).statusCode()).isEqualTo(404);
        assertThat(http("DELETE", base + "/" + c1, tokenComentarista, null, null).statusCode()).isEqualTo(204);
        assertThat(http("DELETE", base + "/" + c1, tokenComentarista, null, null).statusCode()).isEqualTo(204);
        assertThat(http("DELETE", base + "/" + c2, tokenDono, null, null).statusCode()).isEqualTo(204);
        assertThat(http("DELETE", base + "/" + c1, tokenTerceira, null, null).statusCode()).isEqualTo(403);
        var lista = json.readTree(http("GET", base, tokenTerceira, null, null).body());
        assertThat(lista.path("itens")).extracting(i -> i.path("id").asString()).containsExactly(c3);
        assertThat(jdbc.queryForObject("select count(*) from comentarios where meal_id = ? and excluido_em is not null",
                Integer.class, post.id())).isEqualTo(2);
    }

    @Test
    void feedMostraTotalDeComentariosAtivos() throws Exception {
        // T21: o card do feed traz quantos comentários o post tem; excluído não conta.
        var leitor = conta();
        var autor = conta();
        var post = publicar(autor.id(), "4");
        var semComentario = publicar(autor.id(), "3");
        String tokenLeitor = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        String base = "/api/meals/" + post.id() + "/comentarios";
        http("POST", base, tokenLeitor, "{\"texto\":\"Um\"}", "f1");
        String dois = json.readTree(http("POST", base, tokenLeitor, "{\"texto\":\"Dois\"}", "f2").body()).path("id").asString();
        assertThat(itemDoFeed(tokenLeitor, post.id()).path("comentarios").asLong()).isEqualTo(2);
        assertThat(itemDoFeed(tokenLeitor, semComentario.id()).path("comentarios").asLong()).isZero();
        http("DELETE", base + "/" + dois, tokenLeitor, null, null);
        assertThat(itemDoFeed(tokenLeitor, post.id()).path("comentarios").asLong()).isEqualTo(1);
    }

    @Test
    void dataDaRefeicaoNoFuturoERecusadaNaPublicacaoENaEdicao() {
        // AGORA = 19/10/2026 12:00 em São Luís.
        UUID autor = conta().id();
        var futuro = new MealRequest(restaurante, List.of(new DishRequest(null, "Prato", new BigDecimal("4"))),
                List.of(foto(autor)), null, true, null, java.time.LocalDate.of(2026, 10, 20), null);
        assertThatThrownBy(() -> meals.publicar(autor, "futuro", futuro))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> assertThat(ex.getMessage()).contains("futuro"));
        var hoje = meals.publicar(autor, "hoje", new MealRequest(restaurante, List.of(new DishRequest(null, "Prato", new BigDecimal("4"))),
                List.of(foto(autor)), null, true, null, java.time.LocalDate.of(2026, 10, 19), null));
        assertThat(hoje.dataRefeicao()).isEqualTo(java.time.LocalDate.of(2026, 10, 19));
        assertThatThrownBy(() -> meals.editar(autor, hoje.id(), new MealRequest(restaurante,
                List.of(new DishRequest(hoje.dishes().getFirst().id(), "Prato", new BigDecimal("4"))), fotosDa(hoje.id()), null, true, null,
                java.time.LocalDate.of(2026, 10, 25), hoje.versao())))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void mediasTemPesoIgualPorMealETrocaDeRestauranteEExclusaoRefletemNaConsulta() {
        var autor = conta();
        var primeira = publicar(autor.id(), "5");
        var segunda = publicar(autor.id(), "1", "1", "1");
        assertThat(restauranteService.consultar(restaurante).notaMedia()).isEqualByComparingTo("3.00");
        UUID outro = selecionar("outro_" + UUID.randomUUID()).id();
        meals.editar(autor.id(), primeira.id(), editar(primeira, outro, "4"));
        assertThat(restauranteService.consultar(restaurante).notaMedia()).isEqualByComparingTo("1.00");
        assertThat(restauranteService.consultar(outro).notaMedia()).isEqualByComparingTo("4.00");
        meals.excluir(autor.id(), segunda.id());
        assertThat(restauranteService.consultar(restaurante).notaMedia()).isNull();
        assertThat(restauranteService.consultar(restaurante).quantidadeAvaliacoes()).isZero();
        assertThat(consultas.listar(null, restaurante, null, null, 20).itens()).isEmpty();
        assertThat(usuarios.consultar(autor.id()).quantidadeMeals()).isEqualTo(1);
    }

    @Test
    void mediaDoRestauranteNaoReutilizaOverallArredondada() {
        UUID autor = conta().id();
        publicar(autor, "0.5", "0.5", "1");
        publicar(autor, "0.5");
        assertThat(restauranteService.consultar(restaurante).notaMedia()).isEqualByComparingTo("0.58");
    }

    @Test
    void autorVersaoEIdsDeDishesSaoConferidosNaEdicao() {
        UUID autor = conta().id();
        UUID terceiro = conta().id();
        var meal = publicar(autor, "5", "4");
        var outraMeal = publicar(autor, "1");
        var edicao = editar(meal, restaurante, "3");
        assertThatThrownBy(() -> meals.editar(terceiro, meal.id(), edicao)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> meals.excluir(terceiro, meal.id())).isInstanceOf(RegraNegocioException.class);
        var estranha = new MealRequest(restaurante, List.of(new DishRequest(outraMeal.dishes().getFirst().id(), "Prato", new BigDecimal("3"))),
                fotosDa(meal.id()), null, true, null, null, meal.versao());
        assertThatThrownBy(() -> meals.editar(autor, meal.id(), estranha)).isInstanceOf(RegraNegocioException.class);
        var alterada = meals.editar(autor, meal.id(), edicao);
        assertThat(alterada.versao()).isGreaterThan(meal.versao());
        assertThat(alterada.dishes()).hasSize(1);
        assertThat(alterada.dishes().getFirst().id()).isEqualTo(meal.dishes().getFirst().id());
        assertThatThrownBy(() -> meals.editar(autor, meal.id(), edicao)).isInstanceOf(RegraNegocioException.class);
        assertThat(jdbc.queryForObject("select count(*) from dishes where meal_id = ?", Integer.class, meal.id())).isEqualTo(1);
        assertThat(totalMeals(autor)).isEqualTo(2);
    }

    @Test
    void publicaMaisDeQuatroMealsConcorrentesNoMesmoDiaSemLimite() throws Exception {
        UUID autor = conta().id();
        var resultados = paralelo(8, i -> meals.publicar(autor, "simultanea-" + i, pedido(autor, "5")).id().toString());
        assertThat(new HashSet<>(resultados)).hasSize(8);
        assertThat(totalMeals(autor)).isEqualTo(8);
        UUID id = UUID.fromString(resultados.getFirst());
        meals.excluir(autor, id);
        assertThat(publicar(autor, "1")).isNotNull();
        assertThat(totalMeals(autor)).isEqualTo(9);
    }

    @Test
    void repeticoesSimultaneasPublicamUmaUnicaMeal() throws Exception {
        UUID autor = conta().id();
        var pedido = pedido(autor, "4.5");
        var resultados = paralelo(6, i -> meals.publicar(autor, "mesma-chave", pedido).id().toString());
        assertThat(new HashSet<>(resultados)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from meals where autor_id = ?", Integer.class, autor)).isEqualTo(1);
    }

    @Test
    void dataPadraoSegueSaoPauloEMantemRefeicaoPassadaSemLimite() {
        UUID autor = conta().id();
        clock.agora = Instant.parse("2026-10-20T02:59:59Z");
        var base = pedido(autor, "5");
        for (int i = 0; i < 6; i++) {
            // Cada post usa uma foto própria: a mesma foto não entra em dois posts (T16).
            var passado = new MealRequest(restaurante, base.dishes(), List.of(foto(autor)), null, true, null, LocalDate.of(2025, 1, 1), null);
            assertThat(meals.publicar(autor, "antes-" + i, passado).dataRefeicao()).isEqualTo(LocalDate.of(2025, 1, 1));
        }
        assertThat(publicar(autor, "1").dataRefeicao()).isEqualTo(LocalDate.of(2026, 10, 19));
        clock.agora = Instant.parse("2026-10-20T03:00:00Z");
        assertThat(publicar(autor, "1").dataRefeicao()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(totalMeals(autor)).isEqualTo(8);
    }

    @Test
    void seguimentoMudaTimelineECompartilhamentoECursorDesempataMesmoInstante() {
        UUID autor = conta().id();
        UUID seguido = conta().id();
        var propria = publicar(autor, "5");
        var externa = publicar(seguido, "3");
        publicar(seguido, "4");
        assertThat(consultas.listar(null, null, autor, null, 20).itens()).extracting(MealResponse::id).containsExactly(propria.id());
        assertThatThrownBy(() -> meals.gerarLink(autor, externa.id())).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> seguimentos.seguir(autor, autor)).isInstanceOf(RegraNegocioException.class);
        seguimentos.seguir(autor, seguido);
        seguimentos.seguir(autor, seguido);
        assertThat(usuarios.consultar(seguido).seguidores()).isEqualTo(1);
        assertThat(seguimentos.listar(seguido, true, 0, 20).itens()).extracting(UsuarioResumoResponse::id).containsExactly(autor);
        assertThat(seguimentos.listar(autor, false, 0, 20).itens()).extracting(UsuarioResumoResponse::id).containsExactly(seguido);
        assertThat(meals.gerarLink(autor, externa.id()).toString()).endsWith(externa.id().toString());
        Set<UUID> vistos = new HashSet<>();
        String cursor = null;
        do {
            var pagina = consultas.listar(null, null, autor, cursor, 1);
            assertThat(pagina.itens()).hasSize(1);
            assertThat(vistos.add(pagina.itens().getFirst().id())).isTrue();
            cursor = pagina.proximoCursor();
        } while (cursor != null);
        assertThat(vistos).hasSize(3);
        seguimentos.deixarDeSeguir(autor, seguido);
        assertThat(consultas.listar(null, null, autor, null, 20).itens()).hasSize(1);
        assertThat(usuarios.consultar(seguido).seguidores()).isZero();
    }

    @Test
    void falhaDePublicacaoNaoPersisteMealEPayloadInvalidoNaoChegaAoBanco() {
        UUID autor = conta().id();
        var base = pedido(autor, "5");
        var invalida = new MealRequest(UUID.randomUUID(), base.dishes(), base.fotoIds(), null, true, null, null, null);
        assertThatThrownBy(() -> meals.publicar(autor, "falha", invalida)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> publicar(autor, "0.75")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> meals.publicar(autor, "sem-dishes", new MealRequest(restaurante, List.of(), base.fotoIds(), null, true, null, null, null)))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(totalMeals(autor)).isZero();
        assertThat(meals.publicar(autor, "falha", base)).isNotNull();
    }

    @Test
    void contratoHttpProtegeLeituraValidaEntradasEOcultaDadosPrivados() throws Exception {
        var autor = conta();
        var sessao = auth.login(new LoginRequest(autor.username(), SENHA));
        String token = sessao.token();
        assertThat(http("GET", "/actuator/health", null, null, null).statusCode()).isEqualTo(200);
        assertThat(http("GET", "/api/restaurantes", null, null, null).statusCode()).isEqualTo(401);
        var perfil = http("GET", "/api/usuarios/" + autor.id(), token, null, null);
        assertThat(perfil.statusCode()).isEqualTo(200);
        assertThat(perfil.body()).doesNotContain("email", "celular", "senha", "token");
        assertThat(http("GET", "/api/usuarios/me", token, null, null).body()).contains(autor.email());
        assertThat(http("GET", "/api/timeline?limite=999", token, null, null).statusCode()).isEqualTo(400);
        assertThat(http("GET", "/api/timeline?cursor=invalido", token, null, null).statusCode()).isEqualTo(400);
        String corpo = json.writeValueAsString(pedido(autor.id(), "5"));
        assertThat(http("POST", "/api/meals", token, corpo, null).statusCode()).isEqualTo(400);
        assertThat(http("POST", "/api/meals", token, corpo.substring(0, corpo.length() - 1) + ",\"autorId\":\"" + autor.id() + "\"}", "fraude").statusCode()).isEqualTo(400);
        var post = http("POST", "/api/meals", token, corpo, "http-1");
        assertThat(post.statusCode()).isEqualTo(201);
        JsonNode node = json.readTree(post.body());
        assertThat(node.path("autor").path("id").asString()).isEqualTo(autor.id().toString());
        assertThat(http("GET", "/api/meals/" + node.path("id").asString(), token, null, null).statusCode()).isEqualTo(200);
        assertThat(http("POST", "/api/auth/logout", token, null, null).statusCode()).isEqualTo(204);
        assertThat(http("GET", "/api/usuarios/me", token, null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void edicoesConcorrentesNaoSobrescrevemVersaoDoOutroAutor() throws Exception {
        UUID autor = conta().id();
        var meal = publicar(autor, "5");
        var resultados = paralelo(2, i -> {
            try { meals.editar(autor, meal.id(), editar(meal, restaurante, i == 0 ? "1" : "2")); return "salva"; }
            catch (RegraNegocioException | org.springframework.dao.OptimisticLockingFailureException ex) { return "conflito"; }
        });
        assertThat(resultados).containsExactlyInAnyOrder("salva", "conflito");
        assertThat(consultas.consultar(meal.id()).versao()).isEqualTo(meal.versao() + 1);
    }

    @Test
    void edicaoReordenaDishesPreservaIdsEAdicionaNovo() {
        UUID autor = conta().id();
        var meal = publicar(autor, "5", "4");
        var primeiro = meal.dishes().getFirst();
        var segundo = meal.dishes().getLast();
        var request = new MealRequest(restaurante, List.of(
                new DishRequest(segundo.id(), segundo.nome(), segundo.nota()),
                new DishRequest(null, "Novo", new BigDecimal("3")),
                new DishRequest(primeiro.id(), primeiro.nome(), primeiro.nota())), fotosDa(meal.id()), null, true, null, null, meal.versao());
        meals.editar(autor, meal.id(), request);
        var persistida = consultas.consultar(meal.id());
        assertThat(persistida.dishes()).hasSize(3);
        assertThat(persistida.dishes().getFirst().id()).isEqualTo(segundo.id());
        assertThat(persistida.dishes().get(1).nome()).isEqualTo("Novo");
        assertThat(persistida.dishes().getLast().id()).isEqualTo(primeiro.id());
    }

    @Test
    void httpCadastroLoginAtualizacaoEAutorizacaoEntreContas() throws Exception {
        String nome = "http_" + UUID.randomUUID().toString().substring(0, 8);
        var cadastro = new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA);
        assertThat(http("POST", "/api/auth/cadastro", null,
                json.writeValueAsString(new CadastroRequest(nome, "invalido", "1", "curta")), null).statusCode()).isEqualTo(400);
        var criada = http("POST", "/api/auth/cadastro", null, json.writeValueAsString(cadastro), null);
        assertThat(criada.statusCode()).isEqualTo(201);
        UUID autor = UUID.fromString(json.readTree(criada.body()).path("id").asString());
        var login = http("POST", "/api/auth/login", null, json.writeValueAsString(new LoginRequest(nome, SENHA)), null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control")).hasValue("no-store");
        String token = json.readTree(login.body()).path("token").asString();
        var atualizado = http("PUT", "/api/usuarios/me", token,
                json.writeValueAsString(new AtualizarContaRequest(nome, "novo_" + nome + "@example.test", "98988888888")), null);
        assertThat(atualizado.statusCode()).isEqualTo(200);
        var meal = publicar(autor, "5");
        var terceiro = conta();
        String tokenTerceiro = auth.login(new LoginRequest(terceiro.username(), SENHA)).token();
        assertThat(http("PUT", "/api/meals/" + meal.id(), tokenTerceiro, json.writeValueAsString(editar(meal, restaurante, "1")), null).statusCode()).isEqualTo(403);
        assertThat(http("DELETE", "/api/meals/" + meal.id(), tokenTerceiro, null, null).statusCode()).isEqualTo(403);
        String modalidadeInvalida = json.writeValueAsString(pedido(autor, "5")).replace("\"modalidade\":null", "\"modalidade\":\"INVALIDA\"");
        assertThat(http("POST", "/api/meals", token, modalidadeInvalida, "enum-invalido").statusCode()).isEqualTo(400);
        assertThat(http("POST", "/api/meals", token, "{}", "vazio").statusCode()).isEqualTo(400);
    }

    @Test
    void referenciaLegadaPermaneceUnicaESnapshotNaoPersisteDadosGoogle() throws Exception {
        String placeId = "concorrente_" + UUID.randomUUID();
        var ids = paralelo(6, i -> selecionar(placeId).id().toString());
        assertThat(new HashSet<>(ids)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from restaurantes where google_place_id = ?", Integer.class, placeId)).isEqualTo(1);
        restaurante = UUID.fromString(ids.getFirst());
        assertThat(selecionar(placeId).id()).isEqualTo(restaurante);
        assertThat(jdbc.queryForMap("select nome, endereco from restaurantes where id = ?", restaurante).values()).containsOnlyNulls();
        UUID autor = conta().id();
        int chamadas = places.chamadas.get();
        var pedido = pedido(autor, "5");
        var meal = meals.publicar(autor, "snapshot-limpo", pedido);
        assertThat(meals.publicar(autor, "snapshot-limpo", pedido)).isEqualTo(meal);
        assertThat(places.chamadas.get()).isEqualTo(chamadas);
        String snapshot = jdbc.queryForObject("select resposta_json from publicacoes_idempotentes where autor_id = ?", String.class, autor);
        assertThat(snapshot).contains(placeId).doesNotContain("Nome externo", "Endereço externo", "googleMapsUri", "atribuicoes", "Provedor de teste");
    }

    @Test
    void perfilNuncaConsultaGoogleEMontaLinkDoMapsSemCobranca() {
        // Referência antiga só com place_id: o perfil não busca nome nem endereço no Google.
        int antes = places.chamadas.get();
        var semNome = restauranteService.consultar(restaurante);
        assertThat(places.chamadas.get()).isEqualTo(antes);
        assertThat(semNome.nome()).isNull();
        assertThat(semNome.dadosExternosDisponiveis()).isFalse();
        assertThat(semNome.atribuicoes()).isEmpty();
        assertThat(semNome.atribuicaoGoogleMaps()).isNull();
        assertThat(semNome.googleMapsUri()).hasToString("https://www.google.com/maps/search/?api=1&query=restaurante&query_place_id="
                + semNome.googlePlaceId());

        jdbc.update("update restaurantes set nome = 'Coisas do Pará', nome_normalizado = 'coisas do para' where id = ?", restaurante);
        assertThat(restauranteService.consultar(restaurante).googleMapsUri()).hasToString(
                "https://www.google.com/maps/search/?api=1&query=Coisas%20do%20Par%C3%A1&query_place_id=" + semNome.googlePlaceId());

        places.falhar = true;
        var proprio = cadastros.cadastrar(conta().id(), "maps-" + UUID.randomUUID(), new CadastroRestauranteRequest(
                "Cantinho " + UUID.randomUUID(), -2.5048, -44.2462, CategoriaRestaurante.RESTAURANTE, null, null, Set.of()));
        var perfilProprio = restauranteService.consultar(proprio.restaurante().id());
        assertThat(perfilProprio.googleMapsUri()).hasToString("https://www.google.com/maps/search/?api=1&query=-2.5048%2C-44.2462");
        assertThat(places.chamadas.get()).isEqualTo(antes);
    }

    @Test
    void perfilInformaDataDaUltimaAvaliacaoAtiva() {
        assertThat(restauranteService.consultar(restaurante).ultimaAvaliacaoEm()).isNull();
        UUID autor = conta().id();
        var primeira = publicar(autor, "4");
        clock.agora = AGORA.plusSeconds(3600);
        var segunda = publicar(autor, "5");
        assertThat(restauranteService.consultar(restaurante).ultimaAvaliacaoEm()).isEqualTo(segunda.publicadaEm());
        meals.excluir(autor, segunda.id());
        assertThat(restauranteService.consultar(restaurante).ultimaAvaliacaoEm()).isEqualTo(primeira.publicadaEm());
    }

    @Test
    void tipoInvalidoEFalhaExternaNaoCriamReferenciaNemImpedemDadosLocais() {
        long antes = restaurantes.count();
        var autorCadastro = conta().id();
        assertThatThrownBy(() -> cadastros.cadastrar(autorCadastro, "tipo", cadastroGoogle("nao_permitido_" + UUID.randomUUID())))
                .isInstanceOf(PlacesException.class);
        assertThat(restaurantes.count()).isEqualTo(antes);
        UUID autor = conta().id();
        var meal = publicar(autor, "1");
        places.falhar = true;
        var fallback = cadastros.cadastrar(autorCadastro, "falha", cadastroGoogle("falha_" + UUID.randomUUID()));
        assertThat(fallback.situacao()).isEqualTo("CRIADO_SEM_GOOGLE");
        assertThat(fallback.restaurante().googlePlaceId()).isNull();
        assertThat(restauranteService.buscar("arroz", UUID.randomUUID()).estadoExterno()).isEqualTo("INDISPONIVEL");
        assertThat(restaurantes.count()).isEqualTo(antes + 1);
        var perfil = restauranteService.consultar(restaurante);
        assertThat(perfil.dadosExternosDisponiveis()).isFalse();
        assertThat(perfil.nome()).isNull();
        assertThat(perfil.notaMedia()).isEqualByComparingTo("1");
        assertThat(perfil.quantidadeAvaliacoes()).isEqualTo(1);
        assertThat(consultas.consultar(meal.id())).isEqualTo(meal);
        assertThat(publicar(autor, "2")).isNotNull();
        assertThat(restauranteService.consultar(restaurante).notaMedia()).isEqualByComparingTo("1.5");
    }

    @Test
    void snapshotAntigoContinuaRepetivelSemReescreverHashOuConteudoPersistido() {
        UUID autor = conta().id();
        var pedido = pedido(autor, "5");
        var original = meals.publicar(autor, "chave-antiga", pedido);
        var node = (tools.jackson.databind.node.ObjectNode) json.valueToTree(original);
        var referenciaAntiga = (tools.jackson.databind.node.ObjectNode) node.get("restaurante");
        referenciaAntiga.remove("googlePlaceId");
        referenciaAntiga.put("nome", "Restaurante curado antigo");
        referenciaAntiga.put("endereco", "Endereço curado antigo");
        String antigo = json.writeValueAsString(node);
        jdbc.update("update publicacoes_idempotentes set resposta_json = ? where autor_id = ?", antigo, autor);
        String hash = jdbc.queryForObject("select hash_requisicao from publicacoes_idempotentes where autor_id = ?", String.class, autor);
        meals.excluir(autor, original.id());
        var repetida = meals.publicar(autor, "chave-antiga", pedido);
        assertThat(repetida.id()).isEqualTo(original.id());
        assertThat(repetida.restaurante().id()).isEqualTo(restaurante);
        assertThat(repetida.restaurante().googlePlaceId()).isNull();
        assertThat(json.writeValueAsString(repetida)).doesNotContain("Restaurante curado antigo", "Endereço curado antigo");
        assertThat(jdbc.queryForObject("select resposta_json from publicacoes_idempotentes where autor_id = ?", String.class, autor)).isEqualTo(antigo);
        assertThat(jdbc.queryForObject("select hash_requisicao from publicacoes_idempotentes where autor_id = ?", String.class, autor)).isEqualTo(hash);
        assertThat(totalMeals(autor)).isEqualTo(1);
    }

    @Test
    void legadoComDadosPropriosContinuaLegivelEPermitePublicacaoSemPlaces() {
        UUID autor = conta().id();
        var antiga = publicar(autor, "4");
        jdbc.update("update restaurantes set google_place_id = null, places_validado_em = null, origem = 'LEGADO', nome = 'Nome próprio legado', endereco = 'Endereço próprio' where id = ?", restaurante);
        int chamadas = places.chamadas.get();
        var perfil = restauranteService.consultar(restaurante);
        assertThat(perfil.origem()).isEqualTo("LEGADO");
        assertThat(perfil.nome()).isEqualTo("Nome próprio legado");
        assertThat(places.chamadas.get()).isEqualTo(chamadas);
        assertThat(consultas.consultar(antiga.id()).restaurante().googlePlaceId()).isNull();
        assertThat(meals.editar(autor, antiga.id(), editar(antiga, restaurante, "3")).notaOverall()).isEqualByComparingTo("3");
        assertThat(publicar(autor, "5").restaurante().id()).isEqualTo(restaurante);
        UUID legado = restaurante;
        restaurante = selecionar("novo_" + UUID.randomUUID()).id();
        var nova = publicar(autor, "5");
        assertThat(meals.editar(autor, nova.id(), editar(nova, legado, "2")).restaurante().id()).isEqualTo(legado);
    }

    @Test
    void contasPublicasPermitemLeituraSemSeguirERejeitamPrivacidade() throws Exception {
        var autor = conta();
        var leitor = conta();
        String token = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        var meal = publicar(autor.id(), "5");
        var perfil = http("GET", "/api/usuarios/" + autor.id(), token, null, null);
        assertThat(perfil.statusCode()).isEqualTo(200);
        assertThat(perfil.body()).doesNotContain("email", "celular", "senha", "sessao", "token");
        assertThat(http("GET", "/api/usuarios/" + autor.id() + "/meals", token, null, null).body()).contains(meal.id().toString());
        assertThat(http("GET", "/api/meals/" + meal.id(), token, null, null).statusCode()).isEqualTo(200);
        assertThat(consultas.listar(null, null, leitor.id(), null, 20).itens()).isEmpty();
        for (String campo : List.of("isPrivate", "privateAccount", "visibilidade")) {
            var cadastro = (tools.jackson.databind.node.ObjectNode) json.valueToTree(new CadastroRequest("priv_teste", "priv@example.test", "98999999999", SENHA));
            cadastro.put(campo, true);
            assertThat(http("POST", "/api/auth/cadastro", null, json.writeValueAsString(cadastro), null).statusCode()).isEqualTo(400);
            var atualizacao = (tools.jackson.databind.node.ObjectNode) json.valueToTree(new AtualizarContaRequest(leitor.username(), leitor.email(), leitor.celular()));
            atualizacao.put(campo, false);
            assertThat(http("PUT", "/api/usuarios/me", token, json.writeValueAsString(atualizacao), null).statusCode()).isEqualTo(400);
        }
        assertThat(http("PUT", "/api/usuarios/" + autor.id() + "/seguimento", token, null, null).statusCode()).isEqualTo(204);
        assertThat(consultas.listar(null, null, leitor.id(), null, 20).itens()).hasSize(1);
    }

    @Test
    void contratoHttpPlacesPropagaSessaoERejeitaNomeLivre() throws Exception {
        var usuario = conta();
        String token = auth.login(new LoginRequest(usuario.username(), SENHA)).token();
        UUID sessaoPlaces = UUID.randomUUID();
        var sugestoes = http("GET", "/api/restaurantes/sugestoes?termo=arroz&tokenSessao=" + sessaoPlaces, token, null, null);
        assertThat(sugestoes.statusCode()).isEqualTo(200);
        assertThat(sugestoes.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(places.ultimoToken).isEqualTo(sessaoPlaces);
        String placeId = "http_" + UUID.randomUUID();
        String corpo = json.writeValueAsString(new SelecionarRestauranteRequest(placeId, sessaoPlaces));
        assertThat(http("POST", "/api/restaurantes/selecoes", token, corpo.substring(0, corpo.length() - 1) + ",\"nome\":\"Nome forjado\"}", null).statusCode()).isEqualTo(400);
        assertThat(http("POST", "/api/restaurantes/selecoes", token, corpo, null).statusCode()).isEqualTo(422);
        selecionar(placeId); // Referência pré-catálogo de teste, não cria via endpoint aposentado.
        var selecionado = http("POST", "/api/restaurantes/selecoes", token, corpo, null);
        assertThat(selecionado.statusCode()).isEqualTo(200);
        String id = json.readTree(selecionado.body()).path("id").asString();
        int antesDoPerfil = places.chamadas.get();
        assertThat(http("GET", "/api/restaurantes/" + id, token, null, null).statusCode()).isEqualTo(200);
        // T01: o perfil não consulta o Google, nem para referência antiga sem nome próprio.
        assertThat(places.chamadas.get()).isEqualTo(antesDoPerfil);
        assertThat(places.ultimoToken).isEqualTo(sessaoPlaces);
        places.falhar = true;
        assertThat(http("POST", "/api/restaurantes/selecoes", token, corpo, null).statusCode()).isEqualTo(200);
        int chamadas = places.chamadas.get();
        assertThat(http("GET", "/api/restaurantes/" + id + "/meals", token, null, null).statusCode()).isEqualTo(200);
        assertThat(places.chamadas.get()).isEqualTo(chamadas);
    }

    @Test
    void fotoSoltaDoPostSoOAutorConsultaENaoUsadaSaiEm24Horas() throws Exception {
        // T16 (P-04): o app envia a foto ao tocar em Publicar; até entrar numa Meal ela é da pessoa e não é pública.
        var autor = conta();
        var outro = conta();
        String tokenAutor = auth.login(new LoginRequest(autor.username(), SENHA)).token();
        String tokenOutro = auth.login(new LoginRequest(outro.username(), SENHA)).token();
        byte[] jpeg = gerarJpegTeste(600, 800, Color.ORANGE);
        assertThat(httpMultipart("/api/fotos", null, jpeg).statusCode()).isEqualTo(401);
        var envio = httpMultipart("/api/fotos", tokenAutor, jpeg);
        assertThat(envio.statusCode()).isEqualTo(201);
        assertThat(envio.headers().firstValue("Cache-Control")).hasValue("no-store");
        UUID fotoId = UUID.fromString(json.readTree(envio.body()).path("id").asString());
        assertThat(jdbc.queryForObject("select meal_id is null from fotos_meal where id = ?", Boolean.class, fotoId)).isTrue();
        assertThat(http("GET", "/api/fotos/" + fotoId, tokenOutro, null, null).statusCode()).isEqualTo(403);
        assertThat(http("GET", "/api/fotos/" + fotoId, tokenAutor, null, null).statusCode()).isEqualTo(200);
        processadorModeracao.processarLote();
        assertThat(httpBytes("/api/fotos/" + fotoId + "/conteudo", tokenAutor).statusCode()).isEqualTo(404);
        String chave = jdbc.queryForObject("select chave_arquivo from fotos where id = ?", String.class, fotoId);
        clock.agora = clock.agora.plus(Duration.ofHours(23));
        fotoService.limparArquivosExpiradosEOrfaos();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoId)).isNotEqualTo("REMOVIDA");
        assertThat(armazenamentoFotos.existe(chave)).isTrue();
        clock.agora = clock.agora.plus(Duration.ofHours(1));
        fotoService.limparArquivosExpiradosEOrfaos();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoId)).isEqualTo("REMOVIDA");
        assertThat(armazenamentoFotos.existe(chave)).isFalse();
    }

    @Test
    void uploadFotoPerfilEDoPostPermanecePrivadoAteAprovacaoETrocaNaEdicaoRemoveAAnterior() throws Exception {
        var autor = conta();
        var leitor = conta();
        String tokenAutor = auth.login(new LoginRequest(autor.username(), SENHA)).token();
        String tokenLeitor = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        byte[] jpeg600x800 = gerarJpegTeste(600, 800, new Color(40, 140, 90));

        var uploadPerfil = httpMultipart("/api/usuarios/me/foto", tokenAutor, jpeg600x800);
        assertThat(uploadPerfil.statusCode()).isEqualTo(201);
        UUID fotoPerfilId = UUID.fromString(json.readTree(uploadPerfil.body()).path("id").asString());

        var uploadPost = httpMultipart("/api/fotos", tokenAutor, jpeg600x800);
        assertThat(uploadPost.statusCode()).isEqualTo(201);
        UUID fotoPostId = UUID.fromString(json.readTree(uploadPost.body()).path("id").asString());
        String hash1 = jdbc.queryForObject("select hash_conteudo from fotos where id = ?", String.class, fotoPostId);
        var meal = meals.publicar(autor.id(), UUID.randomUUID().toString(), new MealRequest(restaurante,
                List.of(new DishRequest(null, "Prato", new BigDecimal("4.5"))), List.of(fotoPostId), null, true, null, null, null));
        assertThat(http("DELETE", "/api/fotos/" + fotoPostId, tokenLeitor, null, null).statusCode()).isEqualTo(403);

        // Enquanto PENDENTE, conteúdo e miniatura retornam 404 e perfis/Meals não expõem foto.
        assertThat(meal.fotos()).isEmpty();
        assertThat(httpBytes("/api/fotos/" + fotoPerfilId + "/conteudo", tokenAutor).statusCode()).isEqualTo(404);
        assertThat(httpBytes("/api/fotos/" + fotoPostId + "/conteudo", tokenLeitor).statusCode()).isEqualTo(404);
        assertThat(httpBytes("/api/fotos/" + fotoPostId + "/miniatura", tokenLeitor).statusCode()).isEqualTo(404);
        assertThat(usuarios.consultar(autor.id()).foto()).isNull();
        assertThat(consultas.consultar(meal.id()).fotos()).isEmpty();
        assertThat(http("GET", "/api/fotos/" + fotoPostId, tokenLeitor, null, null).statusCode()).isEqualTo(403);
        assertThat(http("GET", "/api/fotos/" + fotoPostId, tokenAutor, null, null).statusCode()).isEqualTo(200);

        // Executa a fila automática de moderação -> ambas aprovadas.
        processadorModeracao.processarLote();
        assertThat(usuarios.consultar(autor.id()).foto()).isNotNull();
        assertThat(usuarios.consultar(autor.id()).foto().id()).isEqualTo(fotoPerfilId);
        assertThat(consultas.consultar(meal.id()).fotos()).singleElement().satisfies(f -> {
            assertThat(f.id()).isEqualTo(fotoPostId);
            assertThat(f.url()).isEqualTo("/api/fotos/" + fotoPostId + "/conteudo");
            assertThat(f.urlMiniatura()).isEqualTo("/api/fotos/" + fotoPostId + "/miniatura");
        });

        var leituraConteudo = httpBytes("/api/fotos/" + fotoPostId + "/conteudo", tokenLeitor);
        assertThat(leituraConteudo.statusCode()).isEqualTo(200);
        assertThat(leituraConteudo.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(leituraConteudo.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(leituraConteudo.body().length).isGreaterThan(100);
        assertThat(httpBytes("/api/fotos/" + fotoPostId + "/miniatura", tokenLeitor).statusCode()).isEqualTo(200);
        assertThat(httpBytes("/api/fotos/" + fotoPostId + "/conteudo", null).statusCode()).isEqualTo(401);

        // Trocar a foto na edição: a nova nasce PENDENTE e a anterior é removida, com os arquivos.
        String chaveAntiga = jdbc.queryForObject("select chave_arquivo from fotos where id = ?", String.class, fotoPostId);
        UUID novaFotoId = UUID.fromString(json.readTree(httpMultipart("/api/fotos", tokenAutor,
                gerarJpegTeste(750, 1000, new Color(200, 60, 40))).body()).path("id").asString());
        assertThat(jdbc.queryForObject("select hash_conteudo from fotos where id = ?", String.class, novaFotoId)).isNotEqualTo(hash1);
        var editada = meals.editar(autor.id(), meal.id(), new MealRequest(restaurante,
                List.of(new DishRequest(meal.dishes().getFirst().id(), "Prato", new BigDecimal("4.5"))), List.of(novaFotoId),
                null, true, null, null, meal.versao()));
        assertThat(editada.fotos()).isEmpty();
        assertThat(httpBytes("/api/fotos/" + fotoPostId + "/conteudo", tokenLeitor).statusCode()).isEqualTo(404);
        assertThat(armazenamentoFotos.existe(chaveAntiga)).isFalse();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoPostId)).isEqualTo("REMOVIDA");
        assertThat(fotosDa(meal.id())).containsExactly(novaFotoId);
    }

    @Test
    void publicarExigeDeUmaADezFotosSoltasDaPropriaPessoaNaOrdemEnviada() throws Exception {
        // T16 (RN-10, AC3, decisão de 25/10): pelo menos 1 e até 10 fotos; a 1ª é a capa. Foto de outra conta,
        // já usada, vencida, rejeitada ou inexistente: a mesma recusa, sem revelar se a foto existe.
        UUID autor = conta().id();
        UUID outro = conta().id();
        java.util.function.Function<List<UUID>, MealRequest> com = fotos -> new MealRequest(restaurante,
                List.of(new DishRequest(null, "Prato", new BigDecimal("4"))), fotos, null, true, null, null, null);
        assertThatThrownBy(() -> meals.publicar(autor, "sem-foto", com.apply(List.of()))).isInstanceOf(RegraNegocioException.class);
        List<UUID> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) onze.add(foto(autor));
        assertThatThrownBy(() -> meals.publicar(autor, "onze", com.apply(onze))).isInstanceOf(RegraNegocioException.class);
        UUID repetida = foto(autor);
        assertThatThrownBy(() -> meals.publicar(autor, "repetida", com.apply(List.of(repetida, repetida))))
                .isInstanceOf(RegraNegocioException.class);
        String recusa = mensagem(() -> meals.publicar(autor, "de-outro", com.apply(List.of(foto(outro)))));
        assertThat(mensagem(() -> meals.publicar(autor, "inexistente", com.apply(List.of(UUID.randomUUID()))))).isEqualTo(recusa);
        UUID rejeitada = foto(autor);
        jdbc.update("update fotos set status = 'REJEITADA' where id = ?", rejeitada);
        assertThat(mensagem(() -> meals.publicar(autor, "rejeitada", com.apply(List.of(rejeitada))))).isEqualTo(recusa);
        UUID vencida = foto(autor);
        clock.agora = clock.agora.plus(Duration.ofHours(24));
        assertThat(mensagem(() -> meals.publicar(autor, "vencida", com.apply(List.of(vencida))))).isEqualTo(recusa);
        assertThat(totalMeals(autor)).isZero();

        var dez = onze.subList(0, 10);
        clock.agora = AGORA.plus(Duration.ofHours(1));
        var meal = meals.publicar(autor, "dez", com.apply(dez));
        assertThat(fotosDa(meal.id())).containsExactlyElementsOf(dez);
        assertThat(mensagem(() -> meals.publicar(autor, "ja-usada", com.apply(List.of(dez.getFirst()))))).isEqualTo(recusa);
        jdbc.update("update fotos set status = 'APROVADA' where id in (select id from fotos_meal where meal_id = ?)", meal.id());
        assertThat(consultas.consultar(meal.id()).fotos()).extracting(FotoPublicaResponse::id).containsExactlyElementsOf(dez);
        // Duas publicações simultâneas com a mesma foto: só uma leva a foto.
        UUID disputada = foto(autor);
        var resultados = paralelo(2, i -> {
            try { return meals.publicar(autor, "disputa-" + i, com.apply(List.of(disputada))).id().toString(); }
            catch (RegraNegocioException ex) { return "recusada"; }
        });
        assertThat(resultados).containsOnlyOnce("recusada");
        // Pelo HTTP, a recusa é 400 dados_invalidos.
        String token = auth.login(new LoginRequest(jdbc.queryForObject("select username from usuarios where id = ?", String.class, autor), SENHA)).token();
        var resposta = http("POST", "/api/meals", token, json.writeValueAsString(com.apply(List.of(foto(outro)))), "http-de-outro");
        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(json.readTree(resposta.body()).path("codigo").asString()).isEqualTo("dados_invalidos");
    }

    @Test
    void edicaoReordenaTrocaERemoveFotosEExclusaoEncerraTodas() throws Exception {
        UUID autor = conta().id();
        // f2 mantém a tarefa de moderação ativa, para conferir que ela é cancelada quando a foto sai.
        UUID f1 = foto(autor), f2 = fotoService.enviarSoltaBytes(autor, JPEG_TESTE).id(), f3 = foto(autor);
        var meal = meals.publicar(autor, UUID.randomUUID().toString(), new MealRequest(restaurante,
                List.of(new DishRequest(null, "Prato", new BigDecimal("5"))), List.of(f1, f2), null, true, null, null, null));
        var outraMeal = publicar(autor, "4");
        String chave2 = jdbc.queryForObject("select chave_arquivo from fotos where id = ?", String.class, f2);
        java.util.function.BiFunction<List<UUID>, Long, MealRequest> com = (fotos, versao) -> new MealRequest(restaurante,
                List.of(new DishRequest(meal.dishes().getFirst().id(), "Prato", new BigDecimal("5"))), fotos, null, true, null, null, versao);
        // Sem foto ou com foto de outro post: recusa, sem mexer nas fotos.
        assertThatThrownBy(() -> meals.editar(autor, meal.id(), com.apply(List.of(), meal.versao()))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> meals.editar(autor, meal.id(), com.apply(List.of(fotosDa(outraMeal.id()).getFirst()), meal.versao())))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(fotosDa(meal.id())).containsExactly(f1, f2);
        // Nova capa (f3), f1 vai para 2º e f2 sai: removida, com a tarefa cancelada e os arquivos apagados.
        var editada = meals.editar(autor, meal.id(), com.apply(List.of(f3, f1), meal.versao()));
        assertThat(fotosDa(meal.id())).containsExactly(f3, f1);
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, f2)).isEqualTo("REMOVIDA");
        assertThat(jdbc.queryForObject("select estado from tarefas_moderacao where foto_id = ?", String.class, f2)).isEqualTo("CANCELADA");
        assertThat(armazenamentoFotos.existe(chave2)).isFalse();
        // Só trocar a ordem também vale (a posição única por post só é conferida no fim da transação).
        meals.editar(autor, meal.id(), com.apply(List.of(f1, f3), editada.versao()));
        assertThat(fotosDa(meal.id())).containsExactly(f1, f3);
        // Excluir o post remove todas as fotos dele e limpa os arquivos.
        String chave1 = jdbc.queryForObject("select chave_arquivo from fotos where id = ?", String.class, f1);
        meals.excluir(autor, meal.id());
        assertThat(jdbc.queryForList("select status from fotos where id in (?, ?)", String.class, f1, f3)).containsOnly("REMOVIDA");
        assertThat(armazenamentoFotos.existe(chave1)).isFalse();
    }

    @Test
    void moderacaoAutomaticaRejeitaTrataFalhaTecnicaComBackoffExaustaoELimpezaRetencao() throws Exception {
        var autor = conta();
        String token = auth.login(new LoginRequest(autor.username(), SENHA)).token();
        byte[] jpeg = gerarJpegTeste(600, 800, Color.MAGENTA);

        // 1) Foto rejeitada automaticamente por sinal crítico >= 80%.
        moderacaoSimulada.sinais = Map.of("explicit_nudity", "91.20");
        UUID fotoRejeitada = UUID.fromString(json.readTree(httpMultipart("/api/usuarios/me/foto", token, jpeg).body()).path("id").asString());
        String chaveRejeitada = jdbc.queryForObject("select chave_arquivo from fotos where id = ?", String.class, fotoRejeitada);
        processadorModeracao.processarLote();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoRejeitada)).isEqualTo("REJEITADA");
        assertThat(httpBytes("/api/fotos/" + fotoRejeitada + "/conteudo", token).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select resultado from registros_moderacao where foto_id = ?", String.class, fotoRejeitada)).isEqualTo("REJEITAR");

        // 2) Falha técnica nunca aprova foto: reagenda com backoff e após 3 falhas encaminha para EM_REVISAO.
        moderacaoSimulada.falhar = true;
        UUID fotoComFalha = UUID.fromString(json.readTree(httpMultipart("/api/fotos", token, jpeg).body()).path("id").asString());

        processadorModeracao.processarLote();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoComFalha)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("select tentativas from tarefas_moderacao where foto_id = ?", Integer.class, fotoComFalha)).isEqualTo(1);

        clock.agora = clock.agora.plusSeconds(5);
        processadorModeracao.processarLote();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoComFalha)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("select tentativas from tarefas_moderacao where foto_id = ?", Integer.class, fotoComFalha)).isEqualTo(2);

        clock.agora = clock.agora.plusSeconds(15);
        processadorModeracao.processarLote();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoComFalha)).isEqualTo("EM_REVISAO");
        assertThat(jdbc.queryForObject("select estado from tarefas_moderacao where foto_id = ?", String.class, fotoComFalha)).isEqualTo("CONCLUIDA");
        assertThat(jdbc.queryForObject("select count(*) from registros_moderacao where foto_id = ? and resultado = 'FALHA_TECNICA'", Integer.class, fotoComFalha)).isEqualTo(3);

        // 3) Retenção de rejeitadas: após 7 dias sem contestação, apaga arquivos do storage.
        assertThat(armazenamentoFotos.existe(chaveRejeitada)).isTrue();
        clock.agora = clock.agora.plus(Duration.ofDays(8));
        fotoService.limparArquivosExpiradosEOrfaos();
        assertThat(armazenamentoFotos.existe(chaveRejeitada)).isFalse();
        assertThat(jdbc.queryForObject("select arquivos_limpos_em is not null from fotos where id = ?", Boolean.class, fotoRejeitada)).isTrue();
    }

    @Test
    void recuperaReservaExpiradaEIgnoraConclusaoDeWorkerAtrasado() {
        var autor = conta();
        byte[] jpeg = gerarJpegTeste(600, 800, Color.GRAY);
        var enviada = fotoService.enviarPerfilBytes(autor.id(), jpeg);
        UUID tarefaId = jdbc.queryForObject("select id from tarefas_moderacao where foto_id = ?", UUID.class, enviada.id());

        var reservaAntiga = moderacaoTransacional.reservar(tarefaId).orElseThrow();
        assertThat(jdbc.queryForObject("select estado from tarefas_moderacao where id = ?", String.class, tarefaId)).isEqualTo("EM_EXECUCAO");

        // Simula queda do worker além da janela de reserva (30s em teste): nova reserva assume a tarefa.
        clock.agora = clock.agora.plusSeconds(40);
        var reservaNova = moderacaoTransacional.reservar(tarefaId).orElseThrow();
        assertThat(reservaNova.tokenReserva()).isNotEqualTo(reservaAntiga.tokenReserva());
        assertThat(moderacaoTransacional.concluirAutomatica(
                tarefaId, reservaNova.tokenReserva(), reservaNova.hashConteudo(),
                new ResultadoProvedor("simulado", "1.0", Map.of()),
                new PoliticaModeracao.DecisaoPolitica(ResultadoModeracao.APROVAR, "Aprovada pelo worker ativo."))).isTrue();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, enviada.id())).isEqualTo("APROVADA");

        // Worker antigo atrasado tenta rejeitar usando o token antigo -> ignorado!
        assertThat(moderacaoTransacional.concluirAutomatica(
                tarefaId, reservaAntiga.tokenReserva(), reservaAntiga.hashConteudo(),
                new ResultadoProvedor("simulado", "1.0", Map.of("explicit_nudity", "99.00")),
                new PoliticaModeracao.DecisaoPolitica(ResultadoModeracao.REJEITAR, "Resultado atrasado."))).isFalse();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, enviada.id())).isEqualTo("APROVADA");
    }

    @Test
    void contestacaoDoAutorERevisaoHumanaPorModeradorComAuditoria() throws Exception {
        var autor = conta();
        String tokenAutor = auth.login(new LoginRequest(autor.username(), SENHA)).token();
        byte[] jpeg = gerarJpegTeste(600, 800, Color.YELLOW);

        moderacaoSimulada.sinais = Map.of("explicit_nudity", "85.00");
        UUID fotoId = UUID.fromString(json.readTree(httpMultipart("/api/usuarios/me/foto", tokenAutor, jpeg).body()).path("id").asString());
        processadorModeracao.processarLote();
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoId)).isEqualTo("REJEITADA");

        // Autor contesta a rejeição automática -> passa para EM_REVISAO.
        var contestacao = http("POST", "/api/fotos/" + fotoId + "/contestacao", tokenAutor,
                json.writeValueAsString(new ContestacaoFotoRequest("É um prato regional legítimo.")), null);
        assertThat(contestacao.statusCode()).isEqualTo(200);
        assertThat(json.readTree(contestacao.body()).path("status").asString()).isEqualTo("EM_REVISAO");

        // Usuário comum não tem acesso aos endpoints administrativos.
        assertThat(http("GET", "/api/moderacao/fotos", tokenAutor, null, null).statusCode()).isEqualTo(403);
        assertThat(http("POST", "/api/moderacao/fotos/" + fotoId + "/revisao", tokenAutor,
                json.writeValueAsString(new RevisaoFotoRequest(ResultadoModeracao.APROVAR, "Tentativa indevida")), null).statusCode()).isEqualTo(403);

        // Moderador configurado em jambu.fotos.moderadores ("admin_moderador") analisa e aprova.
        var admin = garantirModerador();
        String tokenAdmin = auth.login(new LoginRequest(admin.username(), SENHA)).token();
        var fila = http("GET", "/api/moderacao/fotos?status=EM_REVISAO", tokenAdmin, null, null);
        assertThat(fila.statusCode()).isEqualTo(200);
        assertThat(fila.body()).contains(fotoId.toString());

        // Moderador pode inspecionar o binário mesmo antes da aprovação pública.
        assertThat(httpBytes("/api/moderacao/fotos/" + fotoId + "/conteudo", tokenAdmin).statusCode()).isEqualTo(200);

        var decisao = http("POST", "/api/moderacao/fotos/" + fotoId + "/revisao", tokenAdmin,
                json.writeValueAsString(new RevisaoFotoRequest(ResultadoModeracao.APROVAR, "Verificado manualmente pela moderação.")), null);
        assertThat(decisao.statusCode()).isEqualTo(200);
        assertThat(json.readTree(decisao.body()).path("status").asString()).isEqualTo("APROVADA");
        assertThat(usuarios.consultar(autor.id()).foto()).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from registros_moderacao where foto_id = ? and origem = 'HUMANA' and resultado = 'APROVAR' and revisor_id = ?",
                Integer.class, fotoId, admin.id())).isEqualTo(1);
    }

    @Test
    void founderReprovaFotoDoPostEOPostInteiroSaiParaTodos() throws Exception {
        // T18 (RN-18, F5, decisão de 25/10): reprovar (REJEITAR ou remoção administrativa) exclui o post inteiro;
        // todas as fotos dele ficam REMOVIDA, com os arquivos apagados. A reprovação fica no histórico.
        var autor = conta();
        var leitor = conta();
        String tokenLeitor = auth.login(new LoginRequest(leitor.username(), SENHA)).token();
        String tokenAdmin = auth.login(new LoginRequest(garantirModerador().username(), SENHA)).token();
        UUID impropria = foto(autor.id()), outra = foto(autor.id());
        var meal = meals.publicar(autor.id(), UUID.randomUUID().toString(), new MealRequest(restaurante,
                List.of(new DishRequest(null, "Prato", new BigDecimal("4"))), List.of(outra, impropria), null, true, null, null, null));
        String chaveOutra = jdbc.queryForObject("select chave_arquivo from fotos where id = ?", String.class, outra);
        var revisao = http("POST", "/api/moderacao/fotos/" + impropria + "/revisao", tokenAdmin,
                json.writeValueAsString(new RevisaoFotoRequest(ResultadoModeracao.REJEITAR, "Conteúdo impróprio.")), null);
        assertThat(revisao.statusCode()).isEqualTo(200);
        assertThat(json.readTree(revisao.body()).path("status").asString()).isEqualTo("REMOVIDA");
        assertThat(http("GET", "/api/meals/" + meal.id(), tokenLeitor, null, null).statusCode()).isEqualTo(404);
        assertThat(feedCompleto(tokenLeitor, "PARA_VOCE")).doesNotContain(meal.id());
        assertThat(jdbc.queryForList("select status from fotos where id in (?, ?)", String.class, impropria, outra)).containsOnly("REMOVIDA");
        assertThat(armazenamentoFotos.existe(chaveOutra)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from registros_moderacao where foto_id = ? and origem = 'HUMANA' and resultado = 'REJEITAR'",
                Integer.class, impropria)).isEqualTo(1);
        // A remoção administrativa da foto de um post também tira o post.
        var segunda = publicar(autor.id(), "3");
        UUID fotoSegunda = fotosDa(segunda.id()).getFirst();
        assertThat(http("DELETE", "/api/moderacao/fotos/" + fotoSegunda, tokenAdmin, null, null).statusCode()).isEqualTo(204);
        assertThat(http("GET", "/api/meals/" + segunda.id(), tokenLeitor, null, null).statusCode()).isEqualTo(404);
        // A reprovação automática só esconde a foto: o post continua.
        var terceira = publicar(autor.id(), "5");
        UUID fotoTerceira = fotosDa(terceira.id()).getFirst();
        jdbc.update("update tarefas_moderacao set estado = 'AGUARDANDO' where foto_id = ?", fotoTerceira);
        moderacaoSimulada.sinais = Map.of("explicit_nudity", "95.00");
        moderacaoService.processar(jdbc.queryForObject("select id from tarefas_moderacao where foto_id = ?", UUID.class, fotoTerceira));
        assertThat(jdbc.queryForObject("select status from fotos where id = ?", String.class, fotoTerceira)).isEqualTo("REJEITADA");
        assertThat(http("GET", "/api/meals/" + terceira.id(), tokenLeitor, null, null).statusCode()).isEqualTo(200);
    }

    private ContaResponse garantirModerador() {
        var existente = jdbc.query("select id, username, email, celular from usuarios where lower(username) = 'admin_moderador'",
                (rs, rowNum) -> new ContaResponse(UUID.fromString(rs.getString("id")), rs.getString("username"),
                        rs.getString("email"), rs.getString("celular")));
        if (!existente.isEmpty()) return existente.getFirst();
        return auth.cadastrar(new CadastroRequest("admin_moderador", "admin_moderador@example.test", "98999999999", SENHA));
    }

    private static byte[] gerarJpegTeste(int largura, int altura, Color cor) {
        try {
            BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setColor(cor);
                g.fillRect(0, 0, largura, altura);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private HttpResponse<String> httpMultipart(String caminho, String token, byte[] arquivo) throws Exception {
        String boundary = "----JambuTesteBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        corpo.write("Content-Disposition: form-data; name=\"arquivo\"; filename=\"foto.jpg\"\r\n".getBytes(StandardCharsets.UTF_8));
        corpo.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        corpo.write(arquivo);
        corpo.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<byte[]> httpBytes(String caminho, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private tools.jackson.databind.JsonNode itemDoFeed(String token, UUID mealId) throws Exception {
        String cursor = null;
        do {
            var corpo = json.readTree(http("GET", "/api/feed?aba=PARA_VOCE&limite=50" + (cursor == null ? "" : "&cursor=" + cursor), token, null, null).body());
            for (var item : corpo.path("itens")) if (item.path("meal").path("id").asString().equals(mealId.toString())) return item;
            cursor = corpo.path("proximoCursor").isNull() ? null : corpo.path("proximoCursor").asString();
        } while (cursor != null);
        throw new AssertionError("Meal fora do feed: " + mealId);
    }
    /** Percorre todas as páginas do feed pelo cursor e devolve os IDs das Meals, na ordem. */
    private List<UUID> feedCompleto(String token, String aba) throws Exception {
        List<UUID> ids = new ArrayList<>();
        String cursor = null;
        do {
            var resposta = http("GET", "/api/feed?aba=" + aba + "&limite=50" + (cursor == null ? "" : "&cursor=" + cursor), token, null, null);
            assertThat(resposta.statusCode()).isEqualTo(200);
            var corpo = json.readTree(resposta.body());
            for (var item : corpo.path("itens")) ids.add(UUID.fromString(item.path("meal").path("id").asString()));
            cursor = corpo.path("proximoCursor").isNull() ? null : corpo.path("proximoCursor").asString();
        } while (cursor != null);
        return ids;
    }
    private ContaResponse conta() {
        String nome = "teste_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA));
    }
    private MealRequest pedido(UUID autor, String... notas) {
        return new MealRequest(restaurante, Arrays.stream(notas).map(n -> new DishRequest(null, "Prato fictício", new BigDecimal(n))).toList(),
                List.of(foto(autor)), null, true, null, null, null);
    }
    private MealResponse publicar(UUID autor, String... notas) { return meals.publicar(autor, UUID.randomUUID().toString(), pedido(autor, notas)); }
    /** Mantém as fotos que a Meal já tem (T16: a edição também manda fotoIds). */
    private MealRequest editar(MealResponse meal, UUID restauranteId, String nota) {
        return new MealRequest(restauranteId, List.of(new DishRequest(meal.dishes().getFirst().id(), "Prato editado", new BigDecimal(nota))),
                fotosDa(meal.id()), null, false, Modalidade.DELIVERY, null, meal.versao());
    }
    /** Foto solta da pessoa (T16), pelo mesmo caminho do POST /api/fotos. */
    private UUID foto(UUID autor) {
        UUID id = fotoService.enviarSoltaBytes(autor, JPEG_TESTE).id();
        // Foto de apoio sai da fila de moderação: o lote é de 10 e não pode atrasar as tarefas dos testes de foto.
        jdbc.update("update tarefas_moderacao set estado = 'CANCELADA' where foto_id = ?", id);
        return id;
    }
    private String mensagem(org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada) {
        var erro = catchThrowable(chamada);
        assertThat(erro).isInstanceOf(RegraNegocioException.class);
        return erro.getMessage();
    }
    private List<UUID> fotosDa(UUID mealId) {
        return jdbc.queryForList("select id from fotos_meal where meal_id = ? order by ordem", UUID.class, mealId);
    }
    private int totalMeals(UUID autor) {
        return jdbc.queryForObject("select count(*) from meals where autor_id = ?", Integer.class, autor);
    }
    private List<String> paralelo(int quantidade, java.util.function.IntFunction<String> tarefa) throws Exception {
        try (var executor = Executors.newFixedThreadPool(quantidade)) {
            var largada = new CountDownLatch(1);
            List<Future<String>> futuros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                int indice = i;
                futuros.add(executor.submit(() -> { largada.await(); return tarefa.apply(indice); }));
            }
            largada.countDown();
            List<String> resultados = new ArrayList<>();
            for (var futuro : futuros) resultados.add(futuro.get(30, TimeUnit.SECONDS));
            return resultados;
        }
    }
    private HttpResponse<String> http(String metodo, String caminho, String token, String corpo, String chave) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (chave != null) request.header("Idempotency-Key", chave);
        request.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
        try (var client = HttpClient.newHttpClient()) { return client.send(request.build(), HttpResponse.BodyHandlers.ofString()); }
    }
    @TestConfiguration
    static class TestConfig {
        @Bean @Primary RelogioTeste relogioTeste() { return new RelogioTeste(); }
        @Bean @Primary PlacesSimulado placesSimulado() { return new PlacesSimulado(); }
        @Bean @Primary ModeracaoSimulada moderacaoSimulada() { return new ModeracaoSimulada(); }
    }
    private RestauranteResponse selecionar(String placeId) {
        jdbc.update("""
                insert into restaurantes(id,google_place_id,places_validado_em,origem)
                values (?,?,?,'GOOGLE_PLACES') on conflict(google_place_id) do nothing
                """, UUID.randomUUID(), placeId, java.sql.Timestamp.from(clock.instant()));
        return restauranteService.consultar(restaurantes.findByGooglePlaceId(placeId).orElseThrow().getId());
    }
    private CadastroRestauranteRequest cadastroGoogle(String placeId) {
        return new CadastroRestauranteRequest("Próprio " + UUID.randomUUID(), -2.53, -44.30,
                CategoriaRestaurante.RESTAURANTE, placeId, UUID.randomUUID(), Set.of());
    }
    static class ModeracaoSimulada implements ProvedorModeracaoImagem {
        volatile boolean falhar;
        volatile Map<String, String> sinais = Map.of();
        final AtomicInteger chamadas = new AtomicInteger();
        @Override
        public ResultadoProvedor analisar(byte[] conteudo) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(conteudo).isNotEmpty();
            chamadas.incrementAndGet();
            if (falhar) throw ModeracaoException.indisponivel();
            return new ResultadoProvedor("simulado", "1.0", sinais);
        }
    }
    static class PlacesSimulado implements ProvedorRestaurantes {
        volatile boolean falhar;
        final AtomicInteger chamadas = new AtomicInteger();
        volatile UUID ultimoToken;
        @Override public List<SugestaoLugar> sugerir(String termo, UUID token) {
            verificar(token);
            return List.of(new SugestaoLugar("teste_sugestao", "Nome externo simulado"));
        }
        @Override public DadosLugar consultar(String id, UUID token) {
            verificar(token);
            return new DadosLugar(id, List.of(id.startsWith("nao_permitido") ? "school" : "restaurant"));
        }
        /** T05: IDs que a busca "só IDs" devolve para cada nome; nome sem entrada devolve vazio. */
        final Map<String, List<String>> idsPorNome = new ConcurrentHashMap<>();
        final AtomicInteger buscasIds = new AtomicInteger();
        volatile double[] ultimoPonto;
        @Override public List<String> buscarIds(String nome, double latitude, double longitude) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            buscasIds.incrementAndGet();
            ultimoPonto = new double[] { latitude, longitude };
            if (falhar) throw PlacesException.indisponivel();
            return idsPorNome.getOrDefault(nome, List.of());
        }
        void verificar(UUID token) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            chamadas.incrementAndGet();
            ultimoToken = token;
            if (falhar) throw PlacesException.indisponivel();
        }
    }
    static class RelogioTeste extends Clock {
        volatile Instant agora = AGORA;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(agora, zone); }
        @Override public Instant instant() { return agora; }
    }
}
