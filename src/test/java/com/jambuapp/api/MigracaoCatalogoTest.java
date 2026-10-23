package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracaoCatalogoTest {
    @Test void v4PreservaLegadosETabelasDeFotosEChecksumsAnteriores() throws Exception {
        String url = System.getenv("JAMBU_TEST_DB_URL"), user = System.getenv("JAMBU_TEST_DB_USER"), password = System.getenv("JAMBU_TEST_DB_PASSWORD");
        String schema = "catalogo_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration", "classpath:db/dev").target("1").load().migrate();
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").ignoreMigrationPatterns("repeatable:missing", "*:future").target("3").load().migrate();
        try (var c = DriverManager.getConnection(url, user, password); var sql = c.createStatement()) {
            c.setSchema(schema);
            String checksums = valor(sql, "select string_agg(version || ':' || checksum, ',' order by version) from flyway_schema_history where version in ('1','2','3')");
            String legados = valor(sql, "select string_agg(id || ':' || nome || ':' || endereco, ',' order by id) from restaurantes");
            sql.execute("insert into restaurantes(id,google_place_id,places_validado_em) values ('10000000-0000-0000-0000-000000000099','fixture_google',now())");
            var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").ignoreMigrationPatterns("repeatable:missing", "*:future").target("4").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(valor(sql, "select string_agg(version || ':' || checksum, ',' order by version) from flyway_schema_history where version in ('1','2','3')")).isEqualTo(checksums);
            assertThat(valor(sql, "select string_agg(id || ':' || nome || ':' || endereco, ',' order by id) from restaurantes where google_place_id is null")).isEqualTo(legados);
            assertThat(valor(sql, "select origem from restaurantes where google_place_id = 'fixture_google'")).isEqualTo("GOOGLE_PLACES");
            for (String tabela : new String[]{"meals","publicacoes_idempotentes","fotos","fotos_dish","registros_moderacao"})
                assertThat(valor(sql, "select to_regclass('" + schema + "." + tabela + "')")).isEqualTo(tabela);
            sql.execute("""
                    insert into restaurantes(id,nome,nome_normalizado,latitude,longitude,categoria,origem,situacao)
                    values (gen_random_uuid(),'Local de teste','local de teste',-2.53,-44.30,'RESTAURANTE','USUARIO','PROVISORIO')
                    """);
            assertThatThrownBy(() -> sql.execute("""
                    insert into restaurantes(id,nome,origem) values(gen_random_uuid(),'Sem pin','USUARIO')
                    """)).isInstanceOf(SQLException.class);
        }
    }
    private String valor(Statement sql, String query) throws SQLException {
        try (var rs = sql.executeQuery(query)) { rs.next(); return rs.getString(1); }
    }
}
