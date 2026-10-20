# Fundação: decisões e fronteiras

Implementação local de 19/10/2026. O código anterior disponível era somente o
bootstrap Spring Boot. A documentação de 17/10 mencionava outra cópia com fundação;
ela não foi usada como prova de comportamento nem copiada para este projeto.

## Escopo e organização

As vistas 1 e 2 do diagrama Java orientam este recorte. A vista de fotos é uma
extensão futura: não foram criadas integrações vazias ou endpoints que prometam
moderação sem realizá-la. As subpastas de modelagem não serviram de fonte.

Monólito organizado por funcionalidade, services concretos com injeção por
construtor e fronteiras transacionais explícitas. Sem novas dependências, broker,
microserviços ou interfaces genéricas artificiais. O banco permanece PostgreSQL
e o Compose existente foi preservado.

`Usuario` não carrega coleções de todo o histórico. Associações são LAZY e
`open-in-view` está desligado. DTOs são montados dentro das transações. Timeline
pagina IDs primeiro e busca as relações em uma segunda consulta; transação de
leitura repetível mantém consistência entre as duas consultas. Busca de restaurantes
agrega suas avaliações em lote. Índices cobrem relações, unicidade e listagens de
Meals ativas por autor, restaurante e instante; busca textual por trecho ainda
depende de varredura, aceitável para o catálogo interno e a reavaliar com volume real.

Meal é a raiz do agregado: Dishes têm cascade/orphan removal, vínculo bidirecional
e posição persistida. Não há exclusão em cascata de usuários ou restaurantes.
Médias são derivadas e não criam valores duplicados sujeitos a inconsistência.

## Concorrência

Nova publicação bloqueia o autor com lock pessimista, consulta idempotência,
valida o restaurante, consome cota, persiste Meal/Dishes e grava o snapshot da
resposta na mesma transação. A primeira cota do dia também é protegida pelo lock.
Falha desfaz o conjunto. Sessões e registros de idempotência são compartilhados
entre instâncias por PostgreSQL.

Edição usa `@Version` e versão enviada pelo cliente. IDs de Dishes são reconciliados
preservando identidade e ordem. A Meal recebe uma marca de edição mesmo se somente
um Dish for alterado. Repetição de publicação usa o snapshot original, sem reconstruir
a resposta com dados modificados depois. Exclusão é lógica e não reduz cota.

## Ameaças tratadas e evidências esperadas

| Ameaça | Controle e verificação |
| --- | --- |
| Alterar autoria/overall pelo JSON | DTO fechado, rejeição de campos desconhecidos, autor da sessão, média calculada |
| Alterar/excluir Meal de outra conta | Autorização no service; testes HTTP 403 e de aplicação |
| Sobrescrever edição simultânea | Versão otimista; duas edições concorrentes resultam em uma gravação |
| Duplicar post ou exceder cota concorrente | Lock do autor, chave única e transação; testes com 6/8 chamadas simultâneas |
| Vazar contato/senha/token no perfil | DTOs distintos; testes de resposta e inspeção de hashes persistidos |
| Reutilizar token após logout/expiração | Verificação por requisição em banco; testes nos limites de tempo e logout |
| Forçar senhas repetidamente | PBKDF2-HMAC-SHA256 com salt aleatório, 600.000 iterações; limite por IP/minuto |
| Publicar dados inválidos | Bean Validation, regras do domínio e CHECK/FK/UNIQUE no PostgreSQL |
| Reaparecer conteúdo removido | Filtro em todas as consultas, contadores e agregações |

Cadastro pode revelar conflito de conta por resposta 409; login usa mensagem única
e executa comparação de hash mesmo para usuário inexistente. A API opera em loopback
por padrão. Em rede, HTTPS e configuração de origens/hosts são requisitos de implantação.
CSRF está desabilitado porque a API usa exclusivamente Bearer explícito no header;
adotar cookies futuramente exige rever essa decisão.

## Limites operacionais

O limitador de autenticação é local a cada processo, usa o IP da conexão e não confia
em `X-Forwarded-For`. Precisa de solução compartilhada e política de proxy antes de
escalar horizontalmente. Cada IP tem 20 tentativas/minuto por padrão e o mapa é
limitado a 10.000 IPs por janela. Sessões expiradas e idempotência permanecem armazenadas;
retenção e limpeza ainda precisam de política operacional.

Sem revisão independente nesta entrega. Testes não substituem recuperação de conta,
verificação de contato, retenção/exclusão de dados, backups/restauração ou autorização
administrativa. Fotos, tarefas de moderação e storage privado pertencem ao próximo
recorte e não estão disponíveis. Não há migração de dados antigos desconhecidos.

Migrações já aplicadas não devem ser editadas em ambientes persistentes: evoluir por
V2, V3 etc. Para recuperar um problema após implantação, priorizar correção adiante
e backup validado; não há promessa de rollback destrutivo. A verificação local usa
apenas banco efêmero e não migra automaticamente o volume de desenvolvimento.

Referências de implementação: [JSON e Jackson 3 no Spring Boot](https://docs.spring.io/spring-boot/reference/features/json.html)
e [consultas e EntityGraph no Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).
