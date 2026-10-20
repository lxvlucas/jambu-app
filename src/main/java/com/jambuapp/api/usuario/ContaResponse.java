package com.jambuapp.api.usuario;

import java.util.UUID;

public record ContaResponse(UUID id, String username, String email, String celular) {
    public static ContaResponse de(Usuario usuario) {
        return new ContaResponse(usuario.getId(), usuario.getUsername(), usuario.getEmail(), usuario.getCelular());
    }
}
