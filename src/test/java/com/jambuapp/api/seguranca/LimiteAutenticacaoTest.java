package com.jambuapp.api.seguranca;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.time.*;
import com.jambuapp.api.comum.JambuProperties;
import org.junit.jupiter.api.Test;

class LimiteAutenticacaoTest {
    @Test
    void limitaTentativasPorIpELiberaNovaJanela() {
        var clock = new Relogio();
        var properties = new JambuProperties(20, 2000, Duration.ofHours(24), 2, URI.create("http://localhost/api/meals/"));
        var limite = new LimiteAutenticacao(clock, properties);
        assertThat(limite.permitir("127.0.0.1")).isTrue();
        assertThat(limite.permitir("127.0.0.1")).isTrue();
        assertThat(limite.permitir("127.0.0.1")).isFalse();
        assertThat(limite.permitir("127.0.0.2")).isTrue();
        clock.agora = clock.agora.plusSeconds(60);
        assertThat(limite.permitir("127.0.0.1")).isTrue();
    }
    private static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-10-19T03:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(agora, zone); }
        @Override public Instant instant() { return agora; }
    }
}
