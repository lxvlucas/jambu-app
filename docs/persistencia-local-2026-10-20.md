# Persistência local e preparação para fotos — 20/10/2026

Lucas autorizou concluir persistência/migrações, usar a chave demonstrativa de
Places e fechar dimensões/compactação das fotos antes do módulo de moderação.
Este registro atualiza as pendências de 19/10 sem alterar suas evidências históricas.

## Banco persistente

Destino conferido: PostgreSQL 17 do Compose existente, serviço `db`, container
`jambu-docker-db-1`, porta local 5432 e volume `jambu-docker_postgres_data`.
A inspeção anterior à aplicação confirmou novamente `public` vazio, sem histórico
Flyway. Não havia usuários, Meals ou vínculos legados para converter.

Foi criado backup custom do estado inicial e validada sua restauração em container
descartável. Depois, a aplicação executou **V1 e V2 com sucesso**; Hibernate validou
o esquema e o endpoint de saúde retornou UP. Os arquivos V1/V2 não foram editados.
V1 conserva SHA-256 `753AA067CB63CF5F941AB7AD3D881804460F66E0E56CB6C6E567EBFA158944D7`.

A tabela `publicacoes_diarias` não existe no esquema final. Não há restaurante sem
placeId/instante de validação, nome/endereço externo persistido ou nome externo no
objeto restaurante dos snapshots. UUIDs, FKs e unicidades permanecem no banco.

| Carga local atual | Quantidade |
| --- | ---: |
| Usuários demonstrativos | 2 |
| Restaurante selecionado no Places | 1 |
| Meals | 3 |
| Dishes | 4 |
| Seguimento | 1 |
| Registros de idempotência | 3 |

As contas são `jambu_demo_ana` e `jambu_demo_bruno`. Pratos/reviews são fictícios e
explicitamente identificados como demonstração; não representam avaliações reais
do restaurante. O script não envia essas avaliações ao Google. Não promover essa
carga a um ambiente de produção.

## Google Places ao vivo

Com a chave fornecida por Lucas, uma consulta direta de Autocomplete retornou
HTTP 200. Na API, foram verificados Autocomplete, seleção com Place Details e detalhe
independente: **três chamadas adicionais ao Google, todas com sucesso**. A seleção
propagou o token da busca e resultou em uma referência local; perfil retornou atribuição
Google Maps. Antes das Meals, média Jambu estava ausente e quantidade era zero.

Foram quatro chamadas reais nesta rodada, separadas da suíte automatizada. Não houve
criação de conta/projeto cloud, alteração de billing ou importação em massa. A chave
é de demonstração segundo Lucas; esta validação não comprova gratuidade, cotas ou
adequação da credencial à produção. Não foi necessária uma chave definitiva para
concluir a validação local.

A configuração está somente em `.env.local`, ignorado pelo Git, lida por
`scripts/iniciar-dev.ps1`. Valores não vazios do processo têm prioridade; para
desativar a integração, use `JAMBU_GOOGLE_PLACES_HABILITADO=false`. A chave não
integra código, documentação, argumentos do Java, exemplos ou logs de payload.
Não é carregada nos testes. Credenciais de outro ambiente exigem configuração própria.

