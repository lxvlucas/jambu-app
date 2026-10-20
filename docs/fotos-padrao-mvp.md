# Padrão de fotos do MVP — 20/10/2026

Lucas definiu proporção **3:4 vertical** e pediu tamanho/compactação adequados a uma
rede social. Este contrato fecha a decisão da tarefa 1 do checklist; upload,
processamento, armazenamento e moderação foram implementados nas tarefas 10 e 11.

## Dimensões e arquivos

| Item | Regra do Jambu |
| --- | --- |
| Proporção publicada | 3:4 vertical, sem distorção ou faixas artificiais |
| Imagem principal | Até 1080 × 1440 px; reduzir imagens maiores e não ampliar menores |
| Resolução mínima após recorte | 600 × 800 px |
| Miniatura | 360 × 480 px, derivada da mesma imagem normalizada |
| Entrada do MVP | JPEG ou PNG estáticos, validados pelo conteúdo e decodificação |
| Limite de upload | 8 MiB por arquivo (8.388.608 bytes) |
| Limite de decodificação | 20 megapixels e até 8192 px por lado, conferidos antes de alocar a imagem completa |
| Saída | JPEG em sRGB, sem transparência; fundo branco para PNG transparente |
| Compactação principal | Qualidade inicial 85%, com tentativas em 80%, 75% e 70% para atingir até 1 MiB |
| Miniatura compactada | Qualidade inicial 80%, reduzindo até 70%, com teto de 200 KiB |
| Metadados | Aplicar orientação antes do recorte e remover EXIF, GPS, comentários e demais metadados da saída |
| Outros formatos | GIF/animados, SVG, HEIC/HEIF e WebP não entram no primeiro contrato; o cliente deve converter antes de enviar |

1080 × 1440 é a resolução escolhida para o formato solicitado; percentual de qualidade
e limites em bytes são decisões técnicas do Jambu, não uma alegação de configuração
interna idêntica à de outra rede. O tamanho final varia com o conteúdo. Se a imagem
continuar acima do teto após as tentativas, retornar erro de validação, sem publicar
um arquivo que ultrapasse os limites. Não guardar o upload original como alternativa.

A consulta à página oficial de resolução do Instagram ficou indisponível nesta
sessão (HTTP 429/login). Assim, não há atribuição de uma regra de compactação à Meta.
Referência para conferência futura: [resolução de fotos no Instagram](https://help.instagram.com/1631821640426723).

## Recorte e validação

Android/web apresentam recorte 3:4 com prévia e escolha do enquadramento antes do envio.
O backend confere a proporção após normalizar orientação (`largura × 4 = altura × 3`).
Não deve recortar silenciosamente a foto do usuário. Entrada fora da proporção ou
abaixo da resolução mínima retorna erro de validação. O backend faz a recompactação
final mesmo se o cliente já tiver otimizado o arquivo.

O contrato vale para as imagens principais de Dish e perfil. Um avatar circular ou
quadrado poderá ser apresentado na interface a partir da imagem aprovada, sem mudar
o arquivo principal 3:4; a interface deve mostrar uma prévia desse enquadramento.

## Relação com a moderação

1. Receber com autenticação, autorização sobre Dish/perfil e limite de upload.
2. Validar formato real, dimensões e quantidade de pixels; normalizar e reencodar.
3. Gravar imagem e miniatura em armazenamento privado, com identificadores gerados.
4. Criar registro PENDENTE e tarefa persistente de moderação; registrar hash/versão
   do arquivo normalizado que será efetivamente exibido.
5. Moderar essa mesma versão. Somente APROVADA libera imagem e miniatura para leitura.
6. Substituir a imagem cria nova versão pendente; falhas/retries não reaproveitam
   aprovação anterior. Excluir temporários e definir retenção dos rejeitados na tarefa 10.

## Critérios para os testes da implementação

- Rejeitar arquivo maior que 8 MiB, conteúdo inválido, formato disfarçado, animação,
  imagem acima dos limites de pixels/lado, proporção inválida e resolução insuficiente.
- Conferir saída 3:4, redução sem ampliação, JPEG/sRGB e ausência de EXIF/GPS.
- Conferir os tetos em bytes, inclusive imagens difíceis de comprimir, e a miniatura.
- Confirmar autorização, privacidade de arquivos pendentes/rejeitados, falha do
  provedor, troca de versão e retomada depois de reinício.
