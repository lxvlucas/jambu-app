# Avisos do catálogo Overture

O recorte de alimentação de São Luís da release `2026-10-21.0` contém dados de
Meta, Microsoft, Overture Maps Foundation, Foursquare e AllThePlaces. As fontes
e suas licenças foram conferidas no campo `sources` de cada feature e na
[documentação de atribuição do Overture](https://docs.overturemaps.org/attribution/#places),
consultada em 22/10/2026. A presença desses arquivos não confirma, por si só, que
o recorte foi carregado em um banco; consultar o registro operacional da carga.

| Fontes presentes | Licença preservada |
| --- | --- |
| Meta, Microsoft e Overture | [CDLA Permissive 2.0](CDLA-Permissive-2.0.txt) |
| Foursquare | [Apache 2.0](Apache-2.0.txt) e [NOTICE integral](Foursquare-NOTICE.txt) |
| AllThePlaces | [CC0 1.0](CC0-1.0.txt) |

O Jambu seleciona pontos dentro da malha municipal simplificada do IBGE,
categorias alimentares e confiança mínima de 0,7. Converte as categorias para
as categorias internas e normaliza nomes para busca; preserva identificador
GERS, versão, fontes, licenças e atribuição. Não redistribuir o catálogo sem
esses avisos. As interfaces devem manter visível a atribuição devolvida pela API.

O [NOTICE Foursquare](Foursquare-NOTICE.txt) integra a documentação da API
e foi extraído integralmente da [página oficial](https://opensource.foursquare.com/places-notice-txt/),
convertendo apenas a marcação HTML para texto. O Overture informa que os dados
Foursquare foram transformados para seu esquema em 18/03/2026.

Origem dos textos: [CDLA oficial](https://raw.githubusercontent.com/Community-Data-License-Agreements/Releases/main/CDLA-Permissive-2.0.txt),
[Apache oficial](https://www.apache.org/licenses/LICENSE-2.0.txt) e
[cópia CC0 do SPDX](https://raw.githubusercontent.com/spdx/license-list-data/main/text/CC0-1.0.txt).
O endpoint Creative Commons recusou a consulta automatizada; o texto CC0 foi
obtido do repositório público SPDX. Export, hashes e snapshots das fontes ficam
no diretório local ignorado `.local/overture/`.
