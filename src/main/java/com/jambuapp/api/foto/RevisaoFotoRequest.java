package com.jambuapp.api.foto;

import jakarta.validation.constraints.*;

public record RevisaoFotoRequest(
        @NotNull ResultadoModeracao resultado,
        @NotBlank @Size(max = 500) String motivo) {}
