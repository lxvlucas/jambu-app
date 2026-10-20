package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.*;

public record DishRequest(UUID id, @NotBlank @Size(max = 150) String nome, @NotNull BigDecimal nota) {}
