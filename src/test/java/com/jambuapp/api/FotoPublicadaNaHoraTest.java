package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import javax.imageio.ImageIO;
import com.jambuapp.api.autenticacao.*;
import com.jambuapp.api.foto.*;
import com.jambuapp.api.meal.*;
import com.jambuapp.api.restaurante.*;
import com.jambuapp.api.usuario.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** T18 (RN-18, RN-71, F4): com a moderação desligada (padrão do MVP), a foto aparece para todos na hora. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(JambuApiApplicationTests.TestConfig.class)
@TestPropertySource(properties = "jambu.fotos.moderacao-habilitada=false")
class FotoPublicadaNaHoraTest {
    private static final String SENHA = "Senha-ficticia-123!";
    @Autowired AutenticacaoService auth;
    @Autowired CadastroRestauranteService cadastros;
    @Autowired MealService meals;
    @Autowired ConsultaMealService consultas;
    @Autowired FotoService fotos;
    @Autowired UsuarioService usuarios;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int porta;

    @Test void fotoDoPostEDePerfilAparecemNaHoraParaOutraContaSemEntrarNaFila() throws Exception {
        UUID autor = conta();
        UUID leitor = conta();
        byte[] jpeg = jpeg();
        var fotoPost = fotos.enviarSoltaBytes(autor, jpeg);
        assertThat(fotoPost.status()).isEqualTo(StatusModeracaoFoto.APROVADA);
        assertThat(jdbc.queryForObject("select count(*) from tarefas_moderacao where foto_id = ?", Integer.class, fotoPost.id())).isZero();
        assertThat(jdbc.queryForMap("select origem, resultado from registros_moderacao where foto_id = ?", fotoPost.id()))
                .containsEntry("origem", "AUTOMATICA").containsEntry("resultado", "APROVAR");
        // Solta, mesmo aprovada, ainda não é pública: só depois de entrar num post.
        assertThat(conteudo(fotoPost.id(), leitor)).isEqualTo(404);
        UUID lugar = cadastros.cadastrar(autor, UUID.randomUUID().toString(), new CadastroRestauranteRequest(
                "Lugar na hora " + UUID.randomUUID(), -2.53, -44.30, CategoriaRestaurante.RESTAURANTE, null, null, Set.of())).restaurante().id();
        var meal = meals.publicar(autor, UUID.randomUUID().toString(), new MealRequest(lugar,
                List.of(new DishRequest(null, "Prato", new BigDecimal("4.5"))), List.of(fotoPost.id()), null, true, null, null, null));
        assertThat(meal.fotos()).extracting(FotoPublicaResponse::id).containsExactly(fotoPost.id());
        assertThat(consultas.consultar(meal.id()).fotos()).extracting(FotoPublicaResponse::id).containsExactly(fotoPost.id());
        assertThat(conteudo(fotoPost.id(), leitor)).isEqualTo(200);
        // A foto de perfil também (RN-71: a moderação do MVP é reativa).
        var fotoPerfil = fotos.enviarPerfilBytes(autor, jpeg);
        assertThat(fotoPerfil.status()).isEqualTo(StatusModeracaoFoto.APROVADA);
        assertThat(usuarios.consultar(autor).foto()).isNotNull();
    }

    private int conteudo(UUID fotoId, UUID usuario) throws Exception {
        String username = jdbc.queryForObject("select username from usuarios where id = ?", String.class, usuario);
        String token = auth.login(new LoginRequest(username, SENHA)).token();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/fotos/" + fotoId + "/conteudo"))
                .header("Authorization", "Bearer " + token).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
    private UUID conta() {
        String nome = "hora_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return auth.cadastrar(new CadastroRequest(nome, nome + "@example.test", "98999999999", SENHA)).id();
    }
    private static byte[] jpeg() throws Exception {
        var saida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB), "jpg", saida);
        return saida.toByteArray();
    }
}
