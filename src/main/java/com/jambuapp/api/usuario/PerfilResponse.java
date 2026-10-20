package com.jambuapp.api.usuario;

import java.util.UUID;
import com.jambuapp.api.foto.FotoPublicaResponse;

public record PerfilResponse(UUID id, String username, long seguidores, long seguindo, long quantidadeMeals,
        FotoPublicaResponse foto) {
    public PerfilResponse(UUID id, String username, long seguidores, long seguindo, long quantidadeMeals) {
        this(id, username, seguidores, seguindo, quantidadeMeals, null);
    }
}
