-- T16 (P-04, RN-10): a foto pertence à Meal, com ordem (a 1ª é a capa). O app envia a foto antes de publicar:
-- ela fica "solta" (meal_id nulo), da pessoa, e é removida se não for usada em 24 h.
CREATE TABLE fotos_meal (
    id uuid PRIMARY KEY REFERENCES fotos(id),
    meal_id uuid REFERENCES meals(id),
    ordem integer,
    CONSTRAINT ck_foto_meal_ordem CHECK ((meal_id IS NULL) = (ordem IS NULL) AND (ordem IS NULL OR ordem >= 1)),
    -- Adiado até o commit: reordenar as fotos de um post troca as posições dentro da mesma transação.
    CONSTRAINT uq_foto_meal_ordem UNIQUE (meal_id, ordem) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_fotos_meal_soltas ON fotos_meal(id) WHERE meal_id IS NULL;
