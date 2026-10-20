package com.jambuapp.api.meal;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.usuario.Usuario;
import com.jambuapp.api.restaurante.Restaurante;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DominioTest {
    @ParameterizedTest
    @ValueSource(strings = {"0", "0.49", "0.75", "4.99", "5.5", "-1"})
    void rejeitaNotasInvalidasAntesDoArredondamentoNoBanco(String nota) {
        assertThatThrownBy(() -> Dish.validarNota(new BigDecimal(nota))).isInstanceOf(RegraNegocioException.class);
    }
    @Test
    void calculaMediaDecimalSemAceitarOverallDoCliente() {
        var meal = meal("5", "4.5", "0.5");
        assertThat(meal.getNotaOverall()).isEqualByComparingTo("3.33");
    }
    @Test
    void impedeRemocaoDoUltimoDish() {
        var meal = meal("5");
        var request = new MealRequest(UUID.randomUUID(), List.of(), null, true, null, null, 0L);
        assertThatThrownBy(() -> meal.editar(Restaurante.doPlaces("teste", Instant.now()), request, Instant.now(), LocalDate.now()))
                .isInstanceOf(RegraNegocioException.class);
    }
    @Test
    void cursorPreservaInstanteEIdentificador() {
        var cursor = new CursorMeal(Instant.parse("2026-10-19T03:00:00.123456Z"), UUID.randomUUID());
        assertThat(CursorMeal.ler(cursor.codificar())).isEqualTo(cursor);
        assertThatThrownBy(() -> CursorMeal.ler("invalido")).isInstanceOf(RegraNegocioException.class);
    }
    private Meal meal(String... notas) {
        var dishes = Arrays.stream(notas).map(n -> new DishRequest(null, "Prato", new BigDecimal(n))).toList();
        var request = new MealRequest(UUID.randomUUID(), dishes, null, true, null, null, null);
        return new Meal(new Usuario("teste", "a@example.test", "98999999999", "hash", Instant.now()),
                Restaurante.doPlaces("teste", Instant.now()), request, Instant.now(), LocalDate.now());
    }
}
