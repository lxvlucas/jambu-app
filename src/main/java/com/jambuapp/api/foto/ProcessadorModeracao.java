package com.jambuapp.api.foto;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessadorModeracao {
    private final ModeracaoService moderacao;
    private final FotoService fotos;
    private final FotoProperties config;

    public ProcessadorModeracao(ModeracaoService moderacao, FotoService fotos, FotoProperties config) {
        this.moderacao = moderacao;
        this.fotos = fotos;
        this.config = config;
    }

    public int processarLote() {
        return moderacao.processarPendentes();
    }

    @Scheduled(fixedDelayString = "${jambu.fotos.intervalo-agendador-ms:5000}")
    public void executarCicloAgendado() {
        if (!config.agendadorModeracaoHabilitado()) return;
        moderacao.processarPendentes();
        fotos.limparArquivosExpiradosEOrfaos();
    }
}
