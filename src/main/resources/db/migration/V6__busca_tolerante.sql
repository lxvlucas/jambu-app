-- T06: busca local tolerante a erro de digitação (trigramas) e pelo nome de pratos avaliados.
-- pg_trgm faz parte do PostgreSQL oficial e está disponível no Supabase.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_restaurantes_trgm ON restaurantes USING gin (nome_normalizado gin_trgm_ops)
    WHERE situacao <> 'REJEITADO';
