package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.jambuapp.api.foto.FotoPublicaResponse;
import com.jambuapp.api.usuario.UsuarioResumoResponse;
import com.jambuapp.api.restaurante.RestauranteReferenciaResponse;

public record MealResponse(UUID id, UsuarioResumoResponse autor, RestauranteReferenciaResponse restaurante,
        List<DishResponse> dishes, BigDecimal notaOverall, String review, Boolean comeriaNovamente,
        Modalidade modalidade, LocalDate dataRefeicao, Instant publicadaEm, Instant editadaEm, long versao) {
    public static MealResponse de(Meal meal) {
        return de(meal, Map.of());
    }
    public static MealResponse de(Meal meal, Map<UUID, FotoPublicaResponse> fotosPorDish) {
        return new MealResponse(meal.getId(), UsuarioResumoResponse.de(meal.getAutor()),
                RestauranteReferenciaResponse.de(meal.getRestaurante()), meal.getDishes().stream()
                .map(d -> new DishResponse(d.getId(), d.getNome(), d.getNota(), fotosPorDish.get(d.getId()),
                        d.getComeriaNovamente(), d.getPreco())).toList(),
                meal.getNotaOverall(), meal.getReview(), meal.getComeriaNovamente(), meal.getModalidade(),
                meal.getDataRefeicao(), meal.getPublicadaEm(), meal.getEditadaEm(), meal.getVersao());
    }
    /**
     * comeriaNovamente: true (marcado) ou false (desmarcado), nunca nulo nas respostas novas; o tipo continua
     * Boolean para ler respostas de idempotência gravadas antes da V9. preco nulo quando não informado (T15).
     */
    public record DishResponse(UUID id, String nome, BigDecimal nota, FotoPublicaResponse foto,
            Boolean comeriaNovamente, BigDecimal preco) {
        public DishResponse(UUID id, String nome, BigDecimal nota, FotoPublicaResponse foto) {
            this(id, nome, nota, foto, null, null);
        }
        public DishResponse(UUID id, String nome, BigDecimal nota) {
            this(id, nome, nota, null, null, null);
        }
    }
}
