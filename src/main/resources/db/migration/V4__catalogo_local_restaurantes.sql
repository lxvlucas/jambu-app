-- A evolução preserva UUIDs, Meals, fotos e snapshots. V1–V3 não são reescritas.
DROP TRIGGER restaurante_referencia_validada ON restaurantes;
DROP FUNCTION validar_nova_referencia_restaurante();

ALTER TABLE restaurantes
    ADD COLUMN origem varchar(20) NOT NULL DEFAULT 'LEGADO',
    ADD COLUMN situacao varchar(20) NOT NULL DEFAULT 'ATIVO',
    ADD COLUMN latitude double precision,
    ADD COLUMN longitude double precision,
    ADD COLUMN categoria varchar(40),
    ADD COLUMN overture_id varchar(128) UNIQUE,
    ADD COLUMN overture_release varchar(30),
    ADD COLUMN overture_versao integer,
    ADD COLUMN fontes_overture text,
    ADD COLUMN licencas_overture text,
    ADD COLUMN atribuicao_overture varchar(1000),
    ADD COLUMN corrigido_localmente boolean NOT NULL DEFAULT false,
    ADD COLUMN criado_por uuid REFERENCES usuarios(id),
    ADD COLUMN criado_em timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN nome_normalizado varchar(150),
    ADD COLUMN versao bigint NOT NULL DEFAULT 0;
UPDATE restaurantes SET origem = 'GOOGLE_PLACES' WHERE google_place_id IS NOT NULL AND places_validado_em IS NOT NULL;
UPDATE restaurantes SET nome_normalizado = trim(regexp_replace(
    translate(lower(nome), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'),
    '[^a-z0-9]+', ' ', 'g')) WHERE nome IS NOT NULL;
ALTER TABLE restaurantes ADD COLUMN busca tsvector GENERATED ALWAYS AS
    (to_tsvector('simple', coalesce(nome_normalizado, ''))) STORED;
ALTER TABLE restaurantes ADD CONSTRAINT ck_restaurante_catalogo CHECK (
    origem IN ('LEGADO','GOOGLE_PLACES','USUARIO','OVERTURE')
    AND situacao IN ('ATIVO','PROVISORIO','REJEITADO')
    AND ((latitude IS NULL AND longitude IS NULL) OR
         (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
    AND (origem NOT IN ('USUARIO','OVERTURE') OR
         (nome IS NOT NULL AND nome_normalizado IS NOT NULL AND latitude IS NOT NULL
          AND longitude IS NOT NULL AND categoria IS NOT NULL))
    AND (google_place_id IS NULL OR places_validado_em IS NOT NULL OR origem = 'LEGADO')
    AND (origem <> 'OVERTURE' OR
         (overture_id IS NOT NULL AND overture_release IS NOT NULL AND fontes_overture IS NOT NULL
          AND licencas_overture IS NOT NULL AND overture_versao IS NOT NULL))
);
CREATE INDEX idx_restaurantes_busca ON restaurantes USING gin(busca) WHERE situacao <> 'REJEITADO';
CREATE INDEX idx_restaurantes_prefixo ON restaurantes(nome_normalizado varchar_pattern_ops) WHERE situacao <> 'REJEITADO';
CREATE INDEX idx_restaurantes_pin ON restaurantes(latitude, longitude) WHERE situacao <> 'REJEITADO';
CREATE INDEX idx_restaurantes_revisao ON restaurantes(situacao, criado_em, id);

CREATE TABLE cadastros_restaurantes (
    autor_id uuid NOT NULL REFERENCES usuarios(id),
    chave varchar(128) NOT NULL,
    hash_requisicao varchar(64) NOT NULL,
    restaurante_id uuid NOT NULL REFERENCES restaurantes(id),
    criado_em timestamptz NOT NULL,
    PRIMARY KEY(autor_id, chave)
);
CREATE TABLE revisoes_restaurantes (
    id uuid PRIMARY KEY,
    restaurante_id uuid NOT NULL REFERENCES restaurantes(id),
    revisor_id uuid NOT NULL REFERENCES usuarios(id),
    situacao_anterior varchar(20) NOT NULL,
    situacao_nova varchar(20) NOT NULL,
    motivo varchar(1000) NOT NULL,
    criada_em timestamptz NOT NULL
);
CREATE TABLE consumo_places_diario (
    dia date PRIMARY KEY,
    chamadas integer NOT NULL CHECK (chamadas >= 0)
);
CREATE TABLE limites_busca_restaurantes (
    usuario_id uuid PRIMARY KEY REFERENCES usuarios(id),
    ultima_busca_em timestamptz NOT NULL
);
