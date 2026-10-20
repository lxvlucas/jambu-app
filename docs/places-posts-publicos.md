# Posts sem cota, contas públicas e Places — 19/10/2026

**Atualização em 20/10:** migrações aplicadas ao PostgreSQL local, backups restaurados,
carga demonstrativa e teste ao vivo de Places concluídos. Consulte o
[registro atual](persistencia-local-2026-10-20.md). O relato abaixo preserva o estado
e as limitações da entrega original de 19/10; o plano continua válido para outros bancos.

Este documento registra a atualização posterior à fundação de 19/10. Os documentos
`fundacao.md` e `verificacao-fundacao.md` ficam preservados como registros da versão
anterior. Contratos atuais estão em [contrato-api.md](contrato-api.md).

## Regras e fronteiras

Não existe entidade, repository, service ou configuração de limite diário de posts.
`PublicacoesDiarias`, seu repository e `CotaDiariaService` foram retirados; a V2 remove
a tabela. Não há contador desativado ou teto aumentado. Contagem de Meals no perfil
continua sendo informação de exibição. Limites de Dishes/review, paginação e tentativas
de autenticação permanecem.

O lock pessimista do autor foi mantido somente para serializar publicação e
idempotência. Publicação, Dishes e snapshot pertencem à mesma transação. Edição usa
versão; autorização por autor e exclusão lógica continuam. O fuso padrão permanece
America/Sao_Paulo, independente da data de refeição informada.

Não havia privacidade de conta nem solicitação de seguimento no código ou no esquema
V1. Não foi criada uma implementação temporária para removê-la. Seguir é imediato;
qualquer usuário autenticado pode ler perfis e Meals sem seguir. A timeline continua
própria/seguidas. Campos de privacidade recebidos são rejeitados como campos desconhecidos.
Contato e credenciais continuam exclusivos da própria conta e não integram respostas públicas.

## Fluxo Places

`RestauranteService` coordena busca, seleção e perfil sem transação ativa.
`ProvedorRestaurantes` define a fronteira e `GooglePlacesAdapter` faz HTTP pelo
cliente do JDK, sem dependências novas. `RestauranteLocalService` abre transações
curtas somente para banco. O adaptador também rejeita uso dentro de transação.

Autocomplete usa POST em `/v1/places:autocomplete`; seleção/perfil usam GET em
`/v1/places/{placeId}`. O token UUID Places segue no corpo de autocomplete e na query
da confirmação, nunca como Bearer. O detalhe independente não recebe esse token.
Busca tem idioma pt-BR, região brasileira e viés de 25 km do centro de São Luís;
o viés não restringe resultados ao município. As sugestões são filtradas pelos tipos
aceitos, e seleção confirma o tipo novamente. Nome/endereço do cliente não são aceitos.

Máscaras explícitas:

- Autocomplete: `suggestions.placePrediction.placeId,suggestions.placePrediction.text.text,suggestions.placePrediction.types`.
- Details: `id,displayName,formattedAddress,types,googleMapsUri,attributions`.

Não se solicitam notas, reviews ou fotos Google. Não há wildcard, importação em
massa, retry automático, log de payloads ou fallback fictício. O prazo cobre a resposta
HTTP completa e é configurável; redirects não são seguidos. A chave vai exclusivamente
no header do servidor. Endpoint alternativo é restrito a simulador em loopback.

Após validar, um upsert com unicidade de placeId cria/reutiliza o UUID local sob
concorrência. Novos registros guardam apenas UUID, placeId e instante de validação.
Uma Meal usa essa referência local; publicar, editar texto e repetir a publicação
não exigem consultar Google. Perfil usa consulta externa separada e pode sinalizar
indisponibilidade enquanto mantém contagem e nota Jambu. Listagem de Meals do
restaurante não chama o provedor.

## Armazenamento e atribuição

