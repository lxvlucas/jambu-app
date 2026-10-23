package com.jambuapp.api.autenticacao;
import jakarta.validation.constraints.*;
public record ReautenticarRequest(@NotBlank @Size(max = 128) String senha) {}
