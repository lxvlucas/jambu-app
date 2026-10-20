package com.jambuapp.api.autenticacao;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Size(max = 254) String identificador,
                           @NotBlank @Size(max = 128) String senha) {}
