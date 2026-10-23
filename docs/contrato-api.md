# Contrato HTTP da API — posts públicos e Places

Base local: `http://127.0.0.1:8080`. JSON UTF-8. IDs UUID; notas decimais;
datas `YYYY-MM-DD`; instantes ISO-8601 em UTC. O dia de publicação é calculado
em `America/Sao_Paulo`. Campos desconhecidos no JSON são rejeitados.

Todas as rotas de negócio exigem
`Authorization: Bearer <token>`. Saúde pública: `GET /actuator/health`.
Renovação e logout aceitam sua credencial própria. As rotas /api/auth/web usam cookie
de refresh com CSRF e origem explícita; ele não autentica rotas de negócio. Não há
Basic, conta padrão ou formulário de login. Consulte [sessões persistentes](sessoes-persistentes.md).
Contas sempre públicas: perfis e Meals podem ser consultados sem seguir o autor,
desde que o leitor esteja autenticado. Não há pedidos de seguimento, aprovação ou
configuração de conta privada. `isPrivate`, `privateAccount`, `visibilidade` e outros
campos desconhecidos são rejeitados no cadastro e na atualização.

## Rotas

| Método e caminho | Resultado |
| --- | --- |
| `POST /api/auth/cadastro` | 201, dados da própria conta |
| `POST /api/auth/login` | 200, acesso de 15 min, refresh e datas; `Cache-Control: no-store` |
| `POST /api/auth/refresh` | Renova com refreshToken e operacaoId, sem exigir Bearer válido |
| `POST /api/auth/logout` | 204, revoga a sessão atual |
| `GET /api/usuarios/me` | Dados privados da própria conta |
| `PUT /api/usuarios/me` | Atualiza username, e-mail e celular |
| `GET /api/usuarios?termo=&pagina=0&limite=20` | Busca: id e username |
| `GET /api/usuarios/{id}` | Perfil: id, username, seguidores, seguindo, quantidadeMeals e foto aprovada opcional |
| `PUT /api/usuarios/{id}/seguimento` | 204; seguir, idempotente |
| `DELETE /api/usuarios/{id}/seguimento` | 204; deixar de seguir, idempotente |
| `GET /api/usuarios/{id}/seguidores` | Lista paginada de id e username |
| `GET /api/usuarios/{id}/seguindo` | Lista paginada de id e username |
| `GET /api/usuarios/{id}/meals` | Meals ativas, por cursor |
| `POST /api/usuarios/me/foto` | 201, multipart `arquivo`; envia/substitui foto 3:4 de perfil (`PENDENTE`) |
| `DELETE /api/usuarios/me/foto` | 204, remove foto de perfil atual |
| `POST /api/dishes/{dishId}/foto` | 201, multipart `arquivo`; envia/substitui foto 3:4 do Dish (`PENDENTE`) |
| `GET /api/fotos/{fotoId}` | Estado da foto (apenas autor ou moderador) |
| `GET /api/fotos/{fotoId}/conteudo` | Binário `image/jpeg` da foto aprovada (`404` se não aprovada) |
| `GET /api/fotos/{fotoId}/miniatura` | Binário `image/jpeg` 360×480 da foto aprovada (`404` se não aprovada) |
| `POST /api/fotos/{fotoId}/contestacao` | Autor contesta foto `REJEITADA`, encaminhando para `EM_REVISAO` |
| `DELETE /api/fotos/{fotoId}` | 204, autor remove a própria foto |
| `GET /api/moderacao/fotos` | Lista paginada de fotos para moderadores configurados |
| `GET /api/moderacao/fotos/{fotoId}` | Detalhe administrativo e histórico `registros_moderacao` |
| `GET /api/moderacao/fotos/{fotoId}/conteudo` | Binário para inspeção do moderador |
| `POST /api/moderacao/fotos/{fotoId}/revisao` | Decisão humana (`APROVAR`, `REJEITAR`, `REVISAR`) com motivo e auditoria |
| `DELETE /api/moderacao/fotos/{fotoId}` | 204, remoção administrativa com motivo |
| `GET /api/restaurantes/sugestoes?termo=&tokenSessao=` | Sugestões Places; `GET /api/restaurantes` é alias com os mesmos parâmetros |
| `POST /api/restaurantes/selecoes` | 200, cria/reutiliza UUID local após validação Places |
| `GET /api/restaurantes/{id}` | Perfil; nota ausente quando não há Meals |
| `GET /api/restaurantes/{id}/meals` | Meals ativas, por cursor |
| `POST /api/meals` | 201, exige `Idempotency-Key`; header Location |
| `GET /api/meals/{id}` | Detalhe de Meal ativa |
| `PUT /api/meals/{id}` | Edição completa, exige `versao`; apenas autor |
| `DELETE /api/meals/{id}` | 204, exclusão lógica; apenas autor |
| `GET /api/meals/{id}/link` | `{ "url": "..." }`; própria ou de conta seguida |
| `GET /api/timeline` | Meals próprias e de contas seguidas |

