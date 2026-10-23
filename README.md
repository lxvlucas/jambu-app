# Jambu API

Autenticação: acesso de 15 minutos e renovação por dispositivo, com 30 dias sem
renovação e limite original de 180 dias. Execute `./scripts/configurar-autenticacao.ps1`
para preparar a chave local antes de iniciar a API. Integração Android/web, cookies,
CSRF, migração e testes: [sessões persistentes](docs/sessoes-persistentes.md).

Fundação do protótipo interno, implementada a partir dos diagramas de classes da raiz
de `docs/modelagem` e das tarefas 1–11 do checklist do MVP. Java 21, Spring Boot 4.1.1,
Maven, Spring Data JPA, Flyway, Spring Security e PostgreSQL 17.

Regras atuais: publicações sem teto diário, contas sempre públicas para usuários
autenticados, catálogo local de restaurantes em São Luís com Overture, cadastro próprio e ampliação pelo Google Places
e fotos 3:4 com armazenamento privado, fila persistente de moderação automática e revisão administrativa.
A suíte automatizada usa HTTP simulado; verificações ao vivo de desenvolvimento são separadas
e dependem de credenciais do backend. Consulte as evidências datadas em `docs/`.

Em 20/10/2026, V1/V2/V3 foram aplicadas ao PostgreSQL local, a carga demonstrativa
foi conferida após reinício, Places foi validado ao vivo com a chave de demonstração e
os módulos de fotos e moderação (tarefas 10 e 11) foram implementados e verificados.
Veja [resultados e comandos de retomada](docs/persistencia-local-2026-10-20.md).

Em 22/10/2026, o catálogo local passou a aceitar cadastros provisórios, revisão por UUID
de conta, importações Overture e prevenção de duplicatas. A ampliação Google tem teto
de 100 chamadas/dia persistido no banco. Veja o [contrato do catálogo](docs/catalogo-restaurantes.md)
e as [evidências da implementação](docs/verificacao-catalogo-2026-10-22.md).

Em 23/10/2026, foram carregados 1.033 registros reais do Overture de São Luís,
com repetição idempotente e backups restaurados: 966 ativos e 67 provisórios
para revisão de possíveis duplicatas. O importador foi compatibilizado com o
GeoJSON do cliente oficial; o gate passou com 88 testes. Consulte a
[carga e suas verificações](docs/carga-overture-2026-10-23.md) e os
[avisos de atribuição e licenças](docs/licencas/overture/README.md).

## Executar localmente

Pré-requisitos: JDK 21 ou compatível, Maven, Docker Desktop ativo e o PostgreSQL do
Compose existente em `../../jambu-docker` preparado. Configure `JAVA_HOME` para um
JDK válido se o Java do PATH não estiver funcionando. Os comandos abaixo partem
da pasta que contém este README, em PowerShell 7.

```powershell
docker compose --project-directory ../../jambu-docker up -d
./scripts/iniciar-dev.ps1
```

O script lê as variáveis permitidas de `.env.local` na raiz da API e usa
`POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` do `.env` do Compose para
completar a conexão. Variáveis não vazias já definidas no processo têm prioridade.
A leitura é literal, sem executar/interpolar o conteúdo nem imprimir credenciais.
Para outros arquivos, use `-ArquivoApiEnv` e `-ArquivoComposeEnv`; `-Porta` muda a porta.
O ambiente anterior é restaurado ao encerrar o script.

Com o JAR já gerado, `./scripts/iniciar-dev.ps1 -Jar` inicia sem Maven. `.env.local`
é ignorado pelo Git e exclusivo deste comando de desenvolvimento; não é carregado
automaticamente pelo Spring nem pelos scripts de teste. Para desligar o Google,
defina `JAMBU_GOOGLE_PLACES_HABILITADO=false` no processo.

O seed manual antigo não roda mais, inclusive no perfil `dev`. Cadastre usuários
pela API e selecione no catálogo local ou use o cadastro próprio. Não existe usuário/senha padrão.
As migrações rodam automaticamente ao iniciar a aplicação; Hibernate apenas valida
o esquema. **Antes de iniciar em outro banco existente**, siga o
[plano da V2 e dos legados](docs/places-posts-publicos.md). Não use fixtures SQL dos
testes no banco persistente.

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

## Testes e build

