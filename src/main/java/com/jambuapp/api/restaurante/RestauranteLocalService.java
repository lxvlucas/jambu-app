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