Listas de usuários/seguimentos retornam `{ "itens": [], "pagina": 0, "total": 0 }`; página começa
em 0, limite entre 1 e 50. Buscas aceitam até 100 caracteres. Listas de Meals recebem
`cursor` opcional e `limite`, retornando `{ "itens": [], "proximoCursor": null }`.
Não decodifique ou construa cursores no cliente. Ordenação: publicação e UUID,
ambos decrescentes. Novos posts posteriores à primeira página aparecem ao atualizar
a timeline; mudanças em seguimentos/exclusões podem alterar as páginas seguintes.

## Cadastro e sessão

```json
{
  "username": "usuario_teste",
  "email": "usuario@example.test",
  "celular": "98999999999",
  "senha": "Uma-senha-de-teste-123!"
}
```

Username: 3–30 letras ASCII, números ou `_`. E-mail e username são normalizados
para minúsculas e têm unicidade no banco. Celular: 10–15 dígitos, com `+` opcional;
não implica verificação de posse. Senha: 12–128 caracteres, sem normalização.
`PUT /api/usuarios/me` recebe os três campos de contato, sem senha.

```json
{ "identificador": "usuario_teste", "senha": "Uma-senha-de-teste-123!" }
```

Login aceita username ou e-mail. Retorna token, expiraEm, refreshToken, sessaoId e
expiraSessaoEm. Acesso expira em 15 minutos; refresh renova silenciosamente dentro de
30 dias sem renovação e do limite original de 180 dias. Login/refresh web não expõem
refresh no JSON. Revogação ou expiração definitiva exigem novo login. Contrato completo,
gestão de dispositivos, CSRF e repetição estão em [sessões persistentes](sessoes-persistentes.md).

## Buscar e selecionar restaurante

1. O cliente gera um UUID novo como `tokenSessao` para cada interação de busca Places.
2. Chama `GET /api/restaurantes/sugestoes?termo=arroz&tokenSessao=<UUID>`.
3. Envia `POST /api/restaurantes/selecoes` com o placeId escolhido e o mesmo token.
4. Usa o `id` local retornado como `restauranteId` na Meal.

```json
{
  "googlePlaceId": "PLACE_ID_RECEBIDO_NA_SUGESTAO",
  "tokenSessao": "11111111-1111-4111-8111-111111111111"
}
```

O token Places não é o Bearer: a sessão de login continua no header Authorization.
Use token novo após concluir/abandonar uma busca; ele não é reutilizável como sessão
permanente. Perfil de restaurante não recebe token de autocomplete.
Não enviar nome, endereço, nota ou fotos na seleção; campos extras são rejeitados.

Sugestões retornam `{ "itens": [{ "googlePlaceId": "...", "descricao": "..." }],
"atribuicaoGoogleMaps": "Google Maps" }`. A busca aceita 2–100 caracteres; não é uma
lista paginada do catálogo local. Somente sugestões de alimentação aceitas são
retornadas, com até cinco candidatos fornecidos pelo Google antes do filtro.

Seleção/detalhe retornam: `id`, `googlePlaceId`, `nome`, `endereco`,
`dadosExternosDisponiveis`, `origem`, `googleMapsUri`, `atribuicoes`
(`provedor`, `uri`), `atribuicaoGoogleMaps`, `notaMedia` e `quantidadeAvaliacoes`.
Média e contagem são exclusivamente Jambu. Tipos aceitos inicialmente: `restaurant`,
`cafe`, `bakery`, `bar`, `meal_takeaway`, `meal_delivery`, `food_court`.
Os tipos são conferidos novamente pelo servidor no Place Details.

Todas essas respostas incluem `Cache-Control: no-store`. Conteúdo externo e
atribuições devem ser apresentados juntos nas interfaces; não persistir os payloads
como catálogo, seed ou cache permanente. Consulte as [regras de exibição](places-posts-publicos.md).

Se Google falhar, sugestões/seleção falham com erro controlado. Detalhe local continua
200 com médias próprias, `dadosExternosDisponiveis: false` e campos externos nulos.
Legados sem vínculo retornam `origem: LEGADO`, placeId nulo e seus dados próprios
originais. Não são fallback de seleção e não servem a novas publicações.
`GET /api/restaurantes/{id}/meals` consulta somente o banco, sem acesso ao Google.

## Publicar Meal

Não existe teto de publicações por dia. Os limites de tamanho, notas e paginação
continuam válidos para todos os usuários.

Header obrigatório: `Idempotency-Key: <chave-gerada-no-cliente>` com 1–128 caracteres
ASCII alfanuméricos ou `.`, `_`, `:`, `-`. Reutilize a mesma chave ao repetir o mesmo
envio após falha de rede; use chave nova para uma nova publicação.

```json
{
  "restauranteId": "UUID_LOCAL_RETORNADO_PELA_SELECAO",
  "dishes": [
    { "nome": "Arroz de cuxá", "nota": 4.5 },
    { "nome": "Peixe", "nota": 5 }
  ],
  "review": "Refeição de teste.",
  "comeriaNovamente": true,
  "modalidade": "PRESENCIAL",
  "dataRefeicao": "2026-10-19"
}
```

