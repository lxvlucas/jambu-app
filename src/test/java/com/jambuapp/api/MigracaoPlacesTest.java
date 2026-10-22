package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracaoPlacesTest {
    @Test
    void preservaSeedDevAnteriorSemReexecutarOuAlterarSeuHistorico() throws Exception {
        String url = System.getenv("JAMBU_TEST_DB_URL");
        String user = System.getenv("JAMBU_TEST_DB_USER");
        String password = System.getenv("JAMBU_TEST_DB_PASSWORD");
        String schema = "seed_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration", "classpath:db/dev").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            connection.setSchema(schema);
            String checksum = valor(sql, "select checksum from flyway_schema_history where version is null and type = 'SQL'");
            assertThat(checksum).isNotNull();
            var atualizadoV2 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").ignoreMigrationPatterns("repeatable:missing", "*:future").target("2").load();
            assertThat(atualizadoV2.migrate().migrationsExecuted).isEqualTo(1);
            atualizadoV2.validate();
            assertThat(valor(sql, "select count(*) from restaurantes where google_place_id is null")).isEqualTo("2");
            assertThat(valor(sql, "select checksum from flyway_schema_history where version is null and type = 'SQL'")).isEqualTo(checksum);
            // A linha SCHEMA, criada pelo Flyway no schema isolado, não é uma migração SQL.
            assertThat(valor(sql, "select count(*) from flyway_schema_history where success = true and type = 'SQL'")).isEqualTo("3");
            var atualizadoV3 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").ignoreMigrationPatterns("repeatable:missing", "*:future").target("3").load();
            assertThat(atualizadoV3.migrate().migrationsExecuted).isEqualTo(1);
            atualizadoV3.validate();
            assertThat(valor(sql, "select count(*) from flyway_schema_history where success = true and type = 'SQL'")).isEqualTo("4");
        }
    }
    @Test
    void atualizaV1PreservandoLegadoRelacoesSessoesESnapshot() throws Exception {
        String url = System.getenv("JAMBU_TEST_DB_URL");
        String user = System.getenv("JAMBU_TEST_DB_USER");
        String password = System.getenv("JAMBU_TEST_DB_PASSWORD");
        String schema = "migracao_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            connection.setSchema(schema);
            sql.execute("""
                    insert into usuarios(id,username,email,celular,senha_hash,criado_em) values
                    ('20000000-0000-0000-0000-000000000001','legado1','legado1@example.test','98999999999','hash-legado',now()),
                    ('20000000-0000-0000-0000-000000000002','legado2','legado2@example.test','98999999999','hash-legado',now());
                    insert into restaurantes(id,nome,endereco) values
                    ('10000000-0000-0000-0000-000000000001','Nome próprio legado','Endereço próprio legado');
                    insert into meals(id,autor_id,restaurante_id,comeria_novamente,data_refeicao,publicada_em) values
                    ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',true,'2026-10-19',now());
                    insert into dishes(id,meal_id,ordem,nome,nota) values
                    ('40000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',0,'Prato',4.5);
                    insert into sessoes(id,usuario_id,token_hash,criada_em,expira_em) values
                    ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','hash-token-legado',now(),now()+interval '1 day');
                    insert into seguimentos(id,seguidor_id,seguido_id,criado_em) values
                    ('60000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002',now());
                    insert into publicacoes_diarias(id,usuario_id,dia,quantidade) values
                    ('70000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','2026-10-19',4);
                    insert into publicacoes_idempotentes(id,autor_id,resultado_id,chave,hash_requisicao,resposta_json,criada_em) values
                    ('80000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',
                    'chave-legada','hash-requisicao-legado','{"restaurante":{"id":"10000000-0000-0000-0000-000000000001","nome":"Nome próprio legado","endereco":"Endereço próprio legado"}}',now());
                    """);
            String snapshot = valor(sql, "select resposta_json from publicacoes_idempotentes");
            var atualizadoV2 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("2").load();
            assertThat(atualizadoV2.migrate().migrationsExecuted).isEqualTo(1);
            atualizadoV2.validate();
            var atualizado = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("3").load();
            assertThat(atualizado.migrate().migrationsExecuted).isEqualTo(1);
            atualizado.validate();
            for (String tabelaFoto : new String[]{"fotos", "fotos_perfil", "fotos_dish", "tarefas_moderacao", "registros_moderacao"})
                assertThat(valor(sql, "select to_regclass('" + schema + "." + tabelaFoto + "')")).isEqualTo(tabelaFoto);
            assertThat(valor(sql, "select count(*) from usuarios")).isEqualTo("2");
            for (String tabela : new String[]{"meals", "dishes", "sessoes", "seguimentos", "publicacoes_idempotentes"})
                assertThat(valor(sql, "select count(*) from " + tabela)).isEqualTo("1");
            assertThat(valor(sql, "select to_regclass('" + schema + ".publicacoes_diarias')")).isNull();
            assertThat(valor(sql, "select nome from restaurantes")).isEqualTo("Nome próprio legado");
            assertThat(valor(sql, "select endereco from restaurantes")).isEqualTo("Endereço próprio legado");
            assertThat(valor(sql, "select google_place_id from restaurantes")).isNull();
            assertThat(valor(sql, "select places_validado_em from restaurantes")).isNull();
            assertThat(valor(sql, "select restaurante_id from meals")).isEqualTo("10000000-0000-0000-0000-000000000001");
            assertThat(valor(sql, "select resposta_json from publicacoes_idempotentes")).isEqualTo(snapshot);
            assertThat(valor(sql, "select hash_requisicao from publicacoes_idempotentes")).isEqualTo("hash-requisicao-legado");
            assertThatThrownBy(() -> sql.execute("insert into restaurantes(id,nome,endereco) values(gen_random_uuid(),'Manual','Manual')"))
                    .isInstanceOf(SQLException.class);
            sql.execute("insert into restaurantes(id,google_place_id,places_validado_em) values(gen_random_uuid(),'teste_migracao',now())");
            assertThatThrownBy(() -> sql.execute("insert into restaurantes(id,google_place_id,places_validado_em) values(gen_random_uuid(),'teste_migracao',now())"))
                    .isInstanceOf(SQLException.class);
            assertThat(valor(sql, "select count(*) from information_schema.columns where table_schema = '" + schema
                    + "' and (column_name ilike '%private%' or column_name ilike '%visib%' or column_name ilike '%pendente%')")).isEqualTo("0");
        }
        // O schema pertence ao PostgreSQL descartável do gate; não há clean/drop em bancos externos.
    }
    private String valor(Statement sql, String consulta) throws SQLException {
        try (var resultado = sql.executeQuery(consulta)) { resultado.next(); return resultado.getString(1); }
    }
}
