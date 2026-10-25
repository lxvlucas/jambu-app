package com.jambuapp.api.restaurante;

import jakarta.validation.constraints.*;

/** No corpo, e não na URL, para a localização da pessoa não ficar em logs de acesso. */
public record RestaurantesProximosRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
