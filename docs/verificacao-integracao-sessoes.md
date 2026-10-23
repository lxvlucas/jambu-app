# Integração do catálogo e das sessões — 23/10/2026

Integração do commit de sessões `9b3a37c` com o catálogo de `a47e8df`, preservando
o histórico por merge. A migração publicada V4 do catálogo permanece intacta;
a migração de sessões foi renumerada para V5 antes de uso em banco persistente.

## Verificação executada

Windows ARM64, JDK 25 com release 21, Maven 3.9.16 e PostgreSQL 18 descartável.
Comando executado na cópia isolada de integração:

```powershell
./scripts/verificar-postgres-local.ps1 -PostgresBin '<bin do PostgreSQL 18 instalado>' -Offline
```

Resultado: **passou**, código de saída 0.

| Verificação | Resultado |
| --- | --- |
| Maven clean/verify | 103 testes; zero falhas, erros ou ignorados; JAR gerado |
| Migração do catálogo | V1–V3 preservadas; aplicação da V4 isoladamente |
| Migração das sessões V4 → V5 | catálogo, hashes, prazos e revogação preservados; checksums V1–V4 inalterados; sem refresh para legado |
| Sessões | expiração, rotação, concorrência, replay, CSRF/origem, reautenticação, gestão e limpeza de respostas temporárias passaram |
| Smoke do JAR | cadastro e busca local, ampliação com Google desativado, Meal, reinício, refresh persistido, recuperação da mesma emissão, idempotência, logout e carga repetida passaram |
| Ambiente PowerShell | precedência, conteúdo literal, variáveis permitidas e rejeição de formato passaram |

O smoke passou a cadastrar seu próprio restaurante pelo endpoint do catálogo e
usa o acesso renovado também na repetição do cadastro após reinício. O gate nativo
não precisa mais de fixture SQL nem do cliente psql. Não foram necessárias chamadas
Google. O cluster descartável foi encerrado e removido; nenhum banco persistente
ou de desenvolvimento foi migrado.

## Limitações

- PostgreSQL 17/Docker não foi executado nesta validação; PostgreSQL 18 foi usado.
- Avisos de Mockito/Byte Buddy sobre carregamento dinâmico de agente no JDK 25
  permanecem visíveis. Não houve testes ignorados.
- A integração dos clientes Android/web continua necessária para renovação silenciosa.
- Não houve deploy nem revisão independente de segurança.
