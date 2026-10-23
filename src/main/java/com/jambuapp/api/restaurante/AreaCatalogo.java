package com.jambuapp.api.restaurante;

import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Malha municipal simplificada IBGE 2111300. Não representa endereço postal. */
@Component
public class AreaCatalogo {
    private final JsonNode aneis;
    public AreaCatalogo(JsonMapper json) {
        try (var stream = getClass().getResourceAsStream("/geografia/sao-luis-ibge.geojson")) {
            var feature = json.readTree(stream).path("features").get(0);
            if (!"2111300".equals(feature.path("properties").path("codarea").asString())
                    || !"Polygon".equals(feature.path("geometry").path("type").asString()))
                throw new IllegalStateException("Malha municipal inválida.");
            aneis = feature.path("geometry").path("coordinates");
        } catch (java.io.IOException ex) { throw new IllegalStateException("Malha municipal indisponível.", ex); }
    }
    public boolean contem(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return false;
        if (!dentro(aneis.get(0), longitude, latitude)) return false;
        for (int i = 1; i < aneis.size(); i++) if (dentro(aneis.get(i), longitude, latitude)) return false;
        return true;
    }
    public void exigir(double latitude, double longitude) {
        if (!contem(latitude, longitude))
            throw RegraNegocioException.invalido("O pin deve estar dentro de São Luís no recorte atual do MVP.");
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