As políticas consultadas permitem persistir placeIds; isso não autoriza copiar
irrestritamente os demais dados. Esta implementação conserva nomes/endereços próprios
legados e não persiste novos dados externos. Snapshots novos têm somente UUID/placeId
do restaurante. Respostas externas têm `no-store`. Não há cache persistente de conteúdo
Google nem cópia desse conteúdo para logs/seeds. [Políticas Places](https://developers.google.com/maps/documentation/places/web-service/policies).

Nas interfaces futuras, exibir atribuição **Google Maps** junto às sugestões/detalhes
e também as atribuições de terceiros retornadas. Preferir os assets oficiais do logo;
texto Google Maps é permitido nos casos de espaço limitado descritos na política.
Manter as atribuições legíveis e distinguir dados Google das notas Jambu. Se exibir
esses dados sobre um mapa, seguir as condições de uso de Google Maps. As UIs não
foram implementadas nem auditadas nesta tarefa. [Regras de atribuição](https://developers.google.com/maps/documentation/places/web-service/policies#google-maps-attribution-requirements).

PlaceIds podem mudar; a documentação recomenda revisar referências com mais de
12 meses. O instante de validação ajuda na operação, mas não foi criado processo de
atualização automática ou chamada paga. Também não se vincula um restaurante por
similaridade de nome. [Place IDs](https://developers.google.com/maps/documentation/places/web-service/place-id).

## Migração V2 e dados legados

V1 foi preservada byte a byte. Seu SHA-256 é
`753AA067CB63CF5F941AB7AD3D881804460F66E0E56CB6C6E567EBFA158944D7`.
V2 remove `publicacoes_diarias`, torna nome/endereço opcionais, amplia placeId e
adiciona `places_validado_em`. Um trigger exige referência validada para INSERTs e
impede preencher nome/endereço em novos restaurantes. Não há NOT NULL global de
placeId, pois as linhas legadas precisam continuar válidas. A unicidade existente
de placeId é mantida.

Usuários, Meals, Dishes, sessões, seguimentos e idempotências não são apagados ou
reescritos. Nome/endereço legados, UUIDs e FKs continuam intactos. Dados de contagem
diária deixam de existir após aplicar V2; não há dependência funcional remanescente.

O repeatable antigo `db/dev/R__restaurantes_ficticios.sql` foi preservado, mas retirado
das localizações ativas. `ignore-migration-patterns=repeatable:missing,*:future`
permite manter seu registro/checksum em bancos dev antigos sem reexecutá-lo ou usar
repair. A exceção de migrações ausentes se aplica a repeatables; V1/V2 continuam com
validação normal. Não adicionar outros repeatables operacionais sem rever essa
política, pois sua remoção também seria ignorada. Bancos novos não recebem o seed.

Snapshots antigos são lidos por um DTO de referência compatível, que ignora os campos
de nome/endereço antigos. O snapshot persistido, a chave e o hash da requisição não
são alterados. A resposta nova remove esses campos e representa placeId ausente por
null. Os demais dados originais não são reconstruídos a partir de uma Meal editada.
Esse ajuste de formato exige que clientes consultem o detalhe de restaurante para
exibir nome/endereço; não há garantia de resposta HTTP idêntica byte a byte à versão V1.

Na inspeção desta tarefa, o container local estava parado. Foi iniciado somente
para transação SQL READ ONLY e depois parado novamente: `public` não tinha tabelas
nem histórico Flyway. Não havia tabelas de contas privadas ou pedidos para contar.
Não foi consultado banco remoto. V1 e V2 foram aplicadas **somente** em PostgreSQL
descartável de testes, incluindo cenário com dados V1 e cenário com seed dev anterior.

### Aplicação futura no banco persistente

1. Conferir o destino, parar escritas, fazer backup e validar recuperação antes da V2.
2. Conferir histórico Flyway, tabelas e origem dos dados legados. Não executar `clean`,
   apagar volume ou editar checksum de V1. Não executar SQL de fixtures de teste.
3. Levantar referências pendentes, inicialmente em leitura:

   ```sql
   SELECT count(*) AS restaurantes,
          count(*) FILTER (WHERE google_place_id IS NULL) AS sem_place_id
   FROM restaurantes;
   ```

4. Aplicar V2 em janela autorizada. A inicialização normal da aplicação executa Flyway;
   portanto, iniciar a versão nova no banco real já aplica a migração.
5. Conferir contagens de usuários/Meals/Dishes/sessões/idempotência e leitura histórica.
   Restaurantes sem placeId/validação continuam legíveis e editáveis na mesma Meal,
   mas não aceitam novas publicações/trocas. Resolver vínculos posteriormente com
   seleção verificada e revisão administrativa, preservando UUIDs; não inventar IDs.
6. Não apagar nome/endereço antigos sem avaliação de origem e uso. Se já houver
   outro UUID vinculado ao mesmo placeId, planejar a conciliação de FKs explicitamente;
   esta entrega não faz merge automático.

Não existe endpoint de cadastro manual como alternativa a Places nem endpoint
administrativo de conciliação nesta entrega. Em falha após migração, preferir correção
adiante; retornar ao binário com cota exigiria estrutura antiga, logo não é rollback
seguro sem restauração planejada. Nenhuma dessas ações foi aplicada ao banco existente.

## Configuração, testes e pendências

Habilitar explicitamente `JAMBU_GOOGLE_PLACES_HABILITADO=true` e fornecer
`JAMBU_GOOGLE_PLACES_API_KEY` no ambiente do backend quando a integração ao vivo
estiver autorizada. Manter a chave restrita ao serviço/backend no provedor e não
habilitar logging de headers HTTP. Credenciais Google, billing, termos/privacidade,
limites operacionais e teste real ficam pendentes; esta tarefa não criou conta,
habilitou faturamento nem fez chamada paga.

Fontes oficiais consultadas: [Autocomplete (New)](https://developers.google.com/maps/documentation/places/web-service/place-autocomplete)
e [Place Details (New)](https://developers.google.com/maps/documentation/places/web-service/place-details).
As máscaras selecionam somente os campos usados; não representam promessa de
gratuidade. Autorrevisão nesta sessão, sem revisão independente. Resultados ficam
em [verificacao-places.md](verificacao-places.md).
