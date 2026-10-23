# Verificação das sessões persistentes — 23/10/2026

## Ambiente e resultado

Windows ARM64, Microsoft JDK 25.0.3 (compilação release 21), Maven 3.9.16 e
PostgreSQL 18 descartável em porta aleatória de loopback. Sem dependências novas no
pom.xml. Nenhum banco persistente/de desenvolvimento foi utilizado ou migrado.

Comando, executado na raiz da API:

```powershell
./scripts/verificar-postgres-local.ps1 -PostgresBin '<bin do PostgreSQL 18 instalado>' -Offline
```

Resultado final: **passou**, exit code 0.

| Verificação | Resultado |
| --- | --- |
| Maven clean/verify | passou: 83 testes, zero falhas, erros ou ignorados; JAR gerado |
| Migração V3 → V4 | passou: tokens, prazos e revogação preservados; sem refresh para legado |
| Expiração e renovação | passou: 15 minutos, 30 dias e limite original de 180 dias |
| Concorrência no PostgreSQL | passou: repetição recupera a mesma emissão; sem duas cadeias |
| Replay e revogação confirmada | passou: nova credencial deixa de funcionar; outra sessão permanece válida |
| Web | passou: cookie protegido, refresh ausente do JSON, CSRF, origem e canal distintos |
| Reautenticação | passou: troca de e-mail exige confirmação recente; limite próprio de tentativas |
| Logout e gestão | passou: acesso expirado, JSON/formulário vazio, sessões próprias e logout de todos |
| Cifra da resposta temporária | passou: IV distinto, vínculo ao token/operação e rejeição de adulteração |
| Limpeza da resposta temporária | passou: cifra expirada removida e replay continua detectável |
| Ambiente PowerShell | passou: precedência, conteúdo literal, porta e formato |
| Smoke do JAR | passou: login, Meal, reinício, refresh persistido, repetição da renovação, idempotência, logout e carga repetida |
| git diff --check | passou |

O smoke desativa integrações externas; nenhuma chamada Google foi necessária.
O cluster do gate é encerrado e removido no finally. A chave local de autenticação
foi gerada sem exibição e .env.local permanece ignorado pelo Git.

## Limitações e tentativas anteriores

- O gate habitual com Docker/PostgreSQL 17 ficou **nao-executado**: Docker Desktop
  não disponibilizou o engine Linux. A alternativa equivalente acima passou com
  PostgreSQL 18; não afirma validação específica em PostgreSQL 17.
- A primeira execução offline falhou por cache incompleto do plugin Maven clean.
  Uma execução normal recuperou os artefatos já previstos; a execução final offline passou.
- O smoke revelou incompatibilidade do logout com corpo vazio e tipo formulário.
  A regressão foi reproduzida em teste e corrigida; também foi preservado JSON vazio.
- Mockito/Byte Buddy emitem aviso de carregamento dinâmico de agente no JDK 25.
  O aviso permanece visível e não foi suprimido. Não houve testes ignorados.
- Autorrevisão realizada; não houve revisão independente de segurança.
- Interface de dispositivos, renovação silenciosa e armazenamento no Android/web
  ainda dependem da integração dos clientes. A origem web real deve ser configurada.
- Não houve commit, push ou deploy. V4 será aplicada pelo Flyway quando a nova API
  iniciar no ambiente escolhido; o binário anterior não é compatível com o schema V4.

Contrato e decisões de segurança: [sessões persistentes](sessoes-persistentes.md).
