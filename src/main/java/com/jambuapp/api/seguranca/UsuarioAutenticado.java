package com.jambuapp.api.seguranca;

import java.util.UUID;

public record UsuarioAutenticado(UUID usuarioId, UUID sessaoId, String username) {}
