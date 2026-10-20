package com.jambuapp.api.seguranca;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import com.jambuapp.api.comum.JambuProperties;
import org.springframework.stereotype.Component;

/** Limite por IP, em memória e com cardinalidade limitada, para uma instância interna. */
@Component
public class LimiteAutenticacao {
    private final Map<String, Janela> janelas = new HashMap<>();
    private final Clock clock;
    private final int limite;
    public LimiteAutenticacao(Clock clock, JambuProperties config) {
        this.clock = clock;
        this.limite = config.limiteAutenticacaoMinuto();
    }
    public synchronized boolean permitir(String ip) {
        long minuto = clock.instant().getEpochSecond() / 60;
        janelas.entrySet().removeIf(e -> e.getValue().minuto != minuto);
        if (!janelas.containsKey(ip) && janelas.size() >= 10000) return false;
        var janela = janelas.computeIfAbsent(ip, chave -> new Janela(minuto));
        return ++janela.tentativas <= limite;
    }
    private static class Janela {
        final long minuto;
        int tentativas;
        Janela(long minuto) { this.minuto = minuto; }
    }
}
