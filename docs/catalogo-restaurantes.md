# Seleção de restaurantes — catálogo local e Overture

Implementação de 22/10/2026. Este contrato substitui o fluxo exclusivamente Google
de 19/10. A API continua um monólito Java/Spring por funcionalidade, sem novas
dependências Maven. As interfaces Android/web ainda precisam consumir este contrato.

## Busca e escolha

1. Após ao menos 2 caracteres, consultar
   `GET /api/restaurantes?termo=aka&tokenSessao=<UUID>`.
2. Mostrar `locais` imediatamente. Cada item tem UUID, nome, endereço quando
   disponível, pin, categoria, origem, situação e atribuição do catálogo.
3. Se houver sugestões locais, `podeVerMais=true`. O botão **Ver mais resultados**
   repete a consulta com `ampliar=true`, preservando o termo e token.
4. Se a lista local estiver vazia, o próprio servidor amplia automaticamente.
5. Os resultados Google ficam em `itens`, separados dos locais, com atribuição.
   O servidor não grava suas descrições nem as usa para preencher o cadastro.
6. A pessoa escolhe um UUID local e o usa diretamente na Meal. Nenhuma chamada
   Google ocorre na publicação. Provisórios também são selecionáveis.
7. **Adicionar restaurante** permanece disponível mesmo em erro, ausência de
   resultados, cota esgotada ou Google desativado.

A resposta da busca inclui `estadoExterno`: `NAO_SOLICITADO`,
`DISPONIVEL`, `SEM_RESULTADOS`, `DESATIVADO`, `COTA_ESGOTADA`,
`INDISPONIVEL` ou `AGUARDE`; inclui também `podeAdicionar`,
`tokenSessao` e `atribuicaoGoogleMaps`. Falhas externas não tornam a
busca local uma resposta 503. O endpoint antigo `/sugestoes` continua como alias.

O cliente deve aguardar uma pausa na digitação (sugestão: 500 ms), cancelar/ignorar
respostas de termos ultrapassados e evitar buscas externas paralelas. O servidor
permite no máximo uma ampliação por segundo por usuário, entre instâncias, retornando
`AGUARDE` nas repetições rápidas, sem consumir chamada Google. Nesse caso o
cliente preserva a lista que já possui e pode repetir depois de 1 segundo.
O token da interação pode ser criado pelo cliente; se omitido, a API devolve um
UUID. Reutilizar na mesma interação e trocar ao concluir ou abandonar; não é token
de autenticação. Não armazenar respostas Google como cache permanente.

A busca local ignora acentos e pontuação e procura prefixos das palavras com índices
GIN de full text e índice de prefixo do PostgreSQL. Desde a V6 usa também a extensão
`pg_trgm` (índice GIN de trigramas): nomes parecidos (`%`, similaridade ≥ 0,3) e palavras
parecidas dentro do nome (`<%`) entram, o que perdoa erros como "burguer". Também acha o
restaurante pelo nome normalizado de pratos de Meals ativas. Ordem: nome exato, depois
verificados antes de provisórios, depois maior semelhança, nome e UUID. Retorna até 20 sugestões.
**Ver mais resultados** amplia para Google; não pagina as mesmas sugestões.

## Cadastro próprio e prevenção de duplicatas

`POST /api/restaurantes/cadastros`, com Bearer e `Idempotency-Key`
(1–128 caracteres alfanuméricos, ponto, dois-pontos, hífen ou sublinhado):

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

Categorias: RESTAURANTE, CAFE, PADARIA, BAR, LANCHONETE, SORVETERIA, PRACA_ALIMENTACAO.
O pin é validado contra as áreas de `JAMBU_AREAS_CATALOGO` (padrão `grande-sao-luis`: São Luís,
São José de Ribamar, Paço do Lumiar e Raposa, malha IBGE de qualidade máxima; ver
`src/main/resources/geografia/README.md`). Pin fora da área retorna **422 `fora_da_area`**
com "O Jambu ainda está só na Grande São Luís."; vale para cadastro, revisão e recorte da
importação. O fornecedor do mapa da futura interface continua por definir.

Respostas HTTP 200:

