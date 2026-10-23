# Sessões persistentes

Cada login cria uma sessão por instalação/navegador, com tokens opacos distintos de
acesso e renovação. PostgreSQL é a autoridade; não há JWT, Redis nem HttpSession.

## Prazos

| Controle | Padrão |
| --- | --- |
| Acesso | 15 minutos |
| Inatividade sem renovação | 30 dias |
| Limite absoluto desde login original | 180 dias |
| Repetição de uma renovação | 30 segundos |
| Autenticação recente para trocar e-mail | 10 minutos |

Toda emissão respeita o menor limite aplicável. Renovar não altera a criação nem a
última confirmação de senha. O cliente renova ao abrir/usar a aplicação; tarefas em
segundo plano não devem manter sessões de usuários ausentes. `ultimaAtividadeEm`
representa login/renovação, não cada leitura do feed.

## Configuração

Execute `./scripts/configurar-autenticacao.ps1` uma vez no ambiente local. O comando
cria `JAMBU_AUTH_CHAVE_REPETICAO` em `.env.local`, preservando configuração existente
e sem exibir o segredo. Essa chave de 32 bytes em Base64 precisa ser estável entre
reinícios e compartilhada pelas instâncias da mesma implantação. Em produção,
forneça pelo gerenciador de segredos; não versione nem use a chave pública dos testes.
A API falha na inicialização se a chave estiver ausente ou tiver formato inválido.

`JAMBU_AUTH_ORIGEM_WEB` habilita as rotas de navegador e deve ser a origem exata,
como `https://jambu.example`, sem caminho ou barra final. Use proxy para servir web e
API sob a mesma origem; CORS entre sites diferentes não está habilitado. Cookies
usam Secure por padrão. Somente desenvolvimento em localhost/127.0.0.1 pode usar
origem HTTP com `JAMBU_AUTH_COOKIE_SEGURO=false`. TLS deve proteger toda implantação
acessível por rede. Não confie em cabeçalhos de proxy de origens arbitrárias.

Os prazos podem ser ajustados por `JAMBU_AUTH_DURACAO_ACESSO`,
`JAMBU_AUTH_INATIVIDADE` e `JAMBU_AUTH_LIMITE_ABSOLUTO`, em duração ISO-8601.
`jambu.duracao-sessao` foi substituída por essas propriedades.

## Cliente nativo/API

`POST /api/auth/login` recebe o identificador e senha existentes e retorna:

```json
{
  "token": "TOKEN_DE_ACESSO",
  "expiraEm": "2026-10-23T12:15:00Z",
  "refreshToken": "TOKEN_DE_RENOVACAO",
  "sessaoId": "UUID_DA_SESSAO",
  "expiraSessaoEm": "2026-11-22T12:00:00Z"
}
```

`token` continua sendo enviado em `Authorization: Bearer`. Guarde o refresh em
armazenamento protegido pela plataforma; nunca guarde a senha para renovar.

`POST /api/auth/refresh` recebe `{"refreshToken":"...","operacaoId":"UUID"}`.
Não precisa de Bearer; um Bearer expirado no header não impede a renovação. A resposta
tem o mesmo contrato do login. O refresh não autentica rotas de negócio e o access
token não pode ser usado para renovar. As rotas nativas não leem cookies de refresh.

`POST /api/auth/logout` aceita Bearer válido, mantendo compatibilidade, ou corpo
`{"refreshToken":"..."}`, que permite sair mesmo após expirar o acesso.

## Navegador

1. Faça `GET /api/auth/web/csrf` com cookies habilitados. A resposta fornece `token`
   e `headerName`; o navegador recebe o cookie CSRF separado.
2. Use esse header em `POST /api/auth/web/login`, com identificador/senha. O servidor
   também confere `Origin` exatamente contra a configuração.
3. O corpo retorna o acesso e as datas, sem refresh. O cookie `jambu_refresh` é
   persistente, HttpOnly, Secure, SameSite=Strict, sem Domain e com Path=/api/auth/web.
4. Mantenha o acesso em memória. Depois de recarregar a página, obtenha CSRF e faça
   `POST /api/auth/web/refresh` com `{"operacaoId":"UUID"}` e cookies habilitados.
5. `POST /api/auth/web/logout` revoga a sessão e expira o cookie. Gestão de sessões e
   demais rotas usam o access token no Bearer, sem autenticar por cookie.

Refresh de WEB só é aceito no canal WEB; refresh NATIVO só no canal NATIVO. O token
CSRF é proteção distinta da credencial de renovação. HttpOnly não elimina XSS;
conteúdo e scripts do aplicativo ainda precisam de proteção própria.

