# Jambu — checklist de desenvolvimento do MVP

Preparado em 12/10/2026; atualizado em 20/10/2026. Caixas marcadas representam
implementação e verificação no recorte descrito em cada item. Uma tarefa principal
fica aberta enquanto tiver entregas pendentes; uma caixa marcada não significa
que a funcionalidade já foi disponibilizada em um ambiente persistente ou remoto.

Atualização de 20/10/2026: persistência/migrações concluídas no PostgreSQL local (incluindo V3 de fotos e moderação),
carga demonstrativa reutilizável, Places verificado ao vivo com a chave de
demonstração (mantendo foco em São Luís no MVP) e módulos de upload/normalização de fotos 3:4 e moderação (tarefas 10 e 11) implementados e verificados.
Consulte o [README da API](../README.md) e a [verificação de 20/10](persistencia-local-2026-10-20.md).
Passaram 68 testes, build e teste do JAR, além das verificações de ambiente,
carga repetida, backups/restauração e reinício do PostgreSQL persistente. Interfaces Android/web,
operação e hospedagem ficam nas próximas etapas.

O fluxo **cadastrar → entrar → selecionar restaurante → publicar Meal com Dishes → enviar fotos 3:4 com moderação → consultar na timeline → editar ou excluir** está implementado no backend. A suíte cobre o backend em banco isolado, e a seleção real pelo Places com publicação/leitura foi conferida no banco local persistente. O MVP interno completo ainda não está concluído.

A API usa Java 21 como alvo de compilação, Spring Boot 4.1.1, Maven e PostgreSQL 17. Android Kotlin/Compose e web React/TypeScript permanecem previstos, com versões e bibliotecas auxiliares a definir. Render e Supabase são destinos previstos, sem hospedagem implementada.

Revisão de escopo em 19/10/2026: sem limite diário de Meals, contas somente públicas e restaurantes pelo Google Places API (New).

## Estado atual e próximas entregas

| Frente | Estado comprovado | O que falta |
| --- | --- | --- |
| Base Java, autenticação, perfis e seguimentos | Concluídos no backend; tarefas 2, 4 e 5 verificadas, com foto de perfil integrada na tarefa 10 | Operação de contas entra nas etapas posteriores |
| Meals, edição/exclusão, timeline e link | Concluídos no backend; tarefas 7, 8, 9 e fotos de Dishes (tarefa 10) verificadas | Interfaces; o link atual abre JSON autenticado |
| Persistência e migrações | V1/V2/V3 aplicadas no volume local; carga reutilizável, reinício e backups/restauração verificados | Configuração de outros ambientes na tarefa 15 |
| Google Places | Sugestões, seleção e detalhe verificados ao vivo com chave demonstrativa (foco em São Luís no MVP); mocks mantidos nos testes | Credencial/configuração própria quando houver outro ambiente |
| Qualidade da API | 68 testes, build, smoke, ambiente local e repetição da carga passaram em 20/10 | Revisão independente, runtime Java 21 e testes do MVP com as interfaces |
| Fotos e moderação | Tarefas 10 e 11 concluídas e verificadas no backend (padrão 3:4, EXIF/GPS, storage privado local/Supabase, fila persistente, Rekognition HTTP simulado e revisão administrativa) | Credenciais reais de storage/moderação em ambiente hospedado na tarefa 15 |
| Android e web | Não implementados | Tarefas 12 e 13 |
| Hospedagem e operação | Não implementadas | Tarefas 15 e 16, conforme o estágio interno ou externo |

Próxima entrega solicitada: implementar interfaces Android (tarefa 12) e web (tarefa 13)
consumindo os contratos da API, e depois concluir integração e hospedagem interna.

## Etapa 1 — preparar a API e o banco

