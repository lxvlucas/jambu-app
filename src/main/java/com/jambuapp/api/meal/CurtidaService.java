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
    private void exigirAtiva(UUID mealId) {
        if (meals.findByIdAndExcluidaEmIsNull(mealId).isEmpty()) throw RegraNegocioException.inexistente();
    }
    public record ResumoCurtidas(long quantidade, boolean curtidaPorMim) {}
}
