-- T15: "comeria de novo" por Dish (sim, não ou sem resposta) e preço pago opcional.
-- O campo da Meal continua para dados antigos, mas deixa de ser obrigatório.
ALTER TABLE dishes ADD COLUMN comeria_novamente boolean;
ALTER TABLE dishes ADD COLUMN preco numeric(8,2);
ALTER TABLE dishes ADD CONSTRAINT ck_dish_preco CHECK (preco IS NULL OR preco >= 0);
ALTER TABLE meals ALTER COLUMN comeria_novamente DROP NOT NULL;
