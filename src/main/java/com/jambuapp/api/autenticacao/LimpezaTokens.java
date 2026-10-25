package com.jambuapp.api.autenticacao;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LimpezaTokens {
    private final TokenRenovacaoRepository refreshes;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LimpezaTokens(TokenRenovacaoRepository refreshes, JdbcTemplate jdbc, Clock clock) {
        this.refreshes = refreshes; this.jdbc = jdbc; this.clock = clock;
    }
    @Scheduled(initialDelayString = "${jambu.auth.limpeza-intervalo-ms:5000}", fixedDelayString = "${jambu.auth.limpeza-intervalo-ms:5000}") @Transactional
    public void limpar() {
        refreshes.limparRespostas(clock.instant());
        // Mantém o histórico de refresh enquanto a sessão ainda pode ser renovada.
        jdbc.update("delete from tokens_acesso where expira_em < ?", java.sql.Timestamp.from(clock.instant().minusSeconds(86400)));
        // T14 (P-11): o histórico só serve para detectar reuso numa sessão que ainda renova. Encerrada (revogada ou
        // expirada) há mais de 30 dias, os refresh tokens saem; a sessão continua registrada.
        jdbc.update("""
                delete from tokens_renovacao where sessao_id in (
                    select id from sessoes where least(coalesce(revogada_em, 'infinity'::timestamptz), expira_em) < ?)
                """, java.sql.Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(30))));
    }
}