- [x] **1. Fechar as regras necessárias para a primeira implementação**
  - [x] Confirmar review e modalidade opcionais, como proposto no diagrama.
  - [x] Manter notas dos Dishes entre 0,5 e 5, em passos de 0,5; médias exibidas em decimal.
  - [x] Definir limites configuráveis para Dishes por Meal e review: 20 Dishes e 2.000 caracteres iniciais, com limites técnicos de 100 e 10.000.
  - [x] Definir fotos 3:4, até 1080 × 1440 px, JPEG/sRGB com qualidade inicial 85% e até 1 MiB; entrada JPEG/PNG até 8 MiB. Recorte, mínimo, miniatura e limites de decodificação estão no [contrato de fotos](fotos-padrao-mvp.md); implementação nas tarefas 10 e 11.
  - [x] Definir quais consultas podem ser feitas sem login: saúde é pública; consultas de negócio exigem sessão. Perfis públicos não expõem contato ou credenciais.
  - [x] Manter fora deste MVP: assinaturas, Top 4, anúncios, gastos, múltiplas fotos por Dish, comentários, curtidas e repostagem interna.

- [x] **2. Criar a base do projeto Java**
  - [x] Escolher versões compatíveis de Java/Spring Boot e um gerenciador de build, Maven ou Gradle.
  - [x] Criar a aplicação com HTTP, validação, acesso ao PostgreSQL e suporte à autenticação.
  - [x] Organizar usuários, seguimentos, restaurantes e Meals por funcionalidade, com controllers, serviços, persistência e DTOs separados; fotos entram na etapa 3.
  - [x] Configurar ambientes de desenvolvimento e teste, com segredos fora do Git.
  - [x] Padronizar erros da API, incluindo campos inválidos, falta de autenticação, falta de permissão e recurso inexistente.
  - [x] Registrar os comandos reais de iniciar, testar e gerar o build no README.
  - [x] **Concluir quando:** a API inicia localmente e responde a uma consulta simples de saúde.

  Referência: [organização de código no Spring Boot](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).

- [x] **3. Implementar persistência e migrações**
  - [x] Escolher a ferramenta de migrações e configurar um PostgreSQL de desenvolvimento/teste.
  - [x] Criar tabelas para Usuario, Seguimento, Restaurante, Meal e Dish.
  - [x] Definir identificadores, chaves estrangeiras, campos obrigatórios e restrições de unicidade.
  - [x] Garantir unicidade de username, e-mail e par de seguimento; definir normalização de username/e-mail.
  - [x] Criar índices para buscar usuários/restaurantes e listar Meals por autor, restaurante e data.
  - [x] Testar migrações em banco vazio e atualização de V1, inclusive seed dev anterior, preservando dados relacionados e idempotência.
  - [x] Aplicar V1/V2 no PostgreSQL persistente local, com backup anterior restaurado em banco isolado. Esquema e histórico Flyway conferidos; volume existente preservado.
  - [x] Completar a carga reutilizável por scripts/carregar-demonstracao.ps1: duas contas, um seguimento, três Meals e quatro Dishes em restaurante realmente selecionado. Repetição mantém os IDs e não duplica posts; nenhum restaurante fictício inserido no banco persistente.
  - [x] Fazer backup após a carga e validar restauração em PostgreSQL isolado, conferindo esquema e contagens.
  - [x] **Concluir quando:** migrações aplicadas no banco local persistente e registros/idempotência mantidos após reiniciar PostgreSQL e API. Leitura e média Jambu também verificadas com Google desativado.

## Etapa 2 — completar o fluxo principal sem fotos

- [x] **4. Implementar cadastro, login e sessão**
  - [x] Escolher uma estratégia de identidade: autenticação na API ou provedor externo. Implementar uma delas, evitando dois sistemas de senha.
  - [x] Cadastrar username, e-mail, celular e senha; validar obrigatoriedade e duplicidades.
  - [x] Se a API gerenciar senhas, armazenar somente hash adequado; se usar provedor, guardar a referência da identidade.
  - [x] Implementar login, identificação do usuário autenticado, expiração da sessão e logout conforme a estratégia escolhida.
  - [x] Proteger operações de escrita e limitar tentativas de login/cadastro.
  - [x] Usar DTOs que nunca retornem senha/hash; obter o autor pelo usuário autenticado, não por um ID escolhido pelo cliente.
  - [x] **Concluir quando:** dois usuários conseguem entrar e uma requisição sem sessão não consegue publicar.

