-- Retirada da regra diária. Nenhuma linha de Meal, usuário, sessão ou idempotência é removida.
DROP TABLE publicacoes_diarias;

-- Preserva UUIDs, FKs e campos próprios legados. A vinculação ao Places é explícita.
ALTER TABLE restaurantes ALTER COLUMN nome DROP NOT NULL;
ALTER TABLE restaurantes ALTER COLUMN endereco DROP NOT NULL;
ALTER TABLE restaurantes ALTER COLUMN google_place_id TYPE varchar(1024);
ALTER TABLE restaurantes ADD COLUMN places_validado_em timestamptz;

-- NULL continua permitido no legado, mas não em uma nova referência.
CREATE FUNCTION validar_nova_referencia_restaurante() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.google_place_id IS NULL OR btrim(NEW.google_place_id) = '' OR NEW.places_validado_em IS NULL THEN
        RAISE EXCEPTION 'Novo restaurante requer referência Places validada' USING ERRCODE = '23514';
    END IF;
    IF NEW.nome IS NOT NULL OR NEW.endereco IS NOT NULL THEN
        RAISE EXCEPTION 'Dados externos de restaurante não devem ser persistidos' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER restaurante_referencia_validada
    BEFORE INSERT ON restaurantes FOR EACH ROW EXECUTE FUNCTION validar_nova_referencia_restaurante();

-- O esquema V1 não tem privacidade de conta ou pedidos de seguimento.
-- Não há conversão automática de relações, associação por nome ou reescrita de snapshots.
