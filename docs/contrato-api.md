# Contrato HTTP da API — posts públicos e catálogo local

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
| `POST /api/fotos` | 201, multipart `arquivo`; foto do post (T16), solta até entrar numa Meal por `fotoIds`; some em 24 h se não for usada |
| `GET /api/fotos/{fotoId}` | Estado da foto (apenas autor ou moderador) |
| `GET /api/fotos/{fotoId}/conteudo` | Binário `image/jpeg` da foto aprovada (`404` se não aprovada) |
| `GET /api/fotos/{fotoId}/miniatura` | Binário `image/jpeg` 360×480 da foto aprovada (`404` se não aprovada) |
| `POST /api/fotos/{fotoId}/contestacao` | Autor contesta foto `REJEITADA`, encaminhando para `EM_REVISAO` |
| `DELETE /api/fotos/{fotoId}` | 204, autor remove a própria foto |
| `GET /api/moderacao/fotos` | Lista paginada de fotos para moderadores configurados (UUIDs em `JAMBU_MODERADORES_FOTOS`; o nome de usuário não conta) |
| `GET /api/moderacao/fotos/{fotoId}` | Detalhe administrativo e histórico `registros_moderacao` |
| `GET /api/moderacao/fotos/{fotoId}/conteudo` | Binário para inspeção do moderador |
| `POST /api/moderacao/fotos/{fotoId}/revisao` | Decisão humana (`APROVAR`, `REJEITAR`, `REVISAR`) com motivo e auditoria |
| `DELETE /api/moderacao/fotos/{fotoId}` | 204, remoção administrativa com motivo |
| `GET /api/restaurantes?termo=&tokenSessao=&ampliar=false` | Sugestões locais; amplia automaticamente quando vazio ou explicitamente com ampliar=true; /sugestoes é alias |
| `POST /api/restaurantes/cadastros` | Cadastro próprio idempotente, confirmação de possíveis duplicatas e placeId opcional |
| `POST /api/restaurantes/proximos` | "Você está aqui?" (T11): até 10 lugares em 150 m do ponto, ampliando para 500 m; só o banco |
| `POST /api/restaurantes/selecoes` | Reutiliza referência Google existente (caso a) ou casa com o Overture escondido (caso b, T05); senão 422 cadastro_necessario |
| `GET /api/moderacao/restaurantes` | Fila paginada de revisão, restrita aos UUIDs configurados |
| `GET /api/moderacao/restaurantes/retornos` | Fila paginada das correções sugeridas pela 2ª conta, só founders |
| `POST /api/moderacao/restaurantes/{id}/revisao` | Revisão/correção com versão e auditoria; resolve as correções sugeridas do lugar |
| `POST /api/moderacao/restaurantes/importacoes/overture` | Importação administrativa de lote GeoJSON |
| `GET /api/restaurantes/{id}` | Perfil só com dados do Jambu; nota e `ultimaAvaliacaoEm` ausentes quando não há Meals |
| `GET /api/restaurantes/{id}/meals` | Meals ativas, por cursor |
| `GET /api/restaurantes/{id}/confirmacao` | `{ "podeConfirmar": true }` quando o app deve perguntar "Nome e bairro estão certos?" |
| `POST /api/restaurantes/{id}/retornos` | Resposta da 2ª conta a "Nome e bairro estão certos?": `CONFIRMACAO` verifica, `CORRECAO` mantém provisório |
| `POST /api/meals` | 201, exige `Idempotency-Key`; header Location |
| `GET /api/meals/{id}` | Detalhe de Meal ativa |
| `PUT /api/meals/{id}` | Edição completa, exige `versao`; apenas autor |
| `DELETE /api/meals/{id}` | 204, exclusão lógica; apenas autor |
| `PUT /api/meals/{id}/curtida` | 204; curtir, idempotente (uma curtida por pessoa); Meal excluída ou inexistente: 404 |
| `DELETE /api/meals/{id}/curtida` | 204; descurtir, idempotente |
| `GET /api/meals/{id}/curtidas` | `{ "quantidade": 2, "curtidaPorMim": true }` |
| `POST /api/meals/{id}/comentarios` | 201; comentar, exige `Idempotency-Key`; ver abaixo |
| `GET /api/meals/{id}/comentarios` | Comentários do mais antigo ao mais novo, por cursor |
| `DELETE /api/meals/{id}/comentarios/{comentarioId}` | 204; só o autor do comentário ou o dono do post |
| `GET /api/meals/{id}/link` | `{ "url": "..." }`; própria ou de conta seguida |
| `GET /api/feed?aba=PARA_VOCE` ou `?aba=SEGUINDO` | Feed do app, sem os próprios posts; ver abaixo |
| `GET /api/timeline` | Meals próprias e de contas seguidas (fica até o app usar o feed) |

