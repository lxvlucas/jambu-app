package com.jambuapp.api.restaurante;

import java.time.Clock;
import java.util.UUID;
import com.jambuapp.api.comum.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RevisaoRestauranteService {
    private final RestauranteRepository restaurantes;
    private final CatalogoTransacionalService catalogo;
    private final CatalogoProperties config;
    private final AreaCatalogo area;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    public RevisaoRestauranteService(RestauranteRepository restaurantes, CatalogoTransacionalService catalogo,
            CatalogoProperties config, AreaCatalogo area, Clock clock, JdbcTemplate jdbc) {
        this.restaurantes = restaurantes; this.catalogo = catalogo; this.config = config;
        this.area = area; this.clock = clock; this.jdbc = jdbc;
    }
    public void exigirRevisor(UUID usuarioId) {
        if (!config.revisores().contains(usuarioId)) throw RegraNegocioException.proibido();
    }
    @Transactional(readOnly = true)
    public PaginaResponse<RestauranteResumo> listar(UUID usuarioId, SituacaoRestaurante situacao, int pagina, int limite) {
        exigirRevisor(usuarioId);
        if (pagina < 0 || limite < 1 || limite > 50) throw RegraNegocioException.invalido("Paginação inválida.");
        return PaginaResponse.de(restaurantes.findBySituacao(situacao, PageRequest.of(pagina, limite,
                Sort.by("criadoEm").ascending().and(Sort.by("id")))).map(RestauranteResumo::de));
    }
    /** Correções sugeridas pela 2ª conta (T08, RN-58) ainda sem revisão, das mais antigas para as mais novas. */
    @Transactional(readOnly = true)
    public PaginaResponse<CorrecaoPendenteResponse> correcoesPendentes(UUID usuarioId, int pagina, int limite) {
        exigirRevisor(usuarioId);
        if (pagina < 0 || limite < 1 || limite > 50) throw RegraNegocioException.invalido("Paginação inválida.");
        var itens = jdbc.query("""
                select c.id, c.restaurante_id, r.nome, r.versao, c.nome_sugerido, c.observacao, c.usuario_id, c.criado_em
                from retornos_restaurantes c join restaurantes r on r.id = c.restaurante_id
                where c.tipo = 'CORRECAO' and c.resolvido_em is null
                order by c.criado_em, c.id limit ? offset ?
                """, (rs, i) -> new CorrecaoPendenteResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class),
                rs.getTimestamp(8).toInstant()), limite, (long) pagina * limite);
        Long total = jdbc.queryForObject(
                "select count(*) from retornos_restaurantes where tipo = 'CORRECAO' and resolvido_em is null", Long.class);
        return new PaginaResponse<>(itens, pagina, total == null ? 0 : total);
    }
    public record CorrecaoPendenteResponse(UUID id, UUID restauranteId, String nomeAtual, long versaoRestaurante,
            String nomeSugerido, String observacao, UUID usuarioId, java.time.Instant criadoEm) {}
    public RestauranteResumo revisar(UUID revisor, UUID id, RevisaoRestauranteRequest request) {
        exigirRevisor(revisor);
        catalogo.bloquearCatalogo();
        var r = restaurantes.findById(id).orElseThrow(RegraNegocioException::inexistente);
        if (r.getVersao() != request.versao())
            throw RegraNegocioException.conflito("O restaurante mudou. Recarregue antes de revisar.");
        var anterior = r.getSituacao();
        if (request.correcao() != null) {
            var c = request.correcao();
            area.exigir(c.latitude(), c.longitude());
            if (NomesRestaurantes.normalizar(c.nome()).length() < 2) throw RegraNegocioException.invalido("Nome inválido.");
            r.corrigir(c.nome(), c.latitude(), c.longitude(), c.categoria(), clock.instant());
        }
        r.revisar(request.situacao(), clock.instant());
        restaurantes.flush();
        jdbc.update("""
                insert into revisoes_restaurantes(id,restaurante_id,revisor_id,situacao_anterior,situacao_nova,motivo,criada_em)
                values (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), id, revisor, anterior.name(), request.situacao().name(),
                request.motivo().strip(), java.sql.Timestamp.from(clock.instant()));
        // A revisão do lugar resolve as correções sugeridas para ele (T08).
        jdbc.update("update retornos_restaurantes set resolvido_em = ? where restaurante_id = ? and tipo = 'CORRECAO' and resolvido_em is null",
                java.sql.Timestamp.from(clock.instant()), id);
        return RestauranteResumo.de(r);
    }
}
