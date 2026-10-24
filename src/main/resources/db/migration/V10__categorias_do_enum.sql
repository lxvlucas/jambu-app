-- O banco aceita só as categorias do enum CategoriaRestaurante. A antiga PRACA_ALIMENTACAO vira
-- RESTAURANTE; um founder corrige na revisão se precisar (decisão de Davi e Lucas, 23/10/2026).
UPDATE restaurantes SET categoria = 'RESTAURANTE' WHERE categoria = 'PRACA_ALIMENTACAO';
ALTER TABLE restaurantes ADD CONSTRAINT ck_restaurante_categoria
    CHECK (categoria IS NULL OR categoria IN ('RESTAURANTE', 'CAFE', 'PADARIA', 'BAR', 'LANCHONETE', 'SORVETERIA'));
