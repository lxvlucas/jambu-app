package com.jambuapp.api.restaurante.overture;

import java.time.Clock;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.restaurante.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Importa lotes já extraídos. Não baixa URLs fornecidas pelo cliente nem consulta Google. */
@Service
@Transactional
public class ImportacaoOvertureService {
    private final RestauranteRepository restaurantes;
    private final CatalogoTransacionalService catalogo;
    private final RevisaoRestauranteService revisao;
    private final AreaCatalogo area;
    private final Validator validator;
    private final Clock clock;
    private static final Set<String> LICENCAS = Set.of("CDLA-Permissive-2.0", "Apache-2.0", "CC0-1.0");
    public ImportacaoOvertureService(RestauranteRepository restaurantes, CatalogoTransacionalService catalogo,
            RevisaoRestauranteService revisao, AreaCatalogo area, Validator validator, Clock clock) {
        this.restaurantes = restaurantes; this.catalogo = catalogo; this.revisao = revisao;
        this.area = area; this.validator = validator; this.clock = clock;
    }
    public Resultado importar(UUID revisorId, ImportacaoOvertureRequest request) {
        revisao.exigirRevisor(revisorId);
        if (request == null || !validator.validate(request).isEmpty() || !LICENCAS.containsAll(request.licencas()))
            throw RegraNegocioException.invalido("Informe release, licenças verificadas e até 200 features GeoJSON.");
        catalogo.bloquearCatalogo();
        List<Item> itens = new ArrayList<>();
        for (JsonNode feature : request.features()) itens.add(importarFeature(feature, request));
        restaurantes.flush();
        return new Resultado(List.copyOf(itens));
    }
    private Item importarFeature(JsonNode feature, ImportacaoOvertureRequest request) {
        if (feature == null || !"Feature".equals(feature.path("type").asString()))
            throw RegraNegocioException.invalido("Cada item deve ser uma Feature GeoJSON Overture.");
        var props = feature.path("properties");
        String id = texto(props, "id");
        if (id == null) id = texto(feature, "id");
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw RegraNegocioException.invalido("Feature sem identificador GERS válido.");
        // O export GeoJSON do cliente oficial omite a coluna de partição type.
        // Este endpoint recebe Places; quando o tipo vier explícito, deve concordar.
        var tipo = props.path("type");
        if ((!tipo.isMissingNode() && !"place".equals(tipo.asString()))
                || !"Point".equals(feature.path("geometry").path("type").asString()))
            return new Item(id, "IGNORADO_TIPO", null);
        var coords = feature.path("geometry").path("coordinates");
        if (!coords.isArray() || coords.size() != 2 || !coords.get(0).isNumber() || !coords.get(1).isNumber())
            throw RegraNegocioException.invalido("Coordenadas Overture inválidas.");
        double lon = coords.get(0).asDouble(), lat = coords.get(1).asDouble();
        if (!area.contem(lat, lon)) return new Item(id, "FORA_DO_RECORTE", null);
        String status = texto(props, "operating_status");
        if (status != null && !"open".equals(status)) return new Item(id, "IGNORADO_FECHADO", null);
        if (props.has("confidence") && props.path("confidence").asDouble() < 0.7)
            return new Item(id, "IGNORADO_BAIXA_CONFIANCA", null);
        var categoria = categoria(props);
        if (categoria == null) return new Item(id, "IGNORADO_CATEGORIA", null);
        String nome = texto(props.path("names"), "primary");
        var fontes = props.path("sources");
        if (nome == null || nome.length() > 150 || NomesRestaurantes.normalizar(nome).length() < 2
                || !fontes.isArray() || fontes.isEmpty() || fontes.toString().length() > 20000
                || !props.path("version").isIntegralNumber() || props.path("version").asInt() < 0)
            throw RegraNegocioException.invalido("Feature sem nome, versão ou fontes válidas.");
        var datasets = new TreeSet<String>();
        for (JsonNode fonte : fontes) {
            if (texto(fonte, "dataset") == null) throw RegraNegocioException.invalido("Fonte Overture sem dataset.");
            datasets.add(texto(fonte, "dataset"));
        }
        String atribuicao = "Overture Maps Foundation; fontes: " + String.join(", ", datasets)
                + "; licenças: " + String.join(", ", request.licencas());
        if (atribuicao.length() > 1000) throw RegraNegocioException.invalido("Atribuições excedem o limite.");
        int versao = props.path("version").asInt();
        var existente = restaurantes.findByOvertureId(id);
        if (existente.isPresent()) {
            var r = existente.get();
            if (request.release().compareTo(r.getOvertureRelease()) < 0 || versao < r.getOvertureVersao())
                return new Item(id, "IGNORADO_ANTIGO", r.getId());
            if (request.release().equals(r.getOvertureRelease()) && versao == r.getOvertureVersao())
                return new Item(id, "INALTERADO", r.getId());
        }
        var candidatos = existente.isPresent() ? List.<CadastroRestauranteResponse.Candidato>of()
                : catalogo.candidatos(nome, lat, lon, null);
        var r = existente.orElseGet(() -> Restaurante.importado(nome, lat, lon, categoria, clock.instant()));
        // Não fundir por nome/proximidade. A colisão fica visível aos founders.
        if (!candidatos.isEmpty()) r.revisar(SituacaoRestaurante.PROVISORIO, clock.instant());
        String endereco = null;
        if (props.path("addresses").isArray() && !props.path("addresses").isEmpty()) {
            endereco = texto(props.path("addresses").get(0), "freeform");
            if (endereco != null && endereco.length() > 300) endereco = endereco.substring(0, 300);
        }
        r.atualizarOverture(id, request.release(), versao, fontes.toString(), String.join(", ", request.licencas()), atribuicao,
                nome, lat, lon, categoria, endereco, clock.instant());
        restaurantes.saveAndFlush(r);
        return new Item(id, existente.isPresent() ? "ATUALIZADO" : candidatos.isEmpty() ? "CRIADO" : "CRIADO_PARA_REVISAO", r.getId());
    }
    private CategoriaRestaurante categoria(JsonNode props) {
        Set<String> categorias = new HashSet<>();
        String basica = texto(props, "basic_category");
        String primaria = texto(props.path("taxonomy"), "primary");
        if (basica != null) categorias.add(basica);
        if (primaria != null) categorias.add(primaria);
        for (var item : props.path("taxonomy").path("hierarchy")) if (item.isString()) categorias.add(item.asString());
        if (categorias.contains("bakery")) return CategoriaRestaurante.PADARIA;
        if (categorias.contains("ice_cream_shop")) return CategoriaRestaurante.SORVETERIA;
        if (categorias.contains("cafe") || categorias.contains("coffee_shop")) return CategoriaRestaurante.CAFE;
        if (categorias.contains("bar") || categorias.contains("pub")) return CategoriaRestaurante.BAR;
        if (categorias.contains("fast_food_restaurant") || categorias.contains("snack_bar")) return CategoriaRestaurante.LANCHONETE;
        if (categorias.contains("restaurant") || categorias.contains("casual_eatery")) return CategoriaRestaurante.RESTAURANTE;
        return null;
    }
    private static String texto(JsonNode node, String campo) {
        var v = node.path(campo);
        return v.isString() && !v.asString().isBlank() ? v.asString().strip() : null;
    }
    public record Item(String overtureId, String resultado, UUID restauranteId) {}
    public record Resultado(List<Item> itens) {}
}