Listas de usuários/seguimentos retornam `{ "itens": [], "pagina": 0, "total": 0 }`; página começa
em 0, limite entre 1 e 50. Buscas aceitam até 100 caracteres. Listas de Meals recebem
`cursor` opcional e `limite`, retornando `{ "itens": [], "proximoCursor": null }`.
**Comentários (T21).** `POST /api/meals/{id}/comentarios` com `{ "texto": "..." }` (1 a 1.000
caracteres, espaços nas pontas removidos) e `Idempotency-Key`. Resposta 201
`{ "id", "autor": { "id", "username" }, "texto", "criadoEm" }`. Repetir a mesma chave com o mesmo
texto devolve o mesmo comentário; outro texto com a mesma chave dá 409. `GET` lista do mais antigo
para o mais novo, com `cursor` e `limite` (1 a 50), no formato `{ "itens", "proximoCursor" }`.
Meal excluída ou inexistente: 404.
`DELETE /api/meals/{id}/comentarios/{comentarioId}` responde 204 ao autor do comentário e ao dono do
post (RN-34); outra conta recebe 403 `acesso_negado`, inclusive depois de excluído. Repetir a
exclusão devolve 204. Comentário de outro post ou inexistente: 404. O comentário excluído sai da
lista e fica marcado no banco (`excluido_em`).

**Feed (T19).** `GET /api/feed?aba=PARA_VOCE&cursor=&limite=20`: `PARA_VOCE` traz os posts de todos os
outros usuários; `SEGUINDO`, só de quem a pessoa segue. **Nenhuma aba traz os próprios posts**, que
ficam no perfil. Mesma ordem, cursor e limite da timeline. Cada item é
`{ "meal": { ...Meal... }, "nomeRestaurante": "Coisas do Pará", "curtidas": 2, "curtidaPorMim": true, "comentarios": 3 }`,
com o nome vindo do catálogo do Jambu (`null` só nas referências antigas do Google ainda sem nome).
`curtidas`, `curtidaPorMim` e `comentarios` (só os ativos) ficam no item, fora da Meal, porque a resposta da publicação é guardada
para a idempotência e o total muda com o tempo. `SEGUINDO` vazio volta
`{ "itens": [], "proximoCursor": null }`, e o app mostra "Siga pessoas para ver os posts delas aqui".
`aba` ausente ou fora das duas: 400 `requisicao_invalida`.
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

Consultar `GET /api/restaurantes?termo=aka` (2–100 caracteres).
A API devolve `locais`, `itens` externos, `estadoExterno`, `podeVerMais`,
`podeAdicionar`, `tokenSessao` e atribuição Google quando aplicável.
Com sugestões locais, **Ver mais resultados** envia `ampliar=true` e preserva
termo/token. Sem sugestões locais, o servidor amplia automaticamente. Com Google
desligado, erro ou cota esgotada, o cadastro próprio permanece disponível.

Escolher um local significa usar seu UUID diretamente na Meal. Para cadastrar,
enviar `POST /api/restaurantes/cadastros` com Bearer e `Idempotency-Key`:

```json
{
  "nome": "Nome informado pela pessoa",
  "latitude": -2.53,
  "longitude": -44.30,
  "categoria": "RESTAURANTE",
  "googlePlaceId": null,
  "tokenSessao": null,
  "distintosConfirmados": []
}
```

Respostas: CRIADO, REUTILIZADO, CONFIRMAR_DUPLICATAS ou CRIADO_SEM_GOOGLE.
CONFIRMAR_DUPLICATAS não cria registro; permite escolher candidato ou reenviar
a mesma chave com os UUIDs recusados em distintosConfirmados. Os cadastros novos
ficam PROVISORIO e já aceitam Meal. Conteúdo próprio não deve ser copiado do Google.

Quando houver seleção externa, informar placeId e token para validar/vincular;
somente o ID validado é persistido. `/selecoes` (`{ "googlePlaceId", "tokenSessao", "textoBusca" }`,
`textoBusca` opcional, até 100 caracteres) reutiliza referências Google já existentes e, com o casamento
ligado, cria o restaurante a partir do Overture escondido (ver [catálogo](catalogo-restaurantes.md));
nunca cria restaurante só com o placeId.

