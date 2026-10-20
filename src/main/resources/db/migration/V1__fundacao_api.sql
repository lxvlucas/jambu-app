CREATE TABLE usuarios (
    id uuid PRIMARY KEY,
    username varchar(30) NOT NULL UNIQUE,
    email varchar(254) NOT NULL UNIQUE,
    celular varchar(20) NOT NULL,
    senha_hash varchar(255) NOT NULL,
    criado_em timestamptz NOT NULL,
    CONSTRAINT ck_usuario_username CHECK (username = lower(btrim(username)) AND username ~ '^[a-z0-9_]{3,30}$'),
    CONSTRAINT ck_usuario_email CHECK (email = lower(btrim(email)) AND length(email) > 3)
);

CREATE TABLE sessoes (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuarios(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    criada_em timestamptz NOT NULL,
    expira_em timestamptz NOT NULL,
    revogada_em timestamptz,
    CONSTRAINT ck_sessao_expiracao CHECK (expira_em > criada_em)
);
CREATE INDEX idx_sessoes_usuario ON sessoes(usuario_id);
CREATE INDEX idx_sessoes_expiracao ON sessoes(expira_em);

CREATE TABLE seguimentos (
    id uuid PRIMARY KEY,
    seguidor_id uuid NOT NULL REFERENCES usuarios(id),
    seguido_id uuid NOT NULL REFERENCES usuarios(id),
    criado_em timestamptz NOT NULL,
    UNIQUE(seguidor_id, seguido_id),
    CONSTRAINT ck_seguimento_distinto CHECK (seguidor_id <> seguido_id)
);
CREATE INDEX idx_seguimentos_seguido ON seguimentos(seguido_id, criado_em, id);
CREATE INDEX idx_seguimentos_seguidor ON seguimentos(seguidor_id, criado_em, id);

CREATE TABLE restaurantes (
    id uuid PRIMARY KEY,
    nome varchar(150) NOT NULL,
    endereco varchar(300) NOT NULL,
    google_place_id varchar(255) UNIQUE,
    CONSTRAINT ck_restaurante_nome CHECK (length(btrim(nome)) > 0)
);
CREATE INDEX idx_restaurantes_nome ON restaurantes(nome, id);

CREATE TABLE meals (
    id uuid PRIMARY KEY,
    versao bigint NOT NULL DEFAULT 0,
    autor_id uuid NOT NULL REFERENCES usuarios(id),
    restaurante_id uuid NOT NULL REFERENCES restaurantes(id),
    review varchar(10000),
    comeria_novamente boolean NOT NULL,
    modalidade varchar(20),
    data_refeicao date NOT NULL,
    publicada_em timestamptz NOT NULL,
    editada_em timestamptz,
    excluida_em timestamptz,
    CONSTRAINT ck_meal_modalidade CHECK (modalidade IN ('PRESENCIAL', 'DELIVERY', 'RETIRADA'))
);
CREATE INDEX idx_meals_timeline ON meals(publicada_em DESC, id DESC) WHERE excluida_em IS NULL;
CREATE INDEX idx_meals_autor ON meals(autor_id, publicada_em DESC, id DESC) WHERE excluida_em IS NULL;
CREATE INDEX idx_meals_restaurante ON meals(restaurante_id, publicada_em DESC, id DESC) WHERE excluida_em IS NULL;

CREATE TABLE dishes (
    id uuid PRIMARY KEY,
    meal_id uuid NOT NULL REFERENCES meals(id),
    ordem integer NOT NULL CHECK (ordem >= 0),
    nome varchar(150) NOT NULL,
    nota numeric(2,1) NOT NULL,
    CONSTRAINT ck_dish_nome CHECK (length(btrim(nome)) > 0),
    CONSTRAINT ck_dish_nota CHECK (nota >= 0.5 AND nota <= 5 AND mod(nota, 0.5) = 0)
);
CREATE INDEX idx_dishes_meal ON dishes(meal_id);

CREATE TABLE publicacoes_diarias (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuarios(id),
    dia date NOT NULL,
    quantidade integer NOT NULL CHECK (quantidade >= 0),
    UNIQUE(usuario_id, dia)
);

CREATE TABLE publicacoes_idempotentes (
    id uuid PRIMARY KEY,
    autor_id uuid NOT NULL REFERENCES usuarios(id),
    resultado_id uuid NOT NULL REFERENCES meals(id),
    chave varchar(128) NOT NULL,
    hash_requisicao varchar(64) NOT NULL,
    resposta_json text NOT NULL,
    criada_em timestamptz NOT NULL,
    UNIQUE(autor_id, chave)
);
CREATE INDEX idx_idempotencias_resultado ON publicacoes_idempotentes(resultado_id);