`review`, `modalidade` e `dataRefeicao` são opcionais. Modalidades: `PRESENCIAL`,
`DELIVERY`, `RETIRADA`. `comeriaNovamente` é obrigatório. São permitidos 1–20 Dishes
por padrão; nomes até 150 caracteres; notas de 0,5 a 5 em passos de 0,5.
Review até 2.000 caracteres por padrão. Não enviar `autorId`, `notaOverall`,
instantes de auditoria, versão ou IDs de Dishes na criação.

A resposta inclui id, resumo do autor, referência do restaurante `{ "id": "UUID", "googlePlaceId": "..." }`, Dishes com IDs,
notaOverall com duas casas, review, comeriaNovamente, modalidade, dataRefeicao,
publicadaEm, editadaEm e versao. Não inclui contato, hash ou entidade JPA.

Repetição equivalente retorna 201 e o snapshot original, mesmo depois de edição
ou exclusão. Mesma chave com conteúdo diferente retorna 409. A comparação normaliza
espaços nas bordas dos textos e escala das notas. Ordem dos pratos é significativa;
data omitida e data explícita são conteúdos diferentes. Publicação e retry não chamam
Google. Nome/endereço e atribuições são consultados no detalhe de restaurante.
Snapshots antigos continuam aceitos sem alterar chave ou hash: campos antigos de
nome/endereço são ignorados ao desserializar a referência, e placeId ausente é `null`.
O JSON antigo no banco não é reescrito e os demais dados originais são preservados.

## Editar e excluir

Envie para `PUT` o estado completo dos Dishes, com `versao` recebida na consulta.
Mantenha IDs de pratos existentes; novos pratos não têm ID; pratos omitidos são
removidos. Todos os IDs informados devem pertencer à Meal e não se repetir. O último
Dish não pode ser removido. A ordem enviada é persistida.

Versão ausente/desatualizada retorna 409. A edição avança a versão mesmo quando só
um Dish muda. Data omitida na edição preserva a data existente; review/modalidade
omitidos são limpos. Autor e instante de publicação permanecem os mesmos.

Uma Meal removida retorna 404 e sai de timeline, perfis e médias. Edição no mesmo
restaurante legado continua permitida. Novas publicações e troca de restaurante
exigem referência local já validada pelo Places. Nota do restaurante é a média das médias não arredondadas de suas
Meals ativas, com o mesmo peso por Meal; arredondamento só na resposta.

## Erros

Erros de domínio, validação e segurança usam `application/problem+json`, com
`status`, `detail` e `codigo`. Bean Validation inclui `campos`, sem ecoar valores
sensíveis. Códigos relevantes: `dados_invalidos`, `conflito`, `recurso_inexistente`,
`acesso_negado`, `credenciais_invalidas`, `sessao_invalida`,
`autenticacao_necessaria`, `limite_autenticacao`.

400 indica entrada inválida; 401 exige login; 403 nega autoria/compartilhamento;
404 indica ausência ou exclusão; 409 indica duplicidade/versão/conteúdo em conflito;
429 indica excesso de tentativas de autenticação. Places usa 422 para lugar inválido
ou tipo não permitido; 502 para resposta externa inválida; 503 para integração
ausente/indisponível (incluindo limite do provedor); 504 para timeout. Mensagens externas,
chave e corpo de erro Google não são retornados ao cliente.

## Fotos e moderação

Uploads usam `multipart/form-data` no campo `arquivo`, com limite de 8 MiB (8.388.608 bytes)
e no máximo 20 megapixels ou 8192 px por lado. Somente JPEG e PNG estáticos são aceitos,
validados por assinatura binária e decodificação; orientação EXIF é aplicada antes da
validação de proporção 3:4 (`largura * 4 = altura * 3`) e mínimo de `600 × 800 px`.
A imagem principal é reduzida até `1080 × 1440 px` (sem ampliar menores), reencodada em
JPEG/sRGB sem metadados EXIF/GPS (teto de 1 MiB, qualidades 85%→70%), junto de miniatura
`360 × 480 px` (teto de 200 KiB, qualidades 80%→70%).

Toda foto nasce `PENDENTE` com tarefa persistente em `tarefas_moderacao` vinculada ao
`hash_conteudo` (SHA-256 do JPEG normalizado) e `versao_politica`. Somente `APROVADA`
aparece em `PerfilResponse.foto` e `DishResponse.foto` (`{ "id": "...", "url": "/api/fotos/.../conteudo", "urlMiniatura": "/api/fotos/.../miniatura" }`)
e libera `/conteudo` e `/miniatura`. Substituir foto cria novo registro/arquivo e invalida
a versão anterior. Falhas técnicas do provedor de moderação nunca aprovam fotos: aplicam
retentativa com backoff e, ao atingir o limite de tentativas, encaminham para `EM_REVISAO`.
