package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.jambuapp.api.foto.FotoPublicaResponse;
import com.jambuapp.api.usuario.UsuarioResumoResponse;
import com.jambuapp.api.restaurante.RestauranteReferenciaResponse;

/** fotos (T16): só as aprovadas, na ordem do post (a 1ª é a capa); lista vazia quando nenhuma está aprovada. */
public record MealResponse(UUID id, UsuarioResumoResponse autor, RestauranteReferenciaResponse restaurante,
        List<DishResponse> dishes, List<FotoPublicaResponse> fotos, BigDecimal notaOverall, String review,
        Boolean comeriaNovamente, Modalidade modalidade, LocalDate dataRefeicao, Instant publicadaEm, Instant editadaEm,
        long versao) {
    public static MealResponse de(Meal meal, List<FotoPublicaResponse> fotos) {
        return new MealResponse(meal.getId(), UsuarioResumoResponse.de(meal.getAutor()),
                RestauranteReferenciaResponse.de(meal.getRestaurante()), meal.getDishes().stream()
                .map(d -> new DishResponse(d.getId(), d.getNome(), d.getNota(), d.getComeriaNovamente(), d.getPreco())).toList(),
                List.copyOf(fotos), meal.getNotaOverall(), meal.getReview(), meal.getComeriaNovamente(), meal.getModalidade(),
                meal.getDataRefeicao(), meal.getPublicadaEm(), meal.getEditadaEm(), meal.getVersao());
    }
    /**
     * comeriaNovamente: true (marcado) ou false (desmarcado), nunca nulo nas respostas novas; o tipo continua
     * Boolean para ler respostas de idempotência gravadas antes da V7. preco nulo quando não informado (T15).
     */
    public record DishResponse(UUID id, String nome, BigDecimal nota, Boolean comeriaNovamente, BigDecimal preco) {}
}
