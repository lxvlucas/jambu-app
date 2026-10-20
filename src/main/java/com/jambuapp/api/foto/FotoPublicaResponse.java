package com.jambuapp.api.foto;

import java.util.UUID;

public record FotoPublicaResponse(UUID id, String url, String urlMiniatura) {
    public static FotoPublicaResponse de(Foto foto) {
        if (foto == null || !foto.estaAprovada()) return null;
        return new FotoPublicaResponse(
                foto.getId(),
                "/api/fotos/" + foto.getId() + "/conteudo",
                "/api/fotos/" + foto.getId() + "/miniatura");
    }
}
