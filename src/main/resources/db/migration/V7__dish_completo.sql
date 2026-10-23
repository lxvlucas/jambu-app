-- T15: "comeria de novo" por Dish, como marcação: marcado (true) ou desmarcado (false, o padrão).
-- Desmarcado não quer dizer "não comeria" (decisão de Davi, 23/10/2026). Preço pago opcional.
-- O campo da Meal continua para dados antigos, com a mesma regra.
ALTER TABLE dishes ADD COLUMN comeria_novamente boolean NOT NULL DEFAULT false;
ALTER TABLE dishes ADD COLUMN preco numeric(8,2);
ALTER TABLE dishes ADD CONSTRAINT ck_dish_preco CHECK (preco IS NULL OR preco >= 0);
UPDATE meals SET comeria_novamente = false WHERE comeria_novamente IS NULL;
ALTER TABLE meals ALTER COLUMN comeria_novamente SET DEFAULT false;
