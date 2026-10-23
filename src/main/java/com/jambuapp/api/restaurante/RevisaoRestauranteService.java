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
            if (!c.categoria().aceitaCadastro()) throw RegraNegocioException.invalido(CategoriaRestaurante.MENSAGEM_CATEGORIAS);
            r.corrigir(c.nome(), c.latitude(), c.longitude(), c.categoria(), clock.instant());
        }
        r.revisar(request.situacao(), clock.instant());
        restaurantes.flush();
        jdbc.update("""
                insert into revisoes_restaurantes(id,restaurante_id,revisor_id,situacao_anterior,situacao_nova,motivo,criada_em)
                values (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), id, revisor, anterior.name(), request.situacao().name(),
                request.motivo().strip(), java.sql.Timestamp.from(clock.instant()));
        return RestauranteResumo.de(r);
    }
}