- [x] **5. Implementar perfis e seguimentos**
  - [x] Verificar ausência de conta privada/configuração de visibilidade e de solicitações de seguimento; seguir é imediato.
  - [x] Testar consulta de perfil e Meals por usuário autenticado que não segue o autor, sem expor contatos privados.
  - [x] Consultar perfil público com username, contadores e Meals paginadas. A foto de perfil foi integrada na tarefa 10.
  - [x] Consultar/editar os dados da própria conta; separar os campos privados da resposta do perfil público.
  - [x] Buscar usuários para seguir.
  - [x] Seguir e deixar de seguir; impedir auto-seguimento e relações duplicadas.
  - [x] Listar seguidores e contas seguidas com paginação.
  - [x] **Concluir quando:** um usuário segue outro, vê a relação no perfil e consegue desfazê-la.

- [x] **6. Alimentar restaurantes pelo Google Places**
  - [x] Implementar Autocomplete (New) e Place Details (New) no backend, com token de sessão, FieldMask, chave de ambiente e timeout.
  - [x] Validar placeId/tipos no servidor; selecionar cria/reutiliza UUID interno com placeId único, inclusive em concorrência.
  - [x] Trocar a fonte curada por Places; restaurante do ambiente local selecionado ao vivo. Referências fictícias permanecem exclusivas dos testes isolados.
  - [x] Retornar perfil do restaurante sem conta de usuário ou proprietário.
  - [x] Retornar as Meals do restaurante com paginação.
  - [x] Calcular a nota como média das overall das Meals não excluídas, com o mesmo peso por Meal; sem avaliações, retornar nota ausente.
  - [x] Tratar falhas do Google preservando Meals/dados próprios, sem inventar restaurante como fallback.
  - [x] Conferir armazenamento e atribuições; manter novos dados externos fora de entidades e snapshots de idempotência. UIs futuras devem exibir as atribuições retornadas.
  - [x] Criar/testar migração de legados sem perder UUIDs/FKs; não exigir NOT NULL antes de resolver os vínculos existentes.
  - [x] Concluir migrações da tarefa 3 e conferir vínculos: banco inicialmente vazio, sem legados a conciliar; restaurante atual tem placeId/validação. Outros ambientes exigem inventário próprio.
  - [x] Testar integração com servidor HTTP simulado, incluindo erros, timeout, máscaras, token e concorrência.
  - [x] Configurar chave demonstrativa em .env.local, ignorado pelo Git, e testar sugestões, seleção e detalhe pelo backend. Quatro chamadas reais nesta rodada, sem alterar billing; nenhuma promessa de gratuidade ou validação de produção.
  - [x] **Concluir quando:** seleção real referencia restaurante local único e suas Meals/notas Jambu continuam consultáveis. A chave demonstrativa foi suficiente para o ambiente local; outros ambientes terão configuração própria.

- [x] **7. Implementar publicação de Meals e Dishes**
  - [x] Definir os DTOs de criação: restaurante, lista de Dishes com nome/nota, review, comeriaNovamente, modalidade e data da refeição.
  - [x] Validar restaurante local previamente selecionado/validado pelo Places, pelo menos um Dish, nomes preenchidos e notas nos passos permitidos; publicar não chama Google.
  - [x] Aceitar PRESENCIAL, DELIVERY e RETIRADA; não aceitar valores desconhecidos.
  - [x] Calcular overall no backend; não aceitar uma média arbitrária enviada pelo cliente.
  - [x] Se a data não vier preenchida, usar o dia da publicação em America/Sao_Paulo; manter o instante de publicação separado.
  - [x] Salvar Meal e Dishes na mesma transação.
  - [x] Implementar detalhe da Meal e uma proteção contra duplicação em novas tentativas da mesma publicação.
  - [x] **Concluir quando:** uma Meal com vários Dishes retorna os dados persistidos e a média correta.

