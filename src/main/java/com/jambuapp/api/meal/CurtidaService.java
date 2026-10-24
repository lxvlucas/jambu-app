package com.jambuapp.api.meal;

import java.time.Clock;
import java.util.UUID;
import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Curtidas (T20): uma por pessoa por Meal ativa; curtir e descurtir são idempotentes. */
@Service
@Transactional
public class CurtidaService {
    private final MealRepository meals;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CurtidaService(MealRepository meals, JdbcTemplate jdbc, Clock clock) {
        this.meals = meals; this.jdbc = jdbc; this.clock = clock;
    }
    public void curtir(UUID usuarioId, UUID mealId) {
        exigirAtiva(mealId);
        jdbc.update("insert into curtidas(meal_id, usuario_id, criada_em) values (?, ?, ?) on conflict do nothing",
                mealId, usuarioId, java.sql.Timestamp.from(clock.instant()));
    }
    public void descurtir(UUID usuarioId, UUID mealId) {
        exigirAtiva(mealId);
        jdbc.update("delete from curtidas where meal_id = ? and usuario_id = ?", mealId, usuarioId);
    }
    @Transactional(readOnly = true)
    public ResumoCurtidas resumo(UUID usuarioId, UUID mealId) {
        exigirAtiva(mealId);
        Integer quantidade = jdbc.queryForObject("select count(*) from curtidas where meal_id = ?", Integer.class, mealId);
        Boolean minha = jdbc.queryForObject("select exists(select 1 from curtidas where meal_id = ? and usuario_id = ?)",
                Boolean.class, mealId, usuarioId);
        return new ResumoCurtidas(quantidade == null ? 0 : quantidade, Boolean.TRUE.equals(minha));
    }
    /** Resumo de várias Meals numa consulta só, para o feed (T19); Meal sem curtida não aparece no mapa. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, ResumoCurtidas> resumos(UUID usuarioId, java.util.Collection<UUID> mealIds) {
        if (mealIds.isEmpty()) return java.util.Map.of();
        var mapa = new java.util.HashMap<UUID, ResumoCurtidas>();
        jdbc.query("""
                select meal_id, count(*), bool_or(usuario_id = ?) from curtidas
                where meal_id = any(?) group by meal_id
                """, rs -> { mapa.put(rs.getObject(1, UUID.class), new ResumoCurtidas(rs.getLong(2), rs.getBoolean(3))); },
                usuarioId, mealIds.toArray(UUID[]::new));
        return mapa;
    }
    private void exigirAtiva(UUID mealId) {
        if (meals.findByIdAndExcluidaEmIsNull(mealId).isEmpty()) throw RegraNegocioException.inexistente();
    }
    public record ResumoCurtidas(long quantidade, boolean curtidaPorMim) {}
}
