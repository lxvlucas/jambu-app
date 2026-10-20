package com.jambuapp.api.meal;

import java.time.LocalDate;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record MealRequest(@NotNull UUID restauranteId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid DishRequest> dishes,
        @Size(max = 10000) String review, @NotNull Boolean comeriaNovamente,
        Modalidade modalidade, LocalDate dataRefeicao, @PositiveOrZero Long versao) {}
