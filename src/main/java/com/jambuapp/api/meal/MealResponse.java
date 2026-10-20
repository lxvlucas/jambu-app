package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.jambuapp.api.foto.FotoPublicaResponse;
import com.jambuapp.api.usuario.UsuarioResumoResponse;
import com.jambuapp.api.restaurante.RestauranteReferenciaResponse;

public record MealResponse(UUID id, UsuarioResumoResponse autor, RestauranteReferenciaResponse restaurante,
        List<DishResponse> dishes, BigDecimal notaOverall, String review, boolean comeriaNovamente,
        Modalidade modalidade, LocalDate dataRefeicao, Instant publicadaEm, Instant editadaEm, long versao) {
    public static MealResponse de(Meal meal) {
        return de(meal, Map.of());
    }
    public static MealResponse de(Meal meal, Map<UUID, FotoPublicaResponse> fotosPorDish) {
        return new MealResponse(meal.getId(), UsuarioResumoResponse.de(meal.getAutor()),
                RestauranteReferenciaResponse.de(meal.getRestaurante()), meal.getDishes().stream()
                .map(d -> new DishResponse(d.getId(), d.getNome(), d.getNota(), fotosPorDish.get(d.getId()))).toList(),
                meal.getNotaOverall(), meal.getReview(), meal.isComeriaNovamente(), meal.getModalidade(),
                meal.getDataRefeicao(), meal.getPublicadaEm(), meal.getEditadaEm(), meal.getVersao());
    }
    public record DishResponse(UUID id, String nome, BigDecimal nota, FotoPublicaResponse foto) {
        public DishResponse(UUID id, String nome, BigDecimal nota) {
            this(id, nome, nota, null);
        }
    }
}