A busca local ignora acentos, usa prefixos das palavras, tolera erro de digitação
(trigramas) e também acha o restaurante pelo nome de pratos já avaliados e por "nome + bairro"
("coisas do cohajap", T07); nome exato vem
primeiro e verificados (`ATIVO`) antes de provisórios; até 20 sugestões.
A ampliação só acontece com 3 letras ou mais (`TERMO_CURTO` abaixo disso), é limitada a uma por
segundo e 30 por dia por usuário (`AGUARDE` e `LIMITE_DIARIO`) e a 300 chamadas/dia para a API
inteira, incluindo detalhes, no fuso America/Sao_Paulo. O Google só sugere lugares dentro do
retângulo da área do catálogo (`locationRestriction`), e o detalhe pede só `id,types`. O token de busca
não é o Bearer. Respostas incluem Cache-Control: no-store; manter atribuições visíveis.

**"Você está aqui?" (T11).** `POST /api/restaurantes/proximos` com
`{ "latitude": -2.4990, "longitude": -44.2410 }` (localização do celular ou da foto; latitude −90 a 90,
longitude −180 a 180, ambas obrigatórias, senão 400 `dados_invalidos`). Resposta 200
`{ "itens": [{ "restaurante": { ...resumo da busca... }, "distanciaMetros": 12 }] }`: até 10 lugares a até
150 m, do mais perto para o mais longe; sem nenhum em 150 m, até 10 em 500 m; nada em 500 m (ou ponto
fora da ilha), lista vazia, e o app segue com a busca e "Adicionar". Entram verificados e provisórios;
rejeitados e escondidos do Overture, não. A localização vai no corpo, e não na URL, para não ficar em
logs de acesso; a API não a guarda e **nunca chama o Google** aqui.

Perfis usam só dados do Jambu e **nunca chamam o Google**, nem com a cota esgotada.
O campo **`bairro`** (T07), no resumo da busca e no perfil, sai do nosso pin e da base de bairros do
OpenStreetMap: se o endereço do Overture cita um bairro com ponto a até 3 km do pin, vale esse nome
(o nome da rua, antes da primeira vírgula, não conta);
senão, o ponto de bairro mais próximo, até 2 km; mais longe que isso, `null`. O app mostra
"Nome · Bairro". O banco preenche o bairro sozinho quando o restaurante nasce ou muda de pin ou de
endereço.
Referências Google antigas sem nome próprio aparecem com `nome` nulo até a revisão dos
founders. `dadosExternosDisponiveis` é sempre `false`, `atribuicoes` vem vazia e
`atribuicaoGoogleMaps` nula (mantidos por compatibilidade). `googleMapsUri` é um link Maps
URLs, sem chave nem cobrança: com `place_id`, `query_place_id` mais o nome próprio; sem
`place_id`, latitude e longitude; sem nenhum dos dois, nulo. `ultimaAvaliacaoEm` é o instante
de publicação da Meal ativa mais recente.
Restaurantes rejeitados não aceitam nova Meal/troca, mas preservam histórico.
GET /api/restaurantes/{id}/meals consulta somente o banco.

**Confirmação pela 2ª conta (T08).** `POST /api/restaurantes/{id}/retornos` com
`{ "tipo": "CONFIRMACAO" }` (o "Sim") ou `{ "tipo": "CORRECAO", "nomeSugerido": "...", "observacao": "..." }`
(o "Corrigir"; pelo menos um dos dois, nome até 150 e observação até 500 caracteres). Resposta 200
`{ "tipo": "CONFIRMACAO", "situacaoRestaurante": "ATIVO" }`. Só responde quem **não** cadastrou o lugar
e tem Meal ativa nele (senão 403 `acesso_negado`); lugar inexistente ou rejeitado dá 404; lugar já
verificado dá 409 `conflito`. Cada pessoa responde uma vez por lugar: repetir a mesma resposta devolve
200 de novo (seguro para reenviar); mandar outra dá 409. `CONFIRMACAO` leva o lugar a `ATIVO`;
`CORRECAO` o mantém `PROVISORIO` até a revisão dos founders.

