package com.jambuapp.api.restaurante;

import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Áreas atendidas pelo catálogo, configuradas em jambu.restaurantes.areas e lidas de
 * /geografia/&lt;nome&gt;.geojson (malhas IBGE). Não representa endereço postal.
 */
@Component
public class AreaCatalogo {
    private final List<JsonNode> poligonos = new ArrayList<>();
    private final String nome;
    private final Retangulo retangulo;
    public AreaCatalogo(JsonMapper json, CatalogoProperties config) {
        List<String> nomes = new ArrayList<>();
        for (String area : config.areas()) {
            if (!area.matches("[a-z0-9-]{1,40}")) throw new IllegalStateException("Nome de área inválido: " + area);
            try (var stream = getClass().getResourceAsStream("/geografia/" + area + ".geojson")) {
                if (stream == null) throw new IllegalStateException("Malha da área indisponível: " + area);
                var colecao = json.readTree(stream);
                if (!colecao.path("nome").isString() || colecao.path("features").isEmpty())
                    throw new IllegalStateException("Malha da área inválida: " + area);
                nomes.add(colecao.path("nome").asString());
                for (JsonNode feature : colecao.path("features")) {
                    var geometria = feature.path("geometry");
                    switch (geometria.path("type").asString()) {
                        case "Polygon" -> poligonos.add(geometria.path("coordinates"));
                        case "MultiPolygon" -> geometria.path("coordinates").forEach(poligonos::add);
                        default -> throw new IllegalStateException("Geometria inválida na área: " + area);
                    }
                }
            } catch (java.io.IOException ex) { throw new IllegalStateException("Malha da área indisponível: " + area, ex); }
        }
        if (poligonos.isEmpty()) throw new IllegalStateException("Nenhuma área do catálogo configurada.");
        nome = String.join(" e ", nomes);
        double sul = 90, norte = -90, oeste = 180, leste = -180;
        for (JsonNode aneis : poligonos) for (JsonNode ponto : aneis.get(0)) {
            double x = ponto.get(0).asDouble(), y = ponto.get(1).asDouble();
            sul = Math.min(sul, y); norte = Math.max(norte, y); oeste = Math.min(oeste, x); leste = Math.max(leste, x);
        }
        retangulo = new Retangulo(sul, oeste, norte, leste);
    }
    /** Retângulo que envolve todas as áreas; usado para restringir a busca no Google (T02). */
    public Retangulo retangulo() { return retangulo; }
    public record Retangulo(double sul, double oeste, double norte, double leste) {}
    /** Nome exibido nas mensagens, como "Grande São Luís". */
    public String nome() { return nome; }
    public boolean contem(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return false;
        for (JsonNode aneis : poligonos) {
            if (!dentro(aneis.get(0), longitude, latitude)) continue;
            boolean buraco = false;
            for (int i = 1; i < aneis.size(); i++) if (dentro(aneis.get(i), longitude, latitude)) buraco = true;
            if (!buraco) return true;
        }
        return false;
    }
    public void exigir(double latitude, double longitude) {
        if (!contem(latitude, longitude))
            throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_CONTENT, "fora_da_area",
                    "O Jambu ainda está só na " + nome + ".");
    }
    private boolean dentro(JsonNode anel, double x, double y) {
        boolean dentro = false;
        for (int i = 0, j = anel.size() - 1; i < anel.size(); j = i++) {
            double xi = anel.get(i).get(0).asDouble(), yi = anel.get(i).get(1).asDouble();
            double xj = anel.get(j).get(0).asDouble(), yj = anel.get(j).get(1).asDouble();
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) dentro = !dentro;
        }
        return dentro;
    }
}
