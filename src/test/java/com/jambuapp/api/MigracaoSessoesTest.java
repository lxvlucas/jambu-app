package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracaoSessoesTest {
    @Test void atualizaV3PreservandoTokenExpiracaoERevogacaoSemCriarRefresh() throws Exception {
        String url = System.getenv("JAMBU_TEST_DB_URL"), usuario = System.getenv("JAMBU_TEST_DB_USER"), senha = System.getenv("JAMBU_TEST_DB_PASSWORD");
        String schema = "sessoes_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, usuario, senha).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("3").load().migrate();
        try (var conexao = DriverManager.getConnection(url, usuario, senha); var sql = conexao.createStatement()) {
            conexao.setSchema(schema);
            sql.execute("""
                insert into usuarios(id, username, email, celular, senha_hash, criado_em)
                values ('20000000-0000-0000-0000-000000000001','legado','legado@example.test','98999999999','hash-senha',now());
                insert into sessoes(id, usuario_id, token_hash, criada_em, expira_em, revogada_em) values
                ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',repeat('a',64),'2026-10-23T12:00:00Z','2026-10-24T12:00:00Z',null),
                ('50000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001',repeat('b',64),'2026-10-23T12:00:00Z','2026-10-24T12:00:00Z','2026-10-23T13:00:00Z');
                """);
            var flyway = Flyway.configure().dataSource(url, usuario, senha).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            try (var resultado = sql.executeQuery("""
                select s.canal, s.expira_em = t.expira_em as prazo_preservado,
                       s.absoluta_em = s.expira_em as limite_preservado, s.revogada_em,
                       t.token_hash from sessoes s join tokens_acesso t on t.sessao_id = s.id order by s.id
                """)) {
                assertThat(resultado.next()).isTrue();
                assertThat(resultado.getString("canal")).isEqualTo("LEGADO");
                assertThat(resultado.getBoolean("prazo_preservado")).isTrue();
                assertThat(resultado.getBoolean("limite_preservado")).isTrue();
                assertThat(resultado.getTimestamp("revogada_em")).isNull();
                assertThat(resultado.getString("token_hash")).isEqualTo("a".repeat(64));
                assertThat(resultado.next()).isTrue();
                assertThat(resultado.getTimestamp("revogada_em").toInstant()).isEqualTo(java.time.Instant.parse("2026-10-23T13:00:00Z"));
            }
            try (var resultado = sql.executeQuery("select count(*) from tokens_renovacao")) {
                resultado.next(); assertThat(resultado.getInt(1)).isZero();
            }
        }
    }
}
