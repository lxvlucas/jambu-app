package com.jambuapp.api.autenticacao;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtecaoRepeticaoTest {
    AuthProperties config(String chave) {
        return new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(180),
                Duration.ofSeconds(30), Duration.ofMinutes(10), chave, "", true, 60, 5);
    }
    @Test void exigeChaveExternaValida() {
        assertThatThrownBy(() -> new ProtecaoRepeticao(config(""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProtecaoRepeticao(config("invalida"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void autenticaConteudoTokenEOperacaoComIvDistinto() {
        var protecao = new ProtecaoRepeticao(config("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        UUID token = UUID.randomUUID(), operacao = UUID.randomUUID();
        String a = protecao.cifrar("credencial-ficticia", token, operacao);
        String b = protecao.cifrar("credencial-ficticia", token, operacao);
        assertThat(a).isNotEqualTo(b).doesNotContain("credencial-ficticia");
        assertThat(protecao.decifrar(a, token, operacao)).isEqualTo("credencial-ficticia");
        assertThatThrownBy(() -> protecao.decifrar(a, UUID.randomUUID(), operacao)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> protecao.decifrar(a, token, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        String alterada = a.substring(0, a.indexOf('.') + 1) + "AAAA";
        assertThatThrownBy(() -> protecao.decifrar(alterada, token, operacao)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejeitaPrazosIncoerentesEOrigemInsegura() {
        assertThatThrownBy(() -> new AuthProperties(Duration.ofDays(31), Duration.ofDays(30), Duration.ofDays(180),
                Duration.ofSeconds(30), Duration.ofMinutes(10), "", "", true, 60, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(180),
                Duration.ofSeconds(30), Duration.ofMinutes(10), "", "http://jambu.example", true, 60, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
