-- T16 (decisão de Davi, 25/10/2026): a foto por dish sai. A foto ativa de cada dish vira foto do post, na ordem
-- dos dishes; a já removida fica sem post. A ligação foto ↔ dish volta pela marcação da T17.
INSERT INTO fotos_meal (id, meal_id, ordem)
SELECT fd.id,
       CASE WHEN fd.dish_ativo_id IS NOT NULL THEN d.meal_id END,
       CASE WHEN fd.dish_ativo_id IS NOT NULL
            THEN row_number() OVER (PARTITION BY d.meal_id, fd.dish_ativo_id IS NOT NULL ORDER BY d.ordem, fd.id) END
FROM fotos_dish fd LEFT JOIN dishes d ON d.id = fd.dish_id;

DROP TABLE fotos_dish;

-- Respostas guardadas para a idempotência: sem o "foto" de cada dish e com "fotos" (vazia), o formato novo.
UPDATE publicacoes_idempotentes
SET resposta_json = (jsonb_set(resposta_json::jsonb, '{dishes}',
        coalesce((SELECT jsonb_agg(d - 'foto' ORDER BY n)
                  FROM jsonb_array_elements(resposta_json::jsonb -> 'dishes') WITH ORDINALITY AS x(d, n)), '[]'::jsonb))
        || jsonb_build_object('fotos', coalesce(resposta_json::jsonb -> 'fotos', '[]'::jsonb)))::text
WHERE resposta_json::jsonb ? 'dishes';