```powershell
# Gate: banco vazio, atualização de V1, HTTP simulado, build limpo e reinício do JAR.
./scripts/verificar.ps1 -Offline

# Somente domínio e limitador de autenticação, sem PostgreSQL.
mvn -o '-Dtest=DominioTest,LimiteAutenticacaoTest' test

# Build e testes usando um banco de teste já configurado nas variáveis JAMBU_TEST_DB_*.
mvn -o clean verify
```

`-Offline`/`-o` usam as dependências já presentes. A primeira resolução em outro
computador exige Maven online. O gate usa a imagem local `postgres:17-bookworm`,
uma porta aleatória em loopback e armazenamento temporário; remove somente o
container criado por ele. Não toca no volume do banco de desenvolvimento.
O adaptador HTTP usa servidor em `127.0.0.1` nos testes; o smoke do JAR desativa
Places e utiliza referência simulada exclusivamente no banco temporário. O gate
também verifica a leitura segura do ambiente local e executa duas vezes a carga
demonstrativa para comprovar que as mesmas três Meals são retornadas.

O JAR fica em `target/jambu-api-0.0.1-SNAPSHOT.jar`. A aplicação empacotada também
pode ser iniciada com `java -jar target/jambu-api-0.0.1-SNAPSHOT.jar`, com as variáveis
de banco configuradas. Relatórios JUnit ficam em `target/surefire-reports`.

## Organização

```text
com.jambuapp.api
├── autenticacao   Cadastro, login, sessão e revogação
├── usuario        Dados privados da própria conta, perfil público e busca
├── seguimento     Relações e listas de seguidores/seguidos
├── restaurante    Seleção Places, referência local, perfil e médias Jambu
│   └── places     Interface do provedor, adaptador HTTP e DTOs transitórios
├── meal           Agregado Meal/Dish, publicação, idempotência e consultas
├── foto           Upload 3:4, normalização JPEG/sRGB, storage privado, fila e moderação
├── seguranca      Principal, filtro Bearer e limite de autenticação
└── comum          Configuração, erros, hash e paginação
```

Controllers recebem DTOs e identidade autenticada. Services controlam autorização,
regras e transações. Repositories fazem persistência e consultas; entidades nunca
são respostas HTTP. Os arquivos de cada funcionalidade ficam juntos, sem camadas
globais nem interfaces de service sem necessidade. `Dish` é persistido pelo agregado
`Meal`, sem repository independente.

## Configuração

| Variável | Padrão / finalidade |
| --- | --- |
| `JAMBU_DB_URL` | `jdbc:postgresql://localhost:5432/jambu` |
| `JAMBU_DB_USER`, `JAMBU_DB_PASSWORD` | Obrigatórias; sem credenciais no código |
| `JAMBU_BIND_ADDRESS` | `127.0.0.1` |
| `JAMBU_LIMITE_DISHES` | 20; máximo técnico configurável de 100 |
| `JAMBU_LIMITE_REVIEW` | 2.000; máximo técnico configurável de 10.000 |
| `JAMBU_URL_COMPARTILHAMENTO` | `http://localhost:8080/api/meals/` |
| `JAMBU_TEST_DB_URL`, `JAMBU_TEST_DB_USER`, `JAMBU_TEST_DB_PASSWORD` | Banco isolado de testes; obrigatórias para `mvn verify` direto |
| `JAMBU_GOOGLE_PLACES_HABILITADO` | `false`; ativação explícita |
| `JAMBU_GOOGLE_PLACES_API_KEY` | Vazia; somente no servidor, necessária ao habilitar |
| `JAMBU_GOOGLE_PLACES_LIMITE_DIA` | 100 chamadas/dia, contando sugestões e detalhes; 0 bloqueia chamadas |
| `JAMBU_REVISORES_RESTAURANTES` | UUIDs das contas revisoras, separados por vírgula; vazio não concede acesso |
| `JAMBU_GOOGLE_PLACES_TIMEOUT` | `PT3S`, até `PT30S` |
| `JAMBU_GOOGLE_PLACES_BASE_URL` | `https://places.googleapis.com/v1/`; override apenas para simulador HTTP em `127.0.0.1` |
| `JAMBU_FOTOS_DIRETORIO_LOCAL` | `.local/storage-fotos`; diretório privado local para binários normalizados |
| `JAMBU_FOTOS_STORAGE_TIPO` | `local` ou `supabase` |
| `JAMBU_FOTOS_SUPABASE_URL`, `JAMBU_FOTOS_SUPABASE_KEY`, `JAMBU_FOTOS_SUPABASE_BUCKET` | Configuração opcional do bucket privado no Supabase Storage |
| `JAMBU_FOTOS_MODERACAO_HABILITADA` | `false`; ativação explícita do provedor externo de moderação |
| `JAMBU_FOTOS_MODERACAO_PROVEDOR` | `rekognition` |
| `JAMBU_FOTOS_MODERACAO_BASE_URL`, `JAMBU_FOTOS_MODERACAO_API_KEY`, `JAMBU_FOTOS_MODERACAO_TIMEOUT` | Endpoint HTTPS AWS (ou simulador `127.0.0.1`), credencial e timeout (`PT5S` até `PT30S`) |
| `JAMBU_MODERADORES_USERNAMES` | Lista separada por vírgula de usernames autorizados nos endpoints `/api/moderacao/fotos` |

