# Verificação da fundação — 19/10/2026

Ambiente: Windows, PowerShell 7, Maven 3.9.16, JDK 25.0.4 com compilação
`--release 21`, Spring Boot 4.1.1, Docker Desktop e PostgreSQL 17 (`postgres:17-bookworm`).
Não havia JDK 21 instalado disponível nesta inspeção; execução em runtime Java 21
específico ainda não foi conferida. Nenhuma dependência nova foi adicionada.

| Verificação | Estado | Evidência |
| --- | --- | --- |
| Compilação, testes e empacotamento | passou | `./scripts/verificar.ps1 -Offline` executou `mvn -o verify`; BUILD SUCCESS |
| Testes automatizados | passou | 26 testes; 0 falhas, 0 erros, 0 ignorados |
| Migração em PostgreSQL vazio | passou | V1 aplicada automaticamente no banco efêmero; JPA validou o esquema |
| Concorrência de cota | passou | 8 publicações simultâneas: 4 persistidas e 4 recusadas |
| Idempotência concorrente | passou | 6 chamadas com a mesma chave retornaram uma única Meal e consumiram uma cota |
| Concorrência de edição | passou | Mesma versão em duas edições: uma salva e outra retorna conflito |
| Snapshot de publicação | passou | Repetição preserva a resposta após edição e exclusão |
| Médias | passou | Peso igual por Meal, nenhuma reutilização da média arredondada, troca de restaurante e exclusão |
| Cota e data | passou | Virada de dia em São Paulo, refeição datada no passado, nenhuma devolução ao excluir |
| Segurança HTTP | passou | Leitura exige login, autoria conferida, contatos ausentes no perfil, campos desconhecidos rejeitados |
| Sessão | passou | Hash persistido, expiração no limite, revogação e logout |
| Dishes | passou | Notas, mínimo de um, IDs de outra Meal, reconciliação, ordem, remoção e versão |
| Timeline e seguimentos | passou | Seguir/deixar de seguir, cursor com empate de instante e autorização do link |
| JAR executável | passou | Saúde UP, cadastro/login, seed dev e criação de Meal por HTTP |
| Reinício e persistência | passou | JAR encerrado/reiniciado; sessão, Meal e idempotência persistiram; logout revogou a sessão |
| Revisão dos arquivos | passou | `git diff --check`, espaços finais dos arquivos novos, links locais e sintaxe dos scripts PowerShell |
| Limpeza do teste | passou | Consulta ao Docker confirmou ausência de containers `jambu-api-teste-*` ao final |
| Revisão independente | nao-executado | Autorrevisão nesta sessão; sem agente ou revisor independente |
| Interfaces/fotos/deploy | nao-aplicavel | Fora do recorte da fundação |

O gate usou banco descartável em porta aleatória; os containers e processos Java
temporários foram removidos/encerrados. O Compose e o volume de desenvolvimento
não foram alterados. O Docker Desktop precisou ser iniciado e o acesso ao motor
exigiu execução fora do sandbox; não houve instalação.

Relatórios: `target/surefire-reports`. Logs locais: `target/verificacao.log`,
`target/jar-primeira-inicializacao.log`, `target/jar-reinicializacao.log`.
`target` é ignorado pelo Git; os logs não são parte da documentação publicada.

Avisos: o suporte de testes do Spring/Mockito ainda carrega dinamicamente o agente
Byte Buddy no JDK 25. A execução passou, mas a configuração de instrumentação deve
ser revista antes de migrar para um JDK que proíba essa carga por padrão. Os avisos
de mapeamento da ordem dos Dishes e de senha padrão do Spring Security foram
corrigidos: posição explícita no Dish e exclusão da configuração de usuário padrão.

Sem commit, push ou deploy. Esta evidência valida a fundação interna; não representa
conclusão do MVP com fotos e interfaces nem autorização para usuários externos.
