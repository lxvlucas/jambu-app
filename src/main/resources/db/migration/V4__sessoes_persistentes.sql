ALTER TABLE sessoes ADD COLUMN absoluta_em timestamptz;
ALTER TABLE sessoes ADD COLUMN ultima_atividade_em timestamptz;
ALTER TABLE sessoes ADD COLUMN autenticada_em timestamptz;
ALTER TABLE sessoes ADD COLUMN canal varchar(10) NOT NULL DEFAULT 'LEGADO';
ALTER TABLE sessoes ADD COLUMN descricao varchar(120) NOT NULL DEFAULT 'Sessão anterior';
UPDATE sessoes SET absoluta_em = expira_em, ultima_atividade_em = criada_em, autenticada_em = criada_em;
ALTER TABLE sessoes ALTER COLUMN absoluta_em SET NOT NULL;
ALTER TABLE sessoes ALTER COLUMN ultima_atividade_em SET NOT NULL;
ALTER TABLE sessoes ALTER COLUMN autenticada_em SET NOT NULL;
ALTER TABLE sessoes ADD CONSTRAINT ck_sessao_limite CHECK (expira_em <= absoluta_em);
ALTER TABLE sessoes ADD CONSTRAINT ck_sessao_canal CHECK (canal IN ('LEGADO', 'NATIVO', 'WEB'));
CREATE TABLE tokens_acesso (
    id uuid PRIMARY KEY, sessao_id uuid NOT NULL REFERENCES sessoes(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE, expira_em timestamptz NOT NULL
);
-- O token anterior conserva seu prazo original; não recebe refresh automaticamente.
INSERT INTO tokens_acesso(id, sessao_id, token_hash, expira_em)
SELECT gen_random_uuid(), id, token_hash, expira_em FROM sessoes;
ALTER TABLE sessoes DROP COLUMN token_hash;
CREATE INDEX idx_acesso_sessao ON tokens_acesso(sessao_id);
CREATE INDEX idx_acesso_expiracao ON tokens_acesso(expira_em);
CREATE TABLE tokens_renovacao (
    id uuid PRIMARY KEY, sessao_id uuid NOT NULL REFERENCES sessoes(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE, criada_em timestamptz NOT NULL, expira_em timestamptz NOT NULL,
    consumido_em timestamptz, operacao_id uuid, sucessor_id uuid REFERENCES tokens_renovacao(id),
    resposta_cifrada text, repeticao_ate timestamptz,
    CONSTRAINT ck_refresh_consumo CHECK (
        (consumido_em IS NULL AND operacao_id IS NULL AND sucessor_id IS NULL)
        OR (consumido_em IS NOT NULL AND operacao_id IS NOT NULL AND sucessor_id IS NOT NULL)),
    CONSTRAINT ck_refresh_resposta CHECK ((resposta_cifrada IS NULL) = (repeticao_ate IS NULL)),
    CONSTRAINT ck_refresh_expiracao CHECK (expira_em > criada_em)
);
CREATE INDEX idx_refresh_sessao ON tokens_renovacao(sessao_id);
CREATE INDEX idx_refresh_repeticao ON tokens_renovacao(repeticao_ate) WHERE resposta_cifrada IS NOT NULL;