- [x] **8. Adequar publicação sem limite diário e manter edição/exclusão**
  - [x] Remover o controle diário da API e suas dependências/configurações; criar e testar V2 para retirar a tabela obsoleta. Aplicação em banco persistente consta na tarefa 6.
  - [x] Verificar mais de quatro publicações distintas no mesmo dia para o mesmo autor, mantendo idempotência em repetições concorrentes.
  - [x] Permitir editar/excluir somente Meals do próprio autor.
  - [x] Permitir editar os Dishes, mantendo pelo menos um; editar não deve criar uma nova Meal.
  - [x] Aplicar exclusão lógica e retirar a Meal das consultas e médias. Retenção/limpeza de arquivos implementada na tarefa 10.
  - [x] Recalcular/refletir médias após alteração de notas, restaurante ou exclusão.
  - [x] **Concluir quando:** novas Meals não são recusadas pela quantidade publicada no dia, repetições não duplicam posts e um usuário não altera a Meal de outro.

- [x] **9. Implementar timeline e compartilhamento**
  - [x] Listar Meals próprias e de contas seguidas, excluindo as removidas.
  - [x] Ordenar por publicação mais recente, com desempate por identificador e paginação estável.
  - [x] Definir respostas enxutas com autor, referência do restaurante (UUID/placeId), Dishes e overall. Fotos aprovadas integradas na tarefa 10; nome/endereço ficam no detalhe do restaurante.
  - [x] Gerar link para detalhe da Meal, respeitando a decisão sobre leitura sem login.
  - [x] Disponibilizar link de posts próprios e de contas seguidas para o detalhe JSON autenticado, sem entidade de repost. Botões de compartilhamento pertencem às interfaces ainda pendentes.
  - [x] **Concluir quando:** seguir/deixar de seguir muda o conteúdo da timeline e o link abre a Meal conforme a regra de acesso.

## Etapa 3 — adicionar fotos com moderação

