package com.jambuapp.api.restaurante;

import jakarta.validation.constraints.*;

/** Na CORRECAO, informe o nome certo, uma observação ou os dois; na CONFIRMACAO, nenhum dos dois. */
public record RetornoRestauranteRequest(@NotNull TipoRetorno tipo, @Size(max = 150) String nomeSugerido,
        @Size(max = 500) String observacao) {}
