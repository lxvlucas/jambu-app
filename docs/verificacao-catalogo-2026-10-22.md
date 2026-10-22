# Verificação do catálogo — 22/10/2026

Código implementado, gate aprovado e V4 aplicada no PostgreSQL persistente local.
README, contrato e checklists atualizados; carga real Overture ainda pendente.

- `passou`: `scripts/verificar.ps1 -Offline`, exit code 0, PostgreSQL 17 descartável.
- `passou`: 86 testes, zero falhas/erros/ignorados, clean verify e JAR.
- `passou`: verificação das variáveis locais.
- `passou`: smoke HTTP com cadastro próprio, busca local/Ver mais com Google
  desativado, publicação, reinício, idempotência, logout e carga demo repetida.
- `passou`: testes de migração V4 em schema isolado, sem alterar V1–V3.
- `passou`: V4 aplicada pelo Flyway; esquema validado e saúde UP após reinício do JAR.
- `passou`: backups anterior/posterior restaurados em PostgreSQL isolado.
- `passou`: fingerprints ordenados dos registros anteriores preservados em
  usuários, Meals, Dishes, seguimentos, idempotência, fotos, fila/auditoria de
  moderação e campos preexistentes dos restaurantes.
- `nao-executado`: carga real de São Luís do Overture; somente fixtures isoladas.
- `nao-executado`: integração Google ao vivo nesta rodada; não necessária para
  o gate e não houve chamada paga.
- `nao-executado`: revisão independente e runtime Java 21 específico.
- `passou`: autorrevisão do diff e git diff --check.
- `passou`: empacotamento final após remover linhas vazias extras; Flyway validou
  as quatro migrações no banco persistente, sem mudança de checksum/histórico,
  e o JAR respondeu saúde UP. Processo temporário encerrado.

Maven concluiu em 22/10/2026 às 15:25:15, America/Sao_Paulo; smoke aprovado depois.
JDK 25.0.4, release 21, Maven 3.9.16. Aviso de instrumentação Mockito/Byte Buddy
persiste. Log local ignorado: `.local/catalogo-gate.log`; relatórios em
`target/surefire-reports`. Uma primeira rodada encontrou username inválido na
fixture nova; correção verificada pelo gate completo posterior.

Empacotamento final: `mvn.cmd -o -DskipTests package`, em 22/10/2026 às 19:26,
com o Maven 3.9.16 já instalado. Os testes completos acima não foram repetidos
para ajustes de espaços/documentação. Uma tentativa pelo wrapper não iniciou o
Maven por falta de permissão para preparar sua distribuição; nenhuma instalação
foi realizada. Log da validação final: `.local/catalogo-final-validacao.log`.

O Docker Desktop foi iniciado e o banco `jambu-docker-db-1` ficou disponível
na porta local 5432, com volume `jambu-docker_postgres_data` preservado.
Histórico Flyway: 1,2,3,4. APIs temporárias e container de restauração encerrados.
Nenhuma fixture foi adicionada ao banco persistente. Google e processamento externo
de fotos foram desligados durante a verificação.

Backups em `.local/backups/` (não versionados), ambos restaurados e comparados:

- `antes-v4-2026-10-22-3dbb1fad07cc.dump`, SHA-256
  `DEE5B8005D5E4780E7C4E7BDBF9BC5ACE26D386F9C352C742FEF97AAF5E6C22F`.
- `apos-v4-2026-10-22-3dbb1fad07cc.dump`, SHA-256
  `D462157DB9AD03074A3BB023CF065671B9E9B1934F21FA8C0383C2F8670156D8`.

Registro operacional local: `.local/backups/registro-v4.json`.

Contrato novo: [catálogo de restaurantes](catalogo-restaurantes.md).
Sem nova dependência Maven, instalação, push ou deploy. Commit local autorizado
por Lucas ao final da implementação.
