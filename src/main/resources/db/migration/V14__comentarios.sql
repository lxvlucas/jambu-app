-- T21 (RN-34): comentários em posts. A chave de idempotência fica no próprio comentário (uma por
-- autor), com o hash do pedido, para o app reenviar sem duplicar. Excluir marca excluido_em.
CREATE TABLE comentarios (
    id uuid PRIMARY KEY,
    meal_id uuid NOT NULL REFERENCES meals(id),
    autor_id uuid NOT NULL REFERENCES usuarios(id),
    texto varchar(1000) NOT NULL CHECK (length(btrim(texto)) > 0),
    chave varchar(128) NOT NULL,
    hash_requisicao varchar(64) NOT NULL,
    criado_em timestamptz NOT NULL,
    excluido_em timestamptz,
    CONSTRAINT uk_comentario_chave UNIQUE (autor_id, chave)
);
CREATE INDEX idx_comentarios_meal ON comentarios(meal_id, criado_em, id) WHERE excluido_em IS NULL;
