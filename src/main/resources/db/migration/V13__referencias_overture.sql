-- T04 (RN-43): lugares do Overture com confiança entre 0,3 e 0,5 ficam aqui, fora do catálogo. A busca,
-- as duplicatas e o perfil só leem restaurantes; esta tabela serve para casar um lugar escolhido no
-- Google com o nome e o pin do Overture (T05). Uma tabela à parte evita que um novo valor de situação
-- vaze nas consultas que só filtram situacao <> 'REJEITADO'.
CREATE TABLE referencias_overture (
    overture_id varchar(128) PRIMARY KEY,
    nome varchar(150) NOT NULL,
    nome_normalizado varchar(150) NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    categoria varchar(40) NOT NULL
        CHECK (categoria IN ('RESTAURANTE', 'CAFE', 'PADARIA', 'BAR', 'LANCHONETE', 'SORVETERIA')),
    endereco varchar(300),
    confianca double precision NOT NULL CHECK (confianca >= 0.3 AND confianca < 0.5),
    overture_release varchar(30) NOT NULL,
    overture_versao integer NOT NULL CHECK (overture_versao >= 0),
    fontes_overture text NOT NULL,
    licencas_overture text NOT NULL,
    atribuicao_overture varchar(1000) NOT NULL,
    atualizado_em timestamptz NOT NULL
);
