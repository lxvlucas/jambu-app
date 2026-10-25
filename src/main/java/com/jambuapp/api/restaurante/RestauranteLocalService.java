package com.jambuapp.api.restaurante;

import java.math.*;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.meal.MealRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fronteira transacional exclusivamente local: nenhum provedor HTTP nesta classe. */
@Service
@Transactional(readOnly = true)
public class RestauranteLocalService {
    private final RestauranteRepository restaurantes;
    private final MealRepository meals;
    public RestauranteLocalService(RestauranteRepository restaurantes, MealRepository meals) {
        this.restaurantes = restaurantes; this.meals = meals;
    }
    public DadosLocais consultar(UUID id) {
        return dados(restaurantes.findById(id).orElseThrow(RegraNegocioException::inexistente));
    }
    public Optional<UUID> porGoogle(String placeId) {
        return restaurantes.findByGooglePlaceId(placeId).map(Restaurante::getId);
    }
    public List<RestauranteResumo> buscar(String termo) {
        String normalizado = NomesRestaurantes.normalizar(termo);
        if (normalizado.isEmpty()) return List.of();
        return restaurantes.buscar(normalizado, normalizado + "%", NomesRestaurantes.consultaPrefixos(normalizado))
                .stream().map(RestauranteResumo::de).toList();
    }
    /**
     * "Você está aqui?" (T11, P-08): até 10 lugares em 150 m do ponto; sem nenhum, amplia para 500 m. Só o banco,
     * e o ponto não é guardado. O retângulo usa o índice do pin; a distância exata corta o que fica nos cantos.
     */
    public RestaurantesProximosResponse proximos(double latitude, double longitude) {
        double deltaLat = 500 / 111320.0;
        double deltaLon = deltaLat / Math.cos(Math.toRadians(latitude));
        var itens = restaurantes.proximos(latitude - deltaLat, latitude + deltaLat, longitude - deltaLon, longitude + deltaLon)
                .stream()
                .map(r -> new RestaurantesProximosResponse.Item(RestauranteResumo.de(r),
                        Math.round(NomesRestaurantes.distancia(latitude, longitude, r.getLatitude(), r.getLongitude()))))
                .filter(i -> i.distanciaMetros() <= 500)
                .sorted(Comparator.comparingLong(RestaurantesProximosResponse.Item::distanciaMetros)
                        .thenComparing(i -> i.restaurante().id()))
                .toList();
        boolean algumEm150 = !itens.isEmpty() && itens.getFirst().distanciaMetros() <= 150;
        return new RestaurantesProximosResponse(itens.stream()
                .filter(i -> !algumEm150 || i.distanciaMetros() <= 150).limit(10).toList());
    }
    public void exigirExistente(UUID id) {
        if (!restaurantes.existsById(id)) throw RegraNegocioException.inexistente();
    }
    public void exigirSelecionavel(UUID id) {
        restaurantes.findById(id).orElseThrow(RegraNegocioException::inexistente).exigirSelecionavel();
    }
    private DadosLocais dados(Restaurante r) {
        var estatistica = meals.estatisticas(List.of(r.getId())).stream().findFirst().orElse(null);
        return new DadosLocais(RestauranteResumo.de(r),
                estatistica == null ? null : estatistica.getNotaMedia().setScale(2, RoundingMode.HALF_UP),
                estatistica == null ? 0 : estatistica.getQuantidade(),
                estatistica == null ? null : estatistica.getUltimaAvaliacaoEm());
    }
    public record DadosLocais(RestauranteResumo restaurante, BigDecimal notaMedia, long quantidadeAvaliacoes,
            java.time.Instant ultimaAvaliacaoEm) {}
}