`GET /api/restaurantes/{id}/confirmacao` diz se o app deve fazer a pergunta, por exemplo logo depois
de publicar: `{ "podeConfirmar": true }` só quando o lugar é provisório, a pessoa não o cadastrou, tem
Meal ativa nele e ainda não respondeu. Fica fora da resposta da Meal porque muda com o tempo e por
pessoa, e a resposta da publicação é guardada para a idempotência. Lugar inexistente ou rejeitado: 404.

Founders veem as correções em `GET /api/moderacao/restaurantes/retornos?pagina=0&limite=20`
(`{ "itens": [{ "id", "restauranteId", "nomeAtual", "versaoRestaurante", "nomeSugerido", "observacao",
"usuarioId", "criadoEm" }], "pagina", "total" }`, das mais antigas para as mais novas; 403 para quem não
é founder). A revisão existente do lugar (`POST /api/moderacao/restaurantes/{id}/revisao`, com
`versaoRestaurante`) resolve as correções dele, que saem da fila.

Contrato completo, revisão por UUID, importação e exemplos:
[catálogo de restaurantes](catalogo-restaurantes.md).

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
    { "nome": "Arroz de cuxá", "nota": 4.5, "comeriaNovamente": true, "preco": 32.50 },
    { "nome": "Peixe", "nota": 5 }
  ],
  "fotoIds": ["UUID_DA_FOTO_CAPA", "UUID_DA_SEGUNDA_FOTO"],
  "review": "Refeição de teste.",
  "comeriaNovamente": true,
  "modalidade": "PRESENCIAL",
  "dataRefeicao": "2026-10-19"
}
```

`review`, `modalidade` e `dataRefeicao` são opcionais; `dataRefeicao` no futuro (fuso de São
Luís) é recusada com 400. Modalidades: `PRESENCIAL`, `DELIVERY`, `RETIRADA`. Em cada Dish,
`comeriaNovamente` e `preco` (≥ 0, até 2 casas) são opcionais. `comeriaNovamente` é uma marcação:
`true` (marcado) ou `false` (desmarcado); ausente é gravado como `false`, e a resposta sempre traz
`true` ou `false`. Desmarcado não quer dizer "não comeria". `preco` ausente continua `null`, nunca 0.
O `comeriaNovamente` da Meal inteira continua aceito por compatibilidade, com a mesma regra. São permitidos 1–20 Dishes
por padrão; nomes até 150 caracteres; notas de 0,5 a 5 em passos de 0,5.
Review até 2.000 caracteres por padrão. Não enviar `autorId`, `notaOverall`,
instantes de auditoria, versão ou IDs de Dishes na criação.

**`fotoIds` (T16) é obrigatório:** de 1 a 10 fotos enviadas antes por `POST /api/fotos`, sem repetir, na
ordem de exibição (a 1ª é a capa). Cada foto tem de ser da própria pessoa, estar solta (enviada há menos
de 24 h e ainda sem post) e não ter sido rejeitada. Qualquer foto fora disso (de outra conta, já usada,
vencida, rejeitada ou inexistente) dá 400 `dados_invalidos` com a mesma mensagem, para não revelar se a
foto de outra pessoa existe; nada é publicado. Duas publicações simultâneas com a mesma foto: só uma leva.

A resposta inclui id, resumo do autor, referência do restaurante `{ "id": "UUID", "googlePlaceId": "..." }`, Dishes com IDs,
`fotos` (as aprovadas, na ordem do post: `[{ "id", "url", "urlMiniatura" }]`; vazia enquanto nenhuma foi
aprovada), notaOverall com duas casas, review, comeriaNovamente, modalidade, dataRefeicao,
publicadaEm, editadaEm e versao. Não inclui contato, hash ou entidade JPA. Desde a T16 os dishes não têm
mais `foto`: a foto é do post, e a marcação de quais dishes aparecem em cada foto vem na T17.

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

A edição também manda `fotoIds` (1 a 10): as fotos que o post já tem e, se quiser, fotos soltas novas, na
ordem desejada. Dá para reordenar e trocar a capa; a foto do post que sai da lista é removida, com os
arquivos. Foto de outro post ou fora das regras da publicação: 400 `dados_invalidos`. `DELETE
/api/fotos/{id}` de uma foto que está num post dá 409 `conflito`: ela sai pela edição. Excluir o post
remove todas as fotos dele.

Versão ausente/desatualizada retorna 409. A edição avança a versão mesmo quando só
um Dish muda. Data omitida na edição preserva a data existente; review/modalidade
omitidos são limpos. Autor e instante de publicação permanecem os mesmos.

Uma Meal removida retorna 404 e sai de timeline, perfis e médias. Edição no mesmo
restaurante legado continua permitida. Novas publicações e troca de restaurante
exigem restaurante local selecionável, inclusive provisório. Nota do restaurante é a média das médias não arredondadas de suas
Meals ativas, com o mesmo peso por Meal; arredondamento só na resposta.

## Erros

Erros de domínio, validação e segurança usam `application/problem+json`, com
`status`, `detail` e `codigo`. Bean Validation inclui `campos`, sem ecoar valores
sensíveis. Códigos relevantes: `dados_invalidos`, `conflito`, `recurso_inexistente`,
`acesso_negado`, `credenciais_invalidas`, `sessao_invalida`,
`autenticacao_necessaria`, `limite_autenticacao`, `fora_da_area`, `limite_cadastros`.

400 indica entrada inválida; 401 exige login; 403 nega autoria/compartilhamento;
404 indica ausência ou exclusão; 409 indica duplicidade/versão/conteúdo em conflito;
429 indica excesso de tentativas de autenticação ou de lugares novos no dia (`limite_cadastros`),
sempre com `Retry-After` em segundos. Seleção Google inválida ou de
tipo não permitido usa 422; pin fora da área do catálogo também (`fora_da_area`). Na busca, falha/cota/ausência do Google são estados
controlados de uma resposta 200; cadastro com dados próprios pode prosseguir sem
vínculo externo. Mensagens externas,
chave e corpo de erro Google não são retornados ao cliente.

## Fotos e moderação

Uploads usam `multipart/form-data` no campo `arquivo`, com limite de 8 MiB (8.388.608 bytes)
e no máximo 20 megapixels ou 8192 px por lado. Somente JPEG e PNG estáticos são aceitos,
validados por assinatura binária e decodificação; orientação EXIF é aplicada antes da
validação de proporção 3:4 (`largura * 4 = altura * 3`) e mínimo de `600 × 800 px`.
A imagem principal é reduzida até `1080 × 1440 px` (sem ampliar menores), reencodada em
JPEG/sRGB sem metadados EXIF/GPS (teto de 1 MiB, qualidades 85%→70%), junto de miniatura
`360 × 480 px` (teto de 200 KiB, qualidades 80%→70%).

**Moderação desligada (padrão do MVP, `JAMBU_FOTOS_MODERACAO_HABILITADA=false`; T18):** toda foto, do post
e de perfil, nasce `APROVADA` e aparece para todos na hora, sem tarefa de análise; `registros_moderacao`
guarda "publicada na hora". A moderação é reativa: quando um moderador reprova a foto de um post (revisão
`REJEITAR` ou `DELETE /api/moderacao/fotos/{id}`), **o post inteiro é excluído** (some do feed, dos perfis e
das médias; `GET /api/meals/{id}` dá 404) e todas as fotos dele ficam `REMOVIDA`, com os arquivos apagados;
não há contestação. Reprovar a foto de perfil só tira a foto. A reprovação automática (moderação ligada)
só esconde a foto, sem excluir o post.

Com a moderação ligada, toda foto nasce `PENDENTE` com tarefa persistente em `tarefas_moderacao` vinculada ao
`hash_conteudo` (SHA-256 do JPEG normalizado) e `versao_politica`. Somente `APROVADA`
aparece em `PerfilResponse.foto` e em `MealResponse.fotos` (`{ "id": "...", "url": "/api/fotos/.../conteudo", "urlMiniatura": "/api/fotos/.../miniatura" }`)
e libera `/conteudo` e `/miniatura`. Substituir foto cria novo registro/arquivo e invalida
a versão anterior. Falhas técnicas do provedor de moderação nunca aprovam fotos: aplicam
retentativa com backoff e, ao atingir o limite de tentativas, encaminham para `EM_REVISAO`.

**Foto do post (T16).** O app envia cada foto com `POST /api/fotos` quando a pessoa toca em Publicar (o
rascunho guarda as fotos no aparelho) e recebe o `id`. Até entrar numa Meal, a foto fica **solta**: só o
autor consulta o estado em `/api/fotos/{id}`, `/conteudo` e `/miniatura` dão 404, e ela passa pela mesma
moderação. Foto solta com mais de 24 h é removida, com os arquivos, pelo agendador da limpeza.