## Concorrência e repetição

O cliente gera um UUID por operação e persiste temporariamente esse UUID junto da
tentativa até receber a resposta. Renovações devem ser únicas por sessão, incluindo
coordenação entre abas. Repetir após erro de rede usa o mesmo refresh e UUID.

A API bloqueia a sessão na transação, relê o refresh e consome cada token uma vez.
Durante 30 segundos, a mesma operação pode recuperar a mesma resposta, desde que o
sucessor ainda não tenha sido consumido. A resposta temporária fica cifrada com
AES-256-GCM, IV aleatório e vínculo ao token/operação. A limpeza remove o texto
cifrado vencido a cada cinco segundos; hashes/histórico continuam para detectar replay.

Outro UUID usando o token consumido nessa janela recebe 409 `renovacao_concorrente`,
sem novas credenciais e sem revogação imediata. O cliente aguarda o resultado da
operação original; não deve tentar indefinidamente com novos UUIDs. Reuso fora da
janela, ou após consumir o sucessor, revoga a sessão. A revogação é confirmada no
banco antes de devolver 401. Essa tolerância é limitada: não distingue com certeza
um atacante de uma repetição legítima e não garante recuperação após a janela.

Rotacionar a chave de cifra durante uma operação pode impedir recuperar aquela
resposta e gerar 503; faça rotação coordenada considerando a janela curta. O banco
guarda tokens somente como hashes, exceto a resposta cifrada temporária descrita acima.

## Gestão, erros e segurança

| Endpoint | Resultado |
| --- | --- |
| GET /api/auth/sessoes | Sessões próprias ativas, descrição, datas e `atual`; sem credenciais |
| DELETE /api/auth/sessoes/{id} | Revoga a sessão própria; ID de outra conta recebe 404 |
| POST /api/auth/logout-todos | Revoga todas, inclusive a atual |
| POST /api/auth/reautenticar | Recebe senha; confirma identidade por 10 minutos |

Trocar e-mail por PUT /api/usuarios/me exige autenticação recente. Mudar somente
username/celular preserva o fluxo existente. Recuperação e troca de senha não foram
criadas neste recorte; quando existirem deverão integrar a revogação de sessões.

401 `acesso_expirado` permite renovar. 401 `acesso_invalido` permite ao cliente tentar
seu refresh protegido; sem refresh válido será preciso entrar. `sessao_encerrada`,
`refresh_invalido` e `refresh_expirado` indicam falha definitiva da credencial usada.
403 `reautenticacao_necessaria` pede confirmação de senha, sem encerrar a sessão.
409 `renovacao_concorrente` é recuperável pela coordenação da operação original.
429 inclui Retry-After; 5xx/timeout não devem apagar o login. Respostas de autenticação
e dados de sessões usam no-store. Logs registram eventos por ID, sem tokens ou senhas.

Há limites por IP de login/cadastro, renovação/logout e confirmação de senha, em
memória por instância. Múltiplas instâncias precisam de limite compartilhado no
gateway antes de exposição pública. Metadados do navegador são informativos,
não provam identidade física do dispositivo; mudança de IP não encerra a sessão.

## Migração e entrega

V4 copia os hashes anteriores para tokens_acesso e mantém exatamente a expiração e
revogação originais. Sessões LEGADO não recebem refresh; novo login com senha cria
a sessão renovável. A migração remove token_hash de sessoes: o binário antigo não é
compatível com o schema novo. Faça backup e implantação coordenada; recuperação deve
ser planejada como correção adiante ou restauração controlada, sem downgrade automático.

Os clientes atuais precisam implementar o contrato de refresh para aproveitar a
persistência; a API sozinha não renova tokens no celular/navegador. Não houve deploy
nem migração do banco persistente durante o desenvolvimento.

## Verificação reproduzível

O gate habitual continua `./scripts/verificar.ps1 -Offline` com Docker/PostgreSQL 17.
Alternativa para Windows com PostgreSQL já instalado:

```powershell
./scripts/verificar-postgres-local.ps1 -PostgresBin '<pasta bin do PostgreSQL>' -Offline
```

Esse gate cria cluster isolado em .local, usa porta aleatória em loopback e senha
efêmera, executa clean/verify, checks do ambiente e smoke do JAR com reinício, depois
encerra e remove somente o cluster criado. Não usa o banco de desenvolvimento.

Autorrevisão: fronteiras de acesso/refresh, CSRF/origem, revogação confirmada, prazo
absoluto e isolamento por conta. Não equivale a revisão independente de segurança.

Evidência executada: [verificação de sessões](verificacao-sessoes.md).