- `CRIADO`: retorna `restaurante` com UUID e situação PROVISORIO.
- `REUTILIZADO`: mesma chave ou mesmo placeId já cadastrado; mesmo UUID.
- `CONFIRMAR_DUPLICATAS`: não grava o restaurante; retorna `candidatos`
  com dados locais, distância em metros e similaridade.
- `CRIADO_SEM_GOOGLE`: os dados próprios foram gravados, mas falha técnica
  ou cota impediram validar o placeId; a referência Google não foi persistida.

Para **Você quis dizer algum destes?**, selecionar o UUID existente e continuar.
Se a pessoa confirmar que é outro estabelecimento, repetir o cadastro com a mesma
chave, acrescentando em `distintosConfirmados` os UUIDs dos candidatos recusados.
Um candidato novo surgido entre as requisições exige nova confirmação.
A chave já concluída com outro nome/pin/categoria/placeId retorna 409. Confirmações
e token de transporte não alteram a identidade do pedido; respostas de replay
retornam o estado atual do restaurante, preservando o UUID.

A comparação usa nome normalizado, similaridade de trigramas a partir de 0,45 e
distância Haversine de até 300 m. Esses são parâmetros técnicos iniciais,
ajustáveis por configuração; não afirmam que dois lugares são iguais.
Mesmo nome perto nunca provoca fusão automática. Transações de escrita do catálogo
usam advisory lock compartilhado entre instâncias e repetem a busca de duplicatas
antes de gravar. Somente a escrita do catálogo é serializada, sem rede sob o lock;
leituras e publicação de Meals não passam por esse lock.

No caminho Google, enviar também o placeId e token da interação. A API confere
identificador e tipo alimentar antes de vincular, sem persistir nome/endereço
externos. Nome, pin e categoria vêm de contribuição independente. Não orientar
a pessoa a copiar/redigitar dados Google para contornar as condições de uso.
Identificador/tipo inválido retorna 422; falha técnica permite cadastro próprio.
Se a referência já existe, reutiliza o UUID sem consultar novamente o provedor.

`POST /api/restaurantes/selecoes` permanece apenas para reutilizar referências
Google **já existentes**, com o DTO antigo. Um ID desconhecido retorna 422
`cadastro_necessario`; o cliente passa a preencher o cadastro próprio.
Para escolher um resultado local, basta usar seu UUID na Meal.

## Perfis, revisão e histórico

`GET /api/restaurantes/{id}` usa só dados locais/Overture e estatísticas Jambu e nunca
consulta o Google, nem para referências antigas sem nome próprio (essas aparecem com
`nome` nulo até a revisão). O link `googleMapsUri` é montado com Maps URLs, sem cobrança.
Snapshots de Meal continuam guardando somente UUID/placeId do restaurante.

Origem (LEGADO, GOOGLE_PLACES, USUARIO ou OVERTURE) é separada de situação
(ATIVO, PROVISORIO ou REJEITADO). Origem USUARIO também pode ter placeId validado.
Provisório é visível e pode receber Meal imediatamente. Rejeitado sai da busca e
não aceita nova Meal/troca; o histórico continua acessível.

Revisores são UUIDs de contas já existentes, configurados em
`JAMBU_REVISORES_RESTAURANTES`, separados por vírgula. O padrão vazio não
concede acesso a ninguém. Alterar username não concede nem remove essa permissão.

- `GET /api/moderacao/restaurantes?situacao=PROVISORIO&pagina=0&limite=20`.
- `POST /api/moderacao/restaurantes/{id}/revisao` com `versao`,
  `situacao`, `motivo` e `correcao` opcional
  (nome, latitude, longitude, categoria).

Revisão é auditada com UUID do revisor, situação anterior/nova, motivo e instante.
Versão desatualizada retorna 409; alteração ocorre junto da auditoria.
Correções recebem proteção contra sobrescrita por importações futuras.
Fusão de restaurantes é uma etapa posterior: não há reassociação silenciosa de Meals
nem remoção de UUIDs/snapshots nesta entrega.

## Importação e atualização do Overture

`POST /api/moderacao/restaurantes/importacoes/overture` é exclusivo de revisores.
Recebe `release`, `licencas` verificadas e até 200 `features` GeoJSON
originais por lote. Aceita GERS em `Feature.id` ou `properties.id`; o export do
cliente oficial pode omitir `properties.type`. Quando informado, o tipo deve ser
`place`; geometria e categoria alimentar continuam obrigatórias. Usa `version`, `names.primary`,
`basic_category`/`taxonomy`, coordenadas, endereço disponível e `sources`.
Preserva fontes, licenças, atribuição e release. Não interpreta `categories`
do esquema antigo como se fosse o formato atual.

