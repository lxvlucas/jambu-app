# Verificação — posts, contas públicas e Places

Executada em 19/10/2026, após a fundação inicial. Escopo: remover a cota diária,
verificar contas sempre públicas e integrar Google Places API (New) com HTTP simulado.

## Ambiente e comando

PowerShell 7, Maven 3.9.16, JDK 25.0.4 compilando para release 21 e PostgreSQL 17
em container descartável, porta aleatória em loopback e dados em tmpfs. A imagem
e as dependências já estavam disponíveis; não houve instalação ou dependência nova.

Na raiz da API, com `JAVA_HOME` apontando para o JDK:

```powershell
./scripts/verificar.ps1 -Offline
```

Resultado: **passou**, exit code 0. `mvn -o clean verify` gerou o JAR; conclusão
Maven às 21:48:13, horário de São Paulo. Depois, o script validou o JAR por HTTP,
incluindo reinício. Relatórios regeneráveis ficam em `target/surefire-reports`.

| Classe | Testes | Falhas / erros / ignorados |
| --- | ---: | --- |
| `JambuApiApplicationTests` | 21 | 0 / 0 / 0 |
| `DominioTest` | 9 | 0 / 0 / 0 |
| `MigracaoPlacesTest` | 2 | 0 / 0 / 0 |
| `GooglePlacesAdapterTest` | 12 | 0 / 0 / 0 |
| `LimiteAutenticacaoTest` | 1 | 0 / 0 / 0 |
| **Total** | **45** | **0 / 0 / 0** |

## Evidências por comportamento

- **Sem cota — passou:** oito publicações distintas e concorrentes do mesmo autor
  no mesmo dia, exclusão e nova publicação; data passada não altera publicação,
  e o padrão continua em America/Sao_Paulo. Seis repetições concorrentes da mesma
  chave criam uma única Meal. Autorização, versão, médias e exclusão lógica passaram.
- **Contas públicas — passou:** perfil e Meals acessíveis por outro usuário autenticado
  sem seguimento; seguir cria relação imediata; cadastro/atualização rejeitam campos
  de privacidade. Contatos/credenciais não aparecem nas respostas públicas, e leitura
  de negócio continua exigindo sessão. V1 não contém privacidade ou pedidos pendentes.
- **Places HTTP — passou:** servidor local conferiu métodos, URLs, chave em header,
  FieldMask, token UUID de sessão, resposta válida e atribuições. Cobertos lugar
  inválido, tipos aceitos, resposta malformada, ID divergente, timeout, redirecionamento,
  401/403/429/5xx e configuração ausente. Erros externos não expõem corpo ou chave.
  Uma falha de montagem da URI de Autocomplete encontrada por esses testes foi corrigida.
- **Seleção e persistência — passou:** seis seleções simultâneas do mesmo placeId
  resultam em um UUID. Tipo não permitido/falha externa não criam registro. Nome,
  endereço, notas Google e atribuições não entram em entidades ou snapshots novos.
  O provedor simulado e o adaptador verificam a ausência de transação durante HTTP.
- **Indisponibilidade — passou:** sugestões/seleção falham de forma controlada;
  Meals existentes, contagem e média Jambu continuam legíveis. Publicar/repetir
  publicação e listar Meals do restaurante não fazem chamada ao provedor.
- **Histórico e idempotência — passou:** leitura e edição de Meal legada no mesmo
  restaurante funcionam; novas publicações/trocas exigem referência validada.
  Snapshots antigos com nome/endereço são desserializados sem alterar JSON, chave
  ou hash persistidos, inclusive em replay após exclusão. A resposta HTTP agora usa
  UUID/placeId; não promete conservar byte a byte o formato antigo do restaurante.
- **Migrações — passou:** aplicação em banco vazio pela suíte integrada, atualização
  V1 com dados relacionados e transição V1 com o repeatable dev antigo. Preservados
  UUIDs/FKs, usuários, Meals/Dishes, sessões, seguimentos, snapshots/hashes e nomes
  legados. V2 remove a tabela de cota e exige placeId/validação em novos registros.
  Histórico/checksum do seed anterior é preservado sem repair; o seed não roda mais.
- **JAR — passou:** saúde, cadastro/login, referência local exclusiva de teste,
  publicação, reinício, persistência, replay idempotente e logout. Places desativado
  explicitamente nesse processo, sem chamada externa.

## Fronteiras verificadas e limites

As ameaças relevantes neste recorte são aceitar restaurante inventado pelo cliente,
duplicar referências/publicações concorrentes, vazar credenciais/conteúdo externo,
permitir alterações de outra conta e perder dados na migração. Os testes acima
exercitam essas fronteiras. O destino HTTP é restrito ao Google ou simulador loopback,
redirects não são seguidos, a chave não compõe URL/resposta e o payload do cliente
não pode fornecer nome/endereço. Não há avaliação de carga ou auditoria independente.

A inspeção do PostgreSQL existente foi somente leitura: schema `public` sem tabelas
e sem histórico Flyway. O container, inicialmente parado, foi parado novamente após
a inspeção. Não houve migração, remoção de volume ou escrita nesse banco; nenhum
banco remoto foi consultado. O script removeu seu próprio container temporário.

| Verificação restante | Estado e motivo |
| --- | --- |
| Google real e credenciais | `nao-executado`: escopo exige mocks e proíbe chamadas pagas/configuração cloud |
| Aplicação V2 em banco persistente | `nao-executado`: somente bancos isolados autorizados para migração |
| Conciliação de legados de outros ambientes | `nao-executado`: depende de inventário e seleção verificada, sem associação automática por nome |
| Runtime específico Java 21 | `nao-executado`: ambiente executou JDK 25 com release 21 |
| Revisão independente | `nao-executado`: houve autorrevisão nesta sessão |
| Interfaces e fotos | `nao-aplicavel`: fora deste recorte |

Avisos existentes de instrumentação dinâmica Mockito/Byte Buddy no JDK 25 foram
emitidos durante os testes; não foram ocultados. Nenhum teste foi ignorado.
Não houve commit, push ou deploy. Configuração, atribuições e plano de aplicação
estão em [places-posts-publicos.md](places-posts-publicos.md).

Verificação final dos arquivos: `git diff --check` passou nos dois projetos; arquivos
novos também foram conferidos quanto a conflitos e espaços finais. Scripts PowerShell
passaram no parser e links locais dos documentos atualizados foram conferidos.
O SHA-256 de V1 confere com o registro anterior. Docker confirmou somente o container
de desenvolvimento parado, sem containers temporários de teste remanescentes.
