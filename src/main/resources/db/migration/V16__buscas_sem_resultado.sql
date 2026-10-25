-- T10 (RN-61): buscas que não acharam nada no catálogo local, para completar o catálogo.
-- Sem dado pessoal: só o termo normalizado, o dia (fuso de São Luís) e o total. Nada liga a busca a uma pessoa.
CREATE TABLE buscas_sem_resultado (
    termo varchar(100) NOT NULL,
    dia date NOT NULL,
    buscas integer NOT NULL,
    PRIMARY KEY (termo, dia),
    CONSTRAINT ck_busca_sem_resultado_termo CHECK (char_length(termo) BETWEEN 2 AND 100),
    CONSTRAINT ck_busca_sem_resultado_total CHECK (buscas >= 1)
);