Filtra alimentação, área do catálogo, fechamento informado e confiança abaixo de 0,7.
Ausência de confiança não é tratada como nota zero. Feature sem nome, ID, versão
ou fontes válidas rejeita o lote inteiro. Um mesmo GERS mantém o UUID nas atualizações;
release/versão anterior é ignorada e a mesma revisão é repetível. Correções locais
e decisões humanas de rejeição não são sobrescritas. Possível colisão com outro UUID
gera provisório para revisão; nunca funde por nome. Ausência em novo arquivo ou
registro fechado não apaga automaticamente um restaurante anteriormente importado.

Com a API local em execução, um revisor pode carregar o export regional:

```powershell
$token = Read-Host 'Bearer do revisor' -AsSecureString
./scripts/importar-overture.ps1 -Arquivo './recorte.geojson' -Release '2026-10-21.0' -Licencas 'CDLA-Permissive-2.0','Apache-2.0','CC0-1.0' -Token $token
```

A lista de licenças do exemplo não substitui conferir as fontes do export.
Preservar também os avisos de licença exigidos quando o conjunto for distribuído.
O script aceita somente localhost, arquivo até 32 MiB e envia lotes de 100,
sem imprimir o Bearer. Pode ser repetido após interrupção. O importador não baixa
URLs fornecidas em requisição, não importa todos os lugares do mundo e não possui
agendador automático. A atualização periódica é operacional: repetir com um export
regional da nova release. O cliente oficial `overturemaps==1.0.2` foi instalado
em ambiente Python isolado `.local/overture-venv`, sem dependência nova na API.

Fontes oficiais:
[acesso e export GeoJSON](https://docs.overturemaps.org/getting-data/),
[esquema Places atual](https://docs.overturemaps.org/guides/places/),
[atribuição/licenças](https://docs.overturemaps.org/attribution/).
Em **23/10/2026**, a release `2026-10-21.0` alimentou o PostgreSQL local com
**1.033 registros**: 966 ativos e 67 provisórios para revisão de possível duplicidade.
A repetição manteve os UUIDs. Os testes automatizados continuam usando fixtures
fictícias em PostgreSQL descartável. Veja a [evidência da carga](carga-overture-2026-10-23.md)
e os [avisos de fontes e licenças](licencas/overture/README.md).

## Cota, migração e limites operacionais

`JAMBU_GOOGLE_PLACES_LIMITE_DIA=100`, conforme escolha de Lucas.
Zero impede chamadas. Cada tentativa HTTP de autocomplete/detalhe reserva uma
chamada no PostgreSQL antes da rede. Erro/timeout não devolve a chamada, e reservas
não são perdidas após reinício. O dia segue America/Sao_Paulo. Isso limita
chamadas, não garante um valor de fatura; billing/SKUs continuam no provedor.
Instâncias que compartilham o banco compartilham a cota. Chamadas feitas por outros
programas/projetos fora desta API não entram nessa contagem. Não há cota de Meals.

V4 adiciona campos/índices do catálogo, revisão, idempotência de cadastro e controles
do provedor. Remove a restrição V2 de exigir Google para todo novo restaurante,
preservando registros existentes, UUIDs, FKs, sessões, fotos e snapshots.
Não editar V1–V3. Fazer backup e testar restauração antes de iniciar o JAR novo
em banco persistente. Evidência operacional em
[verificação do catálogo](verificacao-catalogo-2026-10-22.md).

Fronteiras conferidas: Bearer obrigatório; autoria vem da sessão; revisores por UUID;
DTOs rejeitam origem/situação forjadas; importação é administrativa; IDs externos
têm unicidade; cota e idempotência são atômicas; HTTP fora de transações; conteúdo
Google transitório. O protótipo interno não adiciona revisão de endereço postal,
limite de novos cadastros por usuário, confirmação da qualidade da contribuição,
processamento de GERS bridge files ou fusão administrativa. Esses pontos e a
cobertura real do catálogo devem ser revisados antes da abertura externa.
