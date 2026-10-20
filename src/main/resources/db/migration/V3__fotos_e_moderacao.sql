CREATE TABLE fotos (
    id uuid PRIMARY KEY,
    versao bigint NOT NULL DEFAULT 0,
    autor_id uuid NOT NULL REFERENCES usuarios(id),
    chave_arquivo varchar(255) NOT NULL UNIQUE,
    chave_miniatura varchar(255) NOT NULL UNIQUE,
    hash_conteudo varchar(64) NOT NULL,
    largura integer NOT NULL CHECK (largura >= 600 AND largura <= 1080 AND largura * 4 = altura * 3),
    altura integer NOT NULL CHECK (altura >= 800 AND altura <= 1440),
    tamanho_bytes integer NOT NULL CHECK (tamanho_bytes > 0 AND tamanho_bytes <= 1048576),
    tamanho_miniatura_bytes integer NOT NULL CHECK (tamanho_miniatura_bytes > 0 AND tamanho_miniatura_bytes <= 204800),
    status varchar(20) NOT NULL,
    atual boolean NOT NULL DEFAULT true,
    criada_em timestamptz NOT NULL,
    atualizada_em timestamptz NOT NULL,
    removida_em timestamptz,
    arquivos_limpos_em timestamptz,
    CONSTRAINT ck_foto_status CHECK (status IN ('PENDENTE', 'EM_ANALISE', 'APROVADA', 'REJEITADA', 'EM_REVISAO', 'REMOVIDA')),
    CONSTRAINT ck_foto_hash CHECK (hash_conteudo ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_foto_removida_nao_atual CHECK (status <> 'REMOVIDA' OR (atual = false AND removida_em IS NOT NULL))
);
CREATE INDEX idx_fotos_autor ON fotos(autor_id, criada_em DESC, id DESC);
CREATE INDEX idx_fotos_status ON fotos(status, criada_em ASC, id ASC);

CREATE TABLE fotos_perfil (
    id uuid PRIMARY KEY REFERENCES fotos(id),
    usuario_id uuid NOT NULL REFERENCES usuarios(id),
    usuario_ativo_id uuid UNIQUE REFERENCES usuarios(id),
    CONSTRAINT ck_foto_perfil_ativo_mesmo_usuario CHECK (usuario_ativo_id IS NULL OR usuario_ativo_id = usuario_id)
);
CREATE INDEX idx_fotos_perfil_usuario ON fotos_perfil(usuario_id, id);

CREATE TABLE fotos_dish (
    id uuid PRIMARY KEY REFERENCES fotos(id),
    dish_id_historico uuid NOT NULL,
    dish_id uuid REFERENCES dishes(id) ON DELETE SET NULL,
    dish_ativo_id uuid UNIQUE REFERENCES dishes(id) ON DELETE SET NULL,
    CONSTRAINT ck_foto_dish_ativo_mesmo_dish CHECK (dish_ativo_id IS NULL OR (dish_id IS NOT NULL AND dish_ativo_id = dish_id AND dish_id = dish_id_historico))
);
CREATE INDEX idx_fotos_dish_dish ON fotos_dish(dish_id, id);

CREATE TABLE tarefas_moderacao (
    id uuid PRIMARY KEY,
    versao bigint NOT NULL DEFAULT 0,
    foto_id uuid NOT NULL REFERENCES fotos(id),
    hash_conteudo varchar(64) NOT NULL,
    versao_politica varchar(50) NOT NULL,
    estado varchar(20) NOT NULL,
    tentativas integer NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    proxima_tentativa_em timestamptz NOT NULL,
    reserva_ate timestamptz,
    token_reserva uuid,
    criada_em timestamptz NOT NULL,
    concluida_em timestamptz,
    CONSTRAINT ck_tarefa_moderacao_estado CHECK (estado IN ('AGUARDANDO', 'EM_EXECUCAO', 'CONCLUIDA', 'CANCELADA')),
    CONSTRAINT ck_tarefa_moderacao_reserva CHECK (
        (estado = 'EM_EXECUCAO' AND reserva_ate IS NOT NULL AND token_reserva IS NOT NULL)
        OR (estado <> 'EM_EXECUCAO' AND reserva_ate IS NULL AND token_reserva IS NULL)
    )
);
CREATE UNIQUE INDEX uq_tarefa_moderacao_ativa ON tarefas_moderacao(foto_id) WHERE estado IN ('AGUARDANDO', 'EM_EXECUCAO');
CREATE INDEX idx_tarefas_moderacao_fila ON tarefas_moderacao(estado, proxima_tentativa_em ASC, criada_em ASC, id ASC);
CREATE INDEX idx_tarefas_moderacao_reserva ON tarefas_moderacao(estado, reserva_ate ASC) WHERE estado = 'EM_EXECUCAO';

CREATE TABLE registros_moderacao (
    id uuid PRIMARY KEY,
    foto_id uuid NOT NULL REFERENCES fotos(id),
    origem varchar(20) NOT NULL,
    resultado varchar(20) NOT NULL,
    motivo varchar(500) NOT NULL,
    versao_politica varchar(50) NOT NULL,
    provedor varchar(100),
    versao_modelo varchar(100),
    sinais_json text,
    revisor_id uuid REFERENCES usuarios(id),
    criado_em timestamptz NOT NULL,
    CONSTRAINT ck_registro_moderacao_origem CHECK (origem IN ('AUTOMATICA', 'HUMANA')),
    CONSTRAINT ck_registro_moderacao_resultado CHECK (resultado IN ('APROVAR', 'REJEITAR', 'REVISAR', 'FALHA_TECNICA')),
    CONSTRAINT ck_registro_moderacao_motivo CHECK (length(btrim(motivo)) > 0),
    CONSTRAINT ck_registro_moderacao_revisor CHECK (
        (origem = 'HUMANA' AND revisor_id IS NOT NULL AND resultado IN ('APROVAR', 'REJEITAR', 'REVISAR'))
        OR (origem = 'AUTOMATICA' AND revisor_id IS NULL)
    )
);
CREATE INDEX idx_registros_moderacao_foto ON registros_moderacao(foto_id, criado_em ASC, id ASC);
