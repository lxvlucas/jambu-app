-- T20: uma curtida por pessoa por Meal; descurtir apaga a linha.
CREATE TABLE curtidas (
    meal_id uuid NOT NULL REFERENCES meals(id),
    usuario_id uuid NOT NULL REFERENCES usuarios(id),
    criada_em timestamptz NOT NULL,
    PRIMARY KEY (meal_id, usuario_id)
);
CREATE INDEX idx_curtidas_usuario ON curtidas(usuario_id);
