package com.jambuapp.api.restaurante.overture;

import java.util.List;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record ImportacaoOvertureRequest(@NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}\\.\\d+") String release,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 80) String> licencas,
        @NotEmpty @Size(max = 200) List<JsonNode> features) {}
