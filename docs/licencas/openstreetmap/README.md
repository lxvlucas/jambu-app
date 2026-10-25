# OpenStreetMap: pontos de bairro

A tabela `bairros` (migração `V15__bairros.sql`) traz 298 pontos `place=suburb` e `place=quarter` da ilha
de São Luís, extraídos do OpenStreetMap em 24/10/2026 pela API Overpass, sem modificação.

- Dados © colaboradores do OpenStreetMap, sob a Open Database License (ODbL 1.0):
  https://www.openstreetmap.org/copyright e https://opendatacommons.org/licenses/odbl/1-0/.
- **Crédito obrigatório no app**, em Configurações → Sobre: "© colaboradores do OpenStreetMap".
- O bairro de cada restaurante é um resultado de geocodificação: o bairro citado no endereço do
  Overture, se houver um ponto dele a até 3 km do pin, ou o ponto mais próximo do nosso pin.
  Pela Geocoding Guideline da OSM Foundation, guardar esse nome junto com os dados do Jambu não aciona o
  "compartilhar igual". A tabela `bairros` em si continua sob a ODbL.
- Por que OSM: a malha de bairros do IBGE (Censo 2022) não cobre São Luís, Paço do Lumiar e Raposa.