- [x] **10. Implementar upload e leitura protegida de fotos**
  - [x] Criar persistência de Foto, FotoDish, FotoPerfil e registros de moderação conforme o diagrama.
  - [x] Configurar armazenamento privado; guardar no banco a referência do arquivo, não os bytes da foto.
  - [x] Validar conteúdo real, formato, tamanho e dimensões da imagem; tratar metadados de localização antes de disponibilizá-la.
  - [x] Autorizar upload somente para o próprio perfil ou Dish de uma Meal própria.
  - [x] Permitir uma foto atual por Dish e uma foto de perfil; substituir cria um novo arquivo/registro para nova análise.
  - [x] Criar a foto como PENDENTE; impedir leitura pública por qualquer caminho antes de APROVADA.
  - [x] Integrar somente fotos aprovadas às respostas de perfil, Dish e timeline.
  - [x] Definir remoção e limpeza de arquivos abandonados/substituídos, respeitando a retenção escolhida.
  - [x] **Concluir quando:** o autor envia uma foto, mas ela ainda não aparece para outros usuários nem pode ser acessada por URL pública.

  Buckets públicos permitem leitura por quem conhece a URL; para este fluxo, começar com bucket privado e controlar a entrega da imagem pela API. [Modelos de acesso do Supabase Storage](https://supabase.com/docs/guides/storage/buckets/fundamentals).

- [x] **11. Integrar a moderação automática**
  - [x] Escolher um serviço pronto de análise; validar custo, região e credenciais. Adaptador HTTP para Amazon Rekognition (`DetectModerationLabels`) implementado com configuração externa; contratação/credencial real fica para o ambiente hospedado.
  - [x] Definir categorias proibidas e critérios de aprovação, rejeição e revisão; testar com imagens adequadas e casos ambíguos.
  - [x] Criar uma tarefa persistente de análise junto ao registro de upload concluído. Uma tabela de tarefas no PostgreSQL é uma opção inicial.
  - [x] Processar em segundo plano, com tentativas limitadas e retomada após reinício; evitar depender apenas de uma tarefa em memória.
  - [x] Registrar resultado, motivo, provedor e versão da política; liberar somente a versão do arquivo realmente analisada.
  - [x] Em timeout/erro, manter a foto indisponível e permitir nova tentativa; nunca interpretar falha como aprovação.
  - [x] Aplicar a análise também ao avatar e a contas confiáveis; texto/notas da Meal continuam disponíveis durante a análise.
  - [x] Disponibilizar uma operação administrativa protegida para revisar, aprovar/rejeitar e remover fotos, com auditoria.
  - [x] **Concluir quando:** aprovação libera a foto; rejeição, falha, reinício e substituição não liberam uma imagem sem análise.

  Apoio: [exemplo oficial do Rekognition para Java](https://docs.aws.amazon.com/rekognition/latest/dg/procedure-moderate-images.html).

## Etapa 4 — construir Android e web

As duas interfaces consomem a mesma API. Começar as telas assim que os contratos da etapa 2 estiverem definidos; usar dados fictícios enquanto algum endpoint estiver em implementação.

- [ ] **12. Criar o aplicativo Android**
  - [ ] Configurar Kotlin/Jetpack Compose e escolher bibliotecas de navegação, HTTP e armazenamento de sessão.
  - [ ] Implementar cadastro, login, logout e tratamento de sessão expirada.
  - [ ] Implementar timeline, detalhe da Meal, perfis de usuário e restaurante, busca e seguir/deixar de seguir.
  - [ ] Implementar formulário da Meal com adição/remoção de Dishes, notas em meias estrelas, review, modalidade, data e booleano.
  - [ ] Implementar edição/exclusão, seleção/envio de fotos e estados pendente/rejeitada/aprovada.
  - [ ] Compartilhar o link da Meal usando o mecanismo de compartilhamento do Android.
  - [ ] Tratar carregamento, lista vazia e erro de rede; impedir envios repetidos por toque.
  - [ ] **Concluir quando:** o fluxo principal e o upload funcionam em um aparelho Android real conectado à API.

- [ ] **13. Criar a interface web**
  - [ ] Configurar React/TypeScript e escolher ferramenta de build, navegação e cliente HTTP.
  - [ ] Implementar os mesmos fluxos essenciais do Android: autenticação, timeline, perfis, seguir e publicar/editar/excluir Meal.
  - [ ] Implementar upload de fotos e apresentação dos estados de moderação.
  - [ ] Criar uma rota de detalhe da Meal que funcione ao abrir diretamente um link; oferecer copiar link como opção de compartilhamento.
  - [ ] Adaptar telas para celular e desktop, com navegação por teclado e campos identificados.
  - [ ] Configurar sessão e integração com a API conforme a estratégia de identidade; tratar expiração e falhas de rede.
  - [ ] **Concluir quando:** o fluxo principal funciona no navegador e os dados criados no Android aparecem na web, e vice-versa.

## Etapa 5 — verificar e preparar o ambiente de testes

- [ ] **14. Verificar as regras e a integração do MVP**
  - [x] Testar notas inválidas, Meal sem Dishes e remoção do último Dish.
  - [x] Testar médias decimais e peso por Meal: uma Meal com um Dish de nota 5 e outra com três Dishes de nota 1 produzem nota 3 no restaurante, não 2.
  - [x] Testar edição/exclusão e troca de restaurante refletindo corretamente nas médias.
  - [x] Testar publicações acima do antigo teto, idempotência concorrente e data da refeição omitida ou informada no passado.
  - [x] Testar autorização entre usuários, ausência de campos privados nas respostas públicas, seguimento imediato e rejeição de configuração de conta privada.
  - [x] Testar versão de edição, concorrência e compatibilidade de snapshots antigos sem invalidar chaves/hashes persistidos.
  - [x] Testar Places com HTTP simulado: seleção concorrente, tipos, token, máscaras, timeout, falhas e ausência de conteúdo externo nos novos registros/snapshots.
  - [x] Conferir Places ao vivo, separadamente da suíte: sugestões, seleção e detalhe, com a chave demonstrativa.
  - [x] Testar o JAR por HTTP, incluindo reinício, persistência, idempotência e logout.
  - [x] Testar carga demonstrativa repetida, precedência/leitura literal do ambiente, restauração de backups e reinício do banco persistente.
  - [x] Documentar o contrato da API e executar testes/build: 68 testes, zero falhas/erros/ignorados; evidências em [verificação de 20/10](persistencia-local-2026-10-20.md).
  - [ ] Verificar execução no runtime Java 21. A execução registrada usou JDK 25.0.4 compilando para release 21; avisos Mockito/Byte Buddy estão documentados.
  - [ ] Realizar revisão independente antes da liberação para usuários externos; a implementação recebeu autorrevisão.
  - [x] Testar acesso administrativo restrito quando o módulo da tarefa 11 existir.
  - [x] Testar fotos pendentes/rejeitadas, acesso direto ao arquivo, falha do provedor, substituição e retomada de tarefas.
  - [ ] Executar um fluxo completo entre API, banco, Android e web; testar novas tentativas após queda de rede sem duplicar publicação.
  - [ ] Executar testes/builds de Android e web e registrar resultados e limitações.

  Resultados atuais executados em 20/10/2026, com 68 testes JUnit e verificações
  locais adicionais. Testes com Android e web dependem das tarefas 12 e 13.

- [ ] **15. Hospedar para testes internos**
  - [x] Gerar o JAR executável da API e verificar sua inicialização local; artefato em target/jambu-api-0.0.1-SNAPSHOT.jar no projeto da API.
  - [ ] Preparar o empacotamento para o destino de hospedagem e configurar banco, arquivos, URLs, HTTPS e segredos nos ambientes previstos.
  - [ ] Configurar acesso web à API e as origens CORS necessárias.
  - [ ] Validar conexão e limites de memória, conexões e armazenamento; conferir preços/cotas atuais dos serviços escolhidos.
  - [ ] Verificar como o processamento de moderação retoma após reinício ou suspensão da hospedagem; escolher execução compatível com o ambiente.
  - [ ] Configurar logs sem senhas/tokens, identificação de erros e acompanhamento de falhas de moderação.
  - [x] Validar backup e restauração do PostgreSQL local antes/depois da migração e carga demonstrativa.
  - [ ] Definir retenção, cópia externa e recuperação do banco no ambiente hospedado, além da recuperação/retenção dos arquivos de fotos.
  - [ ] Distribuir uma versão de teste Android e disponibilizar a web para os founders.
  - [ ] **Concluir quando:** os founders completam o fluxo nas duas interfaces, com dados persistentes e problemas registrados para correção.

## Antes de convidar usuários externos ou publicar na loja

- [ ] **16. Preparar a operação básica da rede social**
  - [ ] Implementar denúncias de Meals/fotos e usuários, com registro e tratamento administrativo.
  - [ ] Implementar bloqueio/desbloqueio de usuários; definir efeitos na timeline, seguimento e interações. Bloqueio não transforma um perfil público em privado.
  - [ ] Atualizar o modelo de classes para denúncias e bloqueios antes de implementar essas relações; o diagrama atual do MVP não as detalha.
  - [ ] Definir regras de conteúdo, termos aceitos antes da publicação e informações de privacidade compatíveis com o funcionamento real.
  - [ ] Implementar recuperação de acesso, excluir conta e tratamento dos dados associados conforme a estratégia escolhida.
  - [ ] Definir quem recebe denúncias, quem pode remover conteúdo/restringir contas e como registrar as ações.
  - [ ] Corrigir falhas impeditivas encontradas nos testes internos e realizar a revisão prevista antes do alpha externo.
  - [ ] Conferir requisitos atuais de distribuição/classificação antes de publicar; publicar na loja é uma etapa posterior ao ambiente interno.

  Para redes sociais públicas na Google Play, a política exige moderação contínua, termos e mecanismos de denúncia e bloqueio. O filtro automático de fotos é apenas parte desse trabalho. [Política de conteúdo gerado pelo usuário](https://support.google.com/googleplay/android-developer/answer/9876937?hl=pt-BR).

**Marco alcançado:** backend do MVP (tarefas 1 a 11) funcionando pela API, com PostgreSQL local persistente (V1/V2/V3), Places validado ao vivo (recorte São Luís no MVP), carga demonstrativa reutilizável e fotos 3:4 com storage privado e moderação automática/humana testadas. **Próximo marco:** interfaces Android (tarefa 12) e web (tarefa 13). **MVP interno completo:** permanece pendente de interfaces Android/web, integração ponta a ponta e hospedagem interna. A preparação para usuários externos vem depois desse marco.
