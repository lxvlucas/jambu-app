-- "Comeria de novo" vira marcação: marcado (true) ou desmarcado (false). Desmarcado não quer dizer
-- "não comeria"; não existe mais o estado "sem resposta" (decisão de Davi, 23/10/2026).
UPDATE dishes SET comeria_novamente = false WHERE comeria_novamente IS NULL;
ALTER TABLE dishes ALTER COLUMN comeria_novamente SET DEFAULT false;
ALTER TABLE dishes ALTER COLUMN comeria_novamente SET NOT NULL;
UPDATE meals SET comeria_novamente = false WHERE comeria_novamente IS NULL;
ALTER TABLE meals ALTER COLUMN comeria_novamente SET DEFAULT false;
ALTER TABLE meals ALTER COLUMN comeria_novamente SET NOT NULL;
