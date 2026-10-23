package com.jambuapp.api.autenticacao;
import jakarta.validation.constraints.*;
public record LogoutRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {}
