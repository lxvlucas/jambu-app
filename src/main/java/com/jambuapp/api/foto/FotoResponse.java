package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;

public record FotoResponse(
        UUID id,
        StatusModeracaoFoto status,
        boolean atual,
        int largura,
        int altura,
        String url,
        String urlMiniatura,
        String mensagem,
        Instant criadaEm,
        Instant atualizadaEm) {

    public static FotoResponse de(Foto foto) {
        boolean aprovada = foto.estaAprovada();
        return new FotoResponse(
                foto.getId(),
                foto.getStatus(),
                foto.isAtual(),
                foto.getLargura(),
                foto.getAltura(),
                aprovada ? "/api/fotos/" + foto.getId() + "/conteudo" : null,
                aprovada ? "/api/fotos/" + foto.getId() + "/miniatura" : null,
                mensagemPara(foto.getStatus()),
                foto.getCriadaEm(),
                foto.getAtualizadaEm());
    }

    public static String mensagemPara(StatusModeracaoFoto status) {
        return switch (status) {
            case PENDENTE, EM_ANALISE -> "Foto em análise";
            case APROVADA -> "Foto aprovada";
            case REJEITADA -> "Sua foto não foi publicada por conter conteúdo não permitido";
            case EM_REVISAO -> "Revisão solicitada";
            case REMOVIDA -> "Foto removida";
        };
    }
}