Preparação exigida pelo provedor para outros ambientes:
[configuração oficial do Places API (New)](https://developers.google.com/maps/documentation/places/web-service/get-api-key).

## Carga reutilizável e recuperação local

`scripts/carregar-demonstracao.ps1` usa endpoints normais, recebe um UUID de restaurante
já selecionado e aceita somente `http://127.0.0.1`. Cria/reutiliza as duas contas,
o seguimento e as três Meals com chaves/payloads estáveis. Não redefine senhas,
não faz SQL direto, não inventa placeId nem chama Google. Em conflito de conta,
exige autenticação com a senha fornecida. Suas sessões são revogadas no final.

Executamos a carga duas vezes e, depois, uma terceira vez após reiniciar banco/API:
os IDs das três Meals permaneceram iguais. Com Google desativado no reinício,
o perfil local continuou retornando três avaliações e média **4,50**.

Para retomar, partindo da raiz da API:

```powershell
# Com JAVA_HOME válido e Docker ativo.
docker compose --project-directory ../../jambu-docker up -d
./scripts/iniciar-dev.ps1 -Jar
```

Em outro terminal, para repetir a mesma carga existente:

```powershell
$credencialDemo = Import-Clixml .local/demonstracao.credential.xml
$cargaDemo = Get-Content .local/demonstracao.json -Raw | ConvertFrom-Json
./scripts/carregar-demonstracao.ps1 -RestauranteId $cargaDemo.RestauranteId -Senha $credencialDemo.Password
```

A senha aleatória foi salva por `Export-Clixml` como PSCredential protegido pelo
Windows/DPAPI para este usuário e computador. Não há senha padrão no Git. O arquivo
não é portável para outro usuário/dispositivo; nesses casos, preparar outra carga
com senha fornecida por `Read-Host -AsSecureString` e prefixo novo.

Backups antes e depois da migração/carga estão em `.local/backups/`, junto dos
registros de SHA-256 e restauração. Ambos foram restaurados em PostgreSQL isolado;
o backup com dados teve esquema e contagens conferidos. Os containers temporários
foram removidos. São backups locais; retenção, cópia externa e restauração de
produção continuam na tarefa de hospedagem. Não restaurar automaticamente sobre
o banco em uso nem apagar seu volume.

Ao encerrar esta rodada, a API temporária foi parada; o PostgreSQL persistente
permaneceu disponível. O comando acima reinicia a API na porta 8080.

## Verificações executadas

| Verificação | Resultado |
| --- | --- |
| `scripts/verificar.ps1 -Offline` | `passou`, exit code 0 |
| JUnit/Maven | 68 testes, zero falhas/erros/ignorados; `clean verify` e JAR gerado |
| Ambiente local | `passou`: precedência, conteúdo literal, lista de variáveis permitidas e rejeição de formato inválido |
| Smoke do JAR | `passou`: saúde, autenticação, publicação, reinício, idempotência/logout e duas cargas iguais |
| PostgreSQL existente | `passou`: migrações V1/V2/V3, contagens, ausência de cota, tabelas de fotos/moderação e proteção de conteúdo externo |
| Backup/restauração | `passou`: antes e depois da carga e após V3 (`apos-v3-2026-10-20-5614774e6564.dump`), sempre em destino isolado |
| Reinício do banco e API | `passou`: mesmas Meals/chaves, leitura e médias sem Google |
| Places real | `passou` com a chave demonstrativa, limitado aos três fluxos descritos |
| Fotos e moderação (tarefas 10 e 11) | `passou`: 18 testes unitários + 5 testes integrados cobrindo upload 3:4, EXIF/GPS, storage privado, fila, retentativa, substituição, contestação e revisão administrativa |

Maven 3.9.16, JDK 25.0.4 com release 21 e PostgreSQL 17. Maven concluiu a execução
final às 14:49:27 de 20/10, horário de São Paulo; o smoke foi executado em seguida.
Uma falha do novo teste de ambiente identificou que variáveis removidas podem
aparecer como string vazia no Windows; o carregador foi corrigido e o gate completo
reexecutado com sucesso. Avisos existentes de instrumentação Mockito/Byte Buddy
continuam documentados, sem testes ignorados.

Runtime Java 21 específico e revisão independente permanecem `nao-executado`.
Não houve dependência nova, commit, push, deploy ou alteração em banco remoto.

## Fotos e moderação implementadas (tarefas 10 e 11)

Contrato: 3:4 vertical, principal até 1080 × 1440 px, mínimo 600 × 800 px sem
ampliação, JPEG/sRGB com qualidade inicial 85% e teto de 1 MiB; miniatura 360 × 480.
Entrada JPEG/PNG até 8 MiB, com limites de decodificação e remoção de metadados.
Detalhes e critérios estão no [padrão de fotos do MVP](fotos-padrao-mvp.md) e no [checklist do MVP](mvp-checklist.md).

A migração `V3__fotos_e_moderacao.sql` e o pacote `com.jambuapp.api.foto` implementam:
persistência de `Foto`, `FotoPerfil`, `FotoDish`, `TarefaModeracao` e `RegistroModeracao`;
processamento com verificação de magic bytes, bloqueio de APNG, limites de 20 MP / 8192 px
antes de alocar a imagem, orientação EXIF 1..8, remoção de EXIF/GPS e geração de miniatura;
armazenamento privado local (`ArmazenamentoFotosLocal`) e adaptador HTTP para bucket
privado no Supabase Storage (`SupabaseStorageAdapter`); fila persistente com reserva e token,
adaptador HTTP `RekognitionAdapter`, política configurável (`PoliticaModeracao`), retentativas
com backoff exponencial sem aprovar em falha, contestação pelo autor, revisão administrativa
protegida e limpeza de arquivos substituídos/expirados. V3 também foi aplicada ao PostgreSQL
local persistente com backup (`apos-v3-2026-10-20-5614774e6564.dump`, SHA-256 `0798CA5A87CB53A5CB95F0DC94C8C0F79D672C3B796E7B4EAF87363CB31DBC1E`)
restaurado e conferido em container isolado.
