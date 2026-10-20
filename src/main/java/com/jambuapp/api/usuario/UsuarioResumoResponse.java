package com.jambuapp.api.usuario;

import java.util.UUID;

public record UsuarioResumoResponse(UUID id, String username) {
    public static UsuarioResumoResponse de(Usuario usuario) {
        return new UsuarioResumoResponse(usuario.getId(), usuario.getUsername());
    }
}
