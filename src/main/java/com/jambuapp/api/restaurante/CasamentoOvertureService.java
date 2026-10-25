package com.jambuapp.api.restaurante;

import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casamento do caso (b) da RN-50 (T05): o lugar escolhido no Google que existe escondido no Overture (confiança
 * 0,3 a 0,5) vira restaurante com o nome e o pin do Overture, provisório. Do Google, só o place_id é guardado.
 */
@Service
@Transactional
public class CasamentoOvertureService {
    private final RestauranteRepository restaurantes;
    private final CatalogoTransacionalService catalogo;
    private final CatalogoProperties config;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CasamentoOvertureService(RestauranteRepository restaurantes, CatalogoTransacionalService catalogo,
            CatalogoProperties config, JdbcTemplate jdbc, Clock clock) {
        this.restaurantes = restaurantes; this.catalogo = catalogo; this.config = config; this.jdbc = jdbc; this.clock = clock;
    }
    /** Escondidos com nome parecido com o texto que a pessoa digitou (trigramas), um punhado por vez. */
    @Transactional(readOnly = true)
    public List<Candidato> candidatos(String textoBusca) {
        String termo = NomesRestaurantes.normalizar(textoBusca);
        if (termo.length() < 3) return List.of();
        return jdbc.query("""
                select overture_id, nome, latitude, longitude from referencias_overture
                where nome_normalizado % ? or ? <% nome_normalizado
                order by similarity(nome_normalizado, ?) desc, overture_id limit ?
                """, (rs, i) -> new Candidato(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4)),
                termo, termo, termo, config.candidatosCasamento());
    }
    /**
     * Cria o restaurante a partir do escondido, sob o lock do catálogo. Se outra seleção já criou, devolve o
     * restaurante dela; se o escondido sumiu sem virar restaurante com esse place_id, não casa.
     */
    public Optional<UUID> criar(String overtureId, String placeId) {
        catalogo.bloquearCatalogo();
        var existente = restaurantes.findByGooglePlaceId(placeId);
        if (existente.isPresent()) return Optional.of(existente.get().getId());
        var linhas = jdbc.queryForList("select * from referencias_overture where overture_id = ?", overtureId);
        if (linhas.isEmpty()) return Optional.empty();
        var e = linhas.getFirst();
        var agora = clock.instant();
        var r = Restaurante.importado((String) e.get("nome"), (Double) e.get("latitude"), (Double) e.get("longitude"),
                CategoriaRestaurante.valueOf((String) e.get("categoria")), agora);
        r.atualizarOverture(overtureId, (String) e.get("overture_release"), (Integer) e.get("overture_versao"),
                (String) e.get("fontes_overture"), (String) e.get("licencas_overture"), (String) e.get("atribuicao_overture"),
                (String) e.get("nome"), (Double) e.get("latitude"), (Double) e.get("longitude"),
                CategoriaRestaurante.valueOf((String) e.get("categoria")), (String) e.get("endereco"), agora);
        // Confiança baixa: entra provisório, como os escondidos que sobem de faixa (RN-56).
        r.revisar(SituacaoRestaurante.PROVISORIO, agora);
        r.vincularGoogle(placeId, agora);
        restaurantes.saveAndFlush(r);
        jdbc.update("delete from referencias_overture where overture_id = ?", overtureId);
        return Optional.of(r.getId());
    }
    public record Candidato(String overtureId, String nome, double latitude, double longitude) {}
}
