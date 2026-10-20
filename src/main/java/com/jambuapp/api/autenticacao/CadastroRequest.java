package com.jambuapp.api.autenticacao;

import jakarta.validation.constraints.*;

public record CadastroRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,30}") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String celular,
        @NotBlank @Size(min = 12, max = 128) String senha) {}
