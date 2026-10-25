package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracaoFotosTest {
    @Test void v18LevaFotoPorDishParaOPostNaOrdemDosDishesELimpaRespostasGuardadas() throws Exception {
        // T16 (decisão de 25/10): a foto por dish sai; a ativa vira foto da Meal, na ordem dos dishes.
        String url = System.getenv("JAMBU_TEST_DB_URL"), usuario = System.getenv("JAMBU_TEST_DB_USER"), senha = System.getenv("JAMBU_TEST_DB_PASSWORD");
        String schema = "fotos_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, usuario, senha).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("17").load().migrate();
        try (var conexao = DriverManager.getConnection(url, usuario, senha); var sql = conexao.createStatement()) {
            conexao.setSchema(schema);
            sql.execute("""
                insert into usuarios(id, username, email, celular, senha_hash, criado_em)
                values ('20000000-0000-0000-0000-000000000001','autor','autor@example.test','98999999999','hash',now());
                insert into restaurantes(id,nome,nome_normalizado,latitude,longitude,categoria,origem,situacao)
                values ('10000000-0000-0000-0000-000000000001','Local','local',-2.53,-44.30,'RESTAURANTE','USUARIO','PROVISORIO');
                insert into meals(id, autor_id, restaurante_id, comeria_novamente, data_refeicao, publicada_em)
                values ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000001',false,'2026-10-20',now());
                insert into dishes(id, meal_id, ordem, nome, nota) values
                ('40000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',0,'Primeiro',4.0),
                ('40000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001',1,'Segundo',5.0);
                insert into fotos(id, autor_id, chave_arquivo, chave_miniatura, hash_conteudo, largura, altura, tamanho_bytes,
                                  tamanho_miniatura_bytes, status, atual, criada_em, atualizada_em, removida_em)
                select ('50000000-0000-0000-0000-00000000000' || n)::uuid, '20000000-0000-0000-0000-000000000001',
                       'fotos/' || n || '/principal.jpg', 'fotos/' || n || '/miniatura.jpg', repeat('a', 64), 600, 800, 1000, 100,
                       case when n = 3 then 'REMOVIDA' else 'APROVADA' end, n <> 3, now(), now(), case when n = 3 then now() end
                from generate_series(1, 3) n;
                insert into fotos_dish(id, dish_id_historico, dish_id, dish_ativo_id) values
                ('50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000002'),
                ('50000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001'),
                ('50000000-0000-0000-0000-000000000003','40000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001',null);
                insert into publicacoes_idempotentes(id, autor_id, resultado_id, chave, hash_requisicao, resposta_json, criada_em)
                values ('60000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',
                        'chave', repeat('b', 64), '{"id":"30000000-0000-0000-0000-000000000001","dishes":[{"nome":"Primeiro","foto":{"id":"x"}},{"nome":"Segundo","foto":null}]}', now());
                """);
            // Alvo fixo na V18: o teste cobre a passagem V17 → V18 e não deve quebrar a cada migração nova.
            var flyway = Flyway.configure().dataSource(url, usuario, senha).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("18").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            try (var r = sql.executeQuery("select id, meal_id, ordem from fotos_meal order by id")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString("meal_id")).isEqualTo("30000000-0000-0000-0000-000000000001");
                assertThat(r.getInt("ordem")).isEqualTo(2); // a foto do 2º dish fica em 2º
                assertThat(r.next()).isTrue();
                assertThat(r.getInt("ordem")).isEqualTo(1);
                assertThat(r.next()).isTrue();
                assertThat(r.getString("meal_id")).isNull(); // removida: fica sem post
                assertThat(r.next()).isFalse();
            }
            try (var r = sql.executeQuery("select to_regclass('" + schema + ".fotos_dish')")) {
                r.next(); assertThat(r.getString(1)).isNull();
            }
            try (var r = sql.executeQuery("select resposta_json::jsonb from publicacoes_idempotentes")) {
                r.next();
                assertThat(r.getString(1)).doesNotContain("\"foto\"").contains("\"fotos\": []", "\"Primeiro\"", "\"Segundo\"");
            }
        }
    }
}
