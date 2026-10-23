package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

/**
 * comeriaNovamente e preco são opcionais (T15). Nulos ficam fora do JSON para que pedidos sem os
 * campos novos mantenham o mesmo hash de idempotência de antes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DishRequest(UUID id, @NotBlank @Size(max = 150) String nome, @NotNull BigDecimal nota,
        Boolean comeriaNovamente, @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal preco) {
    public DishRequest(UUID id, String nome, BigDecimal nota) {
        this(id, nome, nota, null, null);
    }
}
