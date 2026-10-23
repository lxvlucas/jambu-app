# Carga real Overture em São Luís — 23/10/2026

O PostgreSQL local recebeu **1.033 registros de alimentação** da release
`2026-10-21.0`: **966 ATIVO** e **67 PROVISORIO** por possível duplicidade.
Provisórios continuam selecionáveis em Meals e aguardam revisão dos founders.
Isso não afirma que o catálogo tenha cobertura completa ou 1.033 estabelecimentos
distintos: registros com GERS diferentes não são fundidos automaticamente.

## Obtenção e recorte

Instalação isolada autorizada pelo usuário: cliente oficial `overturemaps==1.0.2`
(MIT), Python 3.14.7, em `.local/overture-venv`. Distribuições binárias vieram do
PyPI oficial; versões, licenças e SHA-256 foram registrados antes da instalação.
Instalação offline dos wheels com hashes obrigatórios; `pip check` passou.
Não houve alteração de dependências Maven ou instalação global de pacotes.
O pip avisou que não removeu alguns diretórios temporários de instalação do sandbox;
isso não afetou a instalação nem a verificação de dependências.

O catálogo STAC oficial informou a release `2026-10-21.0`. Extração:

```powershell
./.local/overture-venv/Scripts/overturemaps.exe download --bbox=-44.41,-2.7992,-44.1618,-2.4739 -f geojson --type=place --release=2026-10-21.0 --stac --connect_timeout=15 --request_timeout=60 -o .local/overture/sao-luis-bbox-2026-10-21.0.geojson
```

Dos 25.343 pontos no retângulo, 1.743 ficaram fora da malha municipal simplificada
do IBGE, 20.765 fora das categorias alimentares e 1.802 abaixo da confiança 0,7.
Restaram 1.033 features, sem registros inválidos no recorte selecionado.
O filtro manteve as features originais, sem inventar nomes, coordenadas ou IDs.
GeoJSON filtrado: 1.290.612 bytes, SHA-256
`FC06F99D30BC7C65B5F399A853343BAA771935FD3940A6F3348710C11D659726`.

Fontes presentes: Meta, Microsoft, Overture, Foursquare e AllThePlaces.
Os [textos de licença e NOTICE](licencas/overture/README.md) acompanham a
documentação. Cada registro preserva GERS, versão, fontes e atribuição.
O export e os snapshots de origem ficam em `.local/overture/`, fora do Git.

## Compatibilidade e testes

O export real do cliente 1.0.2 guarda GERS em `Feature.id` e omite
`properties.type`, que é coluna de partição do conjunto original. O importador
anterior descartava essas features como `IGNORADO_TIPO`. Um teste com dados
fictícios nesse formato reproduziu a falha antes da correção.

Agora a ausência do campo é aceita no endpoint específico de Places. Um tipo
explicitamente diferente continua sendo ignorado; permanecem as verificações
de geometria Point, categoria, município, nome, versão e fontes.

- `passou`: `scripts/verificar.ps1 -Offline`, concluído em 22/10/2026 às 20:01.
- `passou`: **88 testes, zero falhas, erros ou ignorados**, build e smoke do JAR.
- `passou`: compatibilidade do export oficial, repetição e recusa de outro tipo explícito.
- `passou`: ensaio de carga no backup restaurado em PostgreSQL isolado.
- `passou`: carga pelo endpoint administrativo e repetição dos 1.033 registros
  como `INALTERADO`, mantendo os mesmos UUIDs.
- `passou`: fingerprints dos dados anteriores preservados, incluindo Meals,
  Dishes, fotos, fila/auditoria, restaurante preexistente, idempotência e consumo Google.
- `passou`: busca local após reiniciar o JAR, sem chamada Google.
- `passou`: remoção da permissão temporária, verificada por HTTP 403 administrativo.
- `passou`: backups anterior e posterior restaurados e comparados.

JDK 25.0.4 compilando para Java 21; Maven 3.9.16; PostgreSQL 17.
O aviso Mockito/Byte Buddy persiste. Runtime Java 21 específico e revisão
independente não executados. Log do gate: `.local/overture/gate-export-oficial.log`.

## Banco e administração

A conta demonstrativa foi habilitada como revisor somente nos processos locais
temporários usados na operação, após autorização para prosseguir. A configuração
permanente não foi alterada; o hash de `.env.local` permaneceu igual e as sessões
criadas foram revogadas. Nenhuma conta nova ou Meal de teste foi criada no banco
persistente. Google e processamento externo de fotos permaneceram desligados.

O Docker estava desligado na retomada de 23/10 e foi iniciado. O backup anterior
foi restaurado novamente no container de ensaio, cujo armazenamento temporário
havia sido limpo no desligamento; as verificações precederam a escrita persistente.
O container de ensaio foi removido após a restauração final; os processos Java
temporários foram encerrados. `jambu-docker-db-1` permanece disponível em localhost:5432.
Histórico Flyway V1–V4 preservado, sem nova migração ou repair.

Backups em `.local/backups/`, ambos restaurados e conferidos:

- `antes-overture-2026-10-22-01abab22e392.dump`, SHA-256
  `67A03F74550EC742E7D72391722E4671654E9B8304B656CB160CC65DCB75B930`.
- `apos-overture-2026-10-23-e3970efe10c8.dump`, SHA-256
  `B75113A901D5233830C29E8AAC936425859357B22DC29069C8C5BF8660AA5EAC`.

Registro operacional: `.local/overture/carga-real.json`. Manifestos de instalação
e extração, lock com hashes e logs ficam no mesmo diretório ignorado.
Faltam definir/configurar UUIDs permanentes dos founders, revisar os 67 provisórios
e avaliar cobertura com uso real. Interfaces e atualização agendada continuam
nas etapas posteriores. A carga não consumiu chamadas Google.
