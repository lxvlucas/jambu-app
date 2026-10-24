# Áreas do catálogo

Cada área atendida é um arquivo `<nome>.geojson` nesta pasta, escolhido por
`jambu.restaurantes.areas` (`JAMBU_AREAS_CATALOGO`, lista separada por vírgula). O arquivo é
uma FeatureCollection com o membro `nome` (usado na mensagem `fora_da_area`) e uma feature
`Polygon` ou `MultiPolygon` por município.

## grande-sao-luis.geojson

Grande São Luís (a ilha): São Luís (IBGE 2111300), São José de Ribamar (2111201), Paço do
Lumiar (2107506) e Raposa (2109452). Malha de qualidade máxima obtida em 23/10/2026 pela
[API de malhas do IBGE](https://servicodados.ibge.gov.br/api/v3/malhas/municipios/2111300?formato=application/vnd.geo+json&qualidade=maxima)
(mesma URL para cada código). A qualidade mínima deixava de fora o centro de São José de
Ribamar e 32 de 2.295 lugares de comida do Overture com confiança ≥ 0,5; a máxima inclui todos
(21 KB). A malha anterior, só do município de São Luís, foi removida.

Usada para validar pins próprios e recortar importações; não é validação de endereço postal.
Nenhuma localização do usuário é inferida ou armazenada por essa validação. Para expandir para
outra região, acrescente o arquivo e o nome em `JAMBU_AREAS_CATALOGO`.
