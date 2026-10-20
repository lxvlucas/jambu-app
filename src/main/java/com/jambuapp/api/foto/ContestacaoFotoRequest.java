package com.jambuapp.api.foto;

import jakarta.validation.constraints.*;

public record ContestacaoFotoRequest(
        @NotBlank @Size(max = 500) String motivo) {}
