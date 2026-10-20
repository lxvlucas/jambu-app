package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.*;

public record FotoModeracaoAdminResponse(
        UUID id,
        UUID autorId,
        String autorUsername,
        String tipo,
        StatusModeracaoFoto status,
        boolean atual,
        String hashConteudo,
        int largura,
        int altura,
        Instant criadaEm,
        Instant atualizadaEm,
        List<HistoricoModeracaoItem> historico) {

    public static FotoModeracaoAdminResponse de(Foto foto, List<RegistroModeracao> registros) {
        String tipo = (foto instanceof FotoPerfil) ? "PERFIL" : "DISH";
        return new FotoModeracaoAdminResponse(
                foto.getId(),
                foto.getAutor().getId(),
                foto.getAutor().getUsername(),
                tipo,
                foto.getStatus(),
                foto.isAtual(),
                foto.getHashConteudo(),
                foto.getLargura(),
                foto.getAltura(),
                foto.getCriadaEm(),
                foto.getAtualizadaEm(),
                registros.stream().map(HistoricoModeracaoItem::de).toList());
    }

    public record HistoricoModeracaoItem(
            UUID id,
            OrigemModeracao origem,
            ResultadoModeracao resultado,
            String motivo,
            String versaoPolitica,
            String provedor,
            String versaoModelo,
            UUID revisorId,
            Instant criadoEm) {
        public static HistoricoModeracaoItem de(RegistroModeracao r) {
            return new HistoricoModeracaoItem(
                    r.getId(),
                    r.getOrigem(),
                    r.getResultado(),
                    r.getMotivo(),
                    r.getVersaoPolitica(),
                    r.getProvedor(),
                    r.getVersaoModelo(),
                    r.getRevisorId(),
                    r.getCriadoEm());
        }
    }
}
