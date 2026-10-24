package com.jambuapp.api.restaurante;

import java.time.Clock;
import java.util.UUID;
import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmação do lugar provisório pela 2ª conta (T08, RN-57): quem cadastrou não responde, e só quem tem
 * Meal ativa no lugar pode. CONFIRMACAO verifica o lugar; CORRECAO o mantém provisório para os founders.
 */
@Service
@Transactional
public class RetornoRestauranteService {
    private final RestauranteRepository restaurantes;
    private final CatalogoTransacionalService catalogo;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public RetornoRestauranteService(RestauranteRepository restaurantes, CatalogoTransacionalService catalogo,
            JdbcTemplate jdbc, Clock clock) {
        this.restaurantes = restaurantes; this.catalogo = catalogo; this.jdbc = jdbc; this.clock = clock;
    }
    public RetornoRestauranteResponse responder(UUID usuarioId, UUID restauranteId, RetornoRestauranteRequest request) {
        String nome = limpo(request.nomeSugerido()), observacao = limpo(request.observacao());
        if (request.tipo() == TipoRetorno.CORRECAO && nome == null && observacao == null)
            throw RegraNegocioException.invalido("Informe o nome certo ou o que está errado.");
        if (request.tipo() == TipoRetorno.CONFIRMACAO && (nome != null || observacao != null))
            throw RegraNegocioException.invalido("A confirmação não leva nome nem observação.");
        if (nome != null && NomesRestaurantes.normalizar(nome).length() < 2)
            throw RegraNegocioException.invalido("Informe um nome com pelo menos duas letras ou números.");
        // Mesmo lock da revisão dos founders: a confirmação e a revisão não se cruzam.
        catalogo.bloquearCatalogo();
        var r = existente(restauranteId);
        exigirSegundaConta(usuarioId, r);
        var resposta = new Resposta(request.tipo().name(), nome, observacao);
        var anterior = jdbc.query("select tipo, nome_sugerido, observacao from retornos_restaurantes where restaurante_id = ? and usuario_id = ?",
                (rs, i) -> new Resposta(rs.getString(1), rs.getString(2), rs.getString(3)), restauranteId, usuarioId);
        if (!anterior.isEmpty()) {
            // Repetir a mesma resposta é seguro (o app pode reenviar); mudar de ideia não.
            if (!anterior.getFirst().equals(resposta)) throw RegraNegocioException.conflito("Você já respondeu sobre este lugar.");
            return new RetornoRestauranteResponse(request.tipo(), r.getSituacao());
        }
        if (r.getSituacao() != SituacaoRestaurante.PROVISORIO)
            throw RegraNegocioException.conflito("Este lugar já foi verificado.");
        var agora = clock.instant();
        jdbc.update("""
                insert into retornos_restaurantes(id, restaurante_id, usuario_id, tipo, nome_sugerido, observacao, criado_em)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), restauranteId, usuarioId, request.tipo().name(), nome, observacao,
                java.sql.Timestamp.from(agora));
        if (request.tipo() == TipoRetorno.CONFIRMACAO) r.revisar(SituacaoRestaurante.ATIVO, agora);
        restaurantes.flush();
        return new RetornoRestauranteResponse(request.tipo(), r.getSituacao());
    }
    private Restaurante existente(UUID restauranteId) {
        var r = restaurantes.findById(restauranteId).orElseThrow(RegraNegocioException::inexistente);
        if (r.getSituacao() == SituacaoRestaurante.REJEITADO) throw RegraNegocioException.inexistente();
        return r;
    }
    private void exigirSegundaConta(UUID usuarioId, Restaurante r) {
        if (usuarioId.equals(r.getCriadoPor()) || !temMealAtiva(usuarioId, r.getId())) throw RegraNegocioException.proibido();
    }
    private boolean temMealAtiva(UUID usuarioId, UUID restauranteId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from meals where autor_id = ? and restaurante_id = ? and excluida_em is null)",
                Boolean.class, usuarioId, restauranteId));
    }
    private static String limpo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
    private record Resposta(String tipo, String nome, String observacao) {}
    public record RetornoRestauranteResponse(TipoRetorno tipo, SituacaoRestaurante situacaoRestaurante) {}
}
