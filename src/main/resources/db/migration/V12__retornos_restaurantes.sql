-- T08: "Nome e bairro estão certos?" (RN-57). Uma resposta por pessoa por lugar: CONFIRMACAO verifica o
-- lugar provisório; CORRECAO o mantém provisório e entra na fila dos founders até a revisão (RN-58).
CREATE TABLE retornos_restaurantes (
    id uuid PRIMARY KEY,
    restaurante_id uuid NOT NULL REFERENCES restaurantes(id),
    usuario_id uuid NOT NULL REFERENCES usuarios(id),
    tipo varchar(20) NOT NULL CHECK (tipo IN ('CONFIRMACAO', 'CORRECAO')),
    nome_sugerido varchar(150),
    observacao varchar(500),
    criado_em timestamptz NOT NULL,
    resolvido_em timestamptz,
    CONSTRAINT uk_retorno_pessoa UNIQUE (restaurante_id, usuario_id),
    CONSTRAINT ck_retorno_conteudo CHECK (
        (tipo = 'CONFIRMACAO' AND nome_sugerido IS NULL AND observacao IS NULL)
        OR (tipo = 'CORRECAO' AND (nome_sugerido IS NOT NULL OR observacao IS NOT NULL)))
);
CREATE INDEX idx_retornos_pendentes ON retornos_restaurantes(criado_em, id)
    WHERE tipo = 'CORRECAO' AND resolvido_em IS NULL;
