-- T02: contagem diária de buscas ampliadas (Google) por pessoa, além do intervalo de 1 segundo.
ALTER TABLE limites_busca_restaurantes ADD COLUMN dia date;
ALTER TABLE limites_busca_restaurantes ADD COLUMN buscas_dia integer NOT NULL DEFAULT 0;
ALTER TABLE limites_busca_restaurantes ADD CONSTRAINT ck_limite_busca_dia CHECK (buscas_dia >= 0);
