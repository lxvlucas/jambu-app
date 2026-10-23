package com.jambuapp.api.autenticacao;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record RefreshRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken, @NotNull UUID operacaoId) {}
