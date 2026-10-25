package com.jambuapp.api.meal;

import java.time.LocalDate;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** fotoIds (T16): fotos do post enviadas antes por POST /api/fotos, na ordem de exibição; a 1ª é a capa. */
public record MealRequest(@NotNull UUID restauranteId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid DishRequest> dishes,
        @NotEmpty @Size(max = 10) List<@NotNull UUID> fotoIds,
        @Size(max = 10000) String review, Boolean comeriaNovamente,
        Modalidade modalidade, LocalDate dataRefeicao, @PositiveOrZero Long versao) {}