`jambu.auth.*` configura acesso de 15 minutos, inatividade de 30 dias e limite
absoluto de 180 dias. `jambu.limite-autenticacao-minuto` começa em 20 tentativas
por IP/minuto para login e cadastro.
O exemplo de variáveis está em [.env.example](.env.example). Configure o ambiente
do processo ou `.env.local` para `iniciar-dev.ps1`. O Spring não carrega dotenv
sozinho. Não existe mais
variável de limite diário de Meals. Com Places ausente/desativado, a busca retorna
estado externo e mantém sugestões locais/cadastro manual disponíveis.

## Carga demonstrativa local

Primeiro, selecione ou cadastre um restaurante e copie seu UUID local.
O script abaixo cria duas contas fictícias, um seguimento e três Meals com quatro
Dishes. Ele usa somente endpoints normais, não cadastra restaurantes manualmente,
não consulta Google e é restrito a `http://127.0.0.1`. O texto das Meals identifica
explicitamente a demonstração; esses dados não devem ser promovidos a produção.

```powershell
$senhaDemo = Read-Host 'Senha para as contas de demonstração (mínimo 12 caracteres)' -AsSecureString
./scripts/carregar-demonstracao.ps1 -RestauranteId '<UUID_LOCAL_SELECIONADO>' -Senha $senhaDemo
```

Contas padrão: `jambu_demo_ana` e `jambu_demo_bruno`; `-Prefixo` permite outra carga.
Repita com o mesmo prefixo, restaurante e senha: as chaves de idempotência mantêm
as mesmas Meals. O script não redefine senha, não sobrescreve edição/exclusão
anterior e revoga suas sessões no final. Interrupções podem deixar carga parcial;
repetir com os mesmos parâmetros conclui os itens restantes. Se uma Meal já tiver
sido excluída, use novo prefixo para outra demonstração, sem apagar o histórico.
Não há senha padrão no repositório.

Backups e credenciais locais ficam em `.local/`, também ignorado pelo Git.
Essa exclusão não substitui o controle de acesso do computador nem uma política
de backup remoto. O volume do PostgreSQL existente continua sendo o destino local.

## Contrato e limites

Consulte [checklist do MVP](docs/mvp-checklist.md), [padrão de fotos](docs/fotos-padrao-mvp.md),
[endpoints e exemplos](docs/contrato-api.md), [migração, Places e segurança](docs/places-posts-publicos.md)
e [verificação de 20/10](docs/persistencia-local-2026-10-20.md). Os registros da
[integração de 19/10](docs/verificacao-places.md), da
[fundação anterior](docs/fundacao.md) e de seus [testes](docs/verificacao-fundacao.md)
permanecem como histórico datado.

Consultas de negócio exigem Bearer. O fluxo permite cadastrar, entrar, seguir,
selecionar restaurante, publicar Meals, consultar timeline e editar/excluir.
Seguir é imediato. Consultar perfil ou Meal de outra conta não exige segui-la;
a timeline continua restrita a próprias e seguidas. Novas Meals usam restaurante
local selecionável, inclusive provisório, e snapshots com UUID/placeId, sem dados Google.
O link de compartilhamento aponta inicialmente para o detalhe JSON autenticado;
a interface web poderá substituir a URL base.

Recuperação de conta, denúncias, bloqueios, Android/web e hospedagem ainda não estão
implementados. Não há cobrança, anúncios ou Top 4. O protótipo precisa de revisão
operacional antes de dados reais ou usuários externos.
