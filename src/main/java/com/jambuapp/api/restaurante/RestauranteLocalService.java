package com.jambuapp.api.restaurante;

import java.math.*;
import java.time.Clock;
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
    private final Clock clock;
    public RestauranteLocalService(RestauranteRepository restaurantes, MealRepository meals, Clock clock) {
        this.restaurantes = restaurantes;
        this.meals = meals;
        this.clock = clock;
    }
    @Transactional
    public DadosLocais registrarSelecaoValidada(String placeId) {
        // Upsert resolve a corrida sem capturar exceção dentro de transação abortada.
        restaurantes.registrarSelecao(UUID.randomUUID(), placeId, clock.instant());
        return dados(restaurantes.findByGooglePlaceId(placeId).orElseThrow());
    }
    public DadosLocais consultar(UUID id) {
        return dados(restaurantes.findById(id).orElseThrow(RegraNegocioException::inexistente));
    }
    public void exigirExistente(UUID id) {
        if (!restaurantes.existsById(id)) throw RegraNegocioException.inexistente();
    }
    private DadosLocais dados(Restaurante r) {
        var estatistica = meals.estatisticas(List.of(r.getId())).stream().findFirst().orElse(null);
        return new DadosLocais(r.getId(), r.getGooglePlaceId(), r.getNome(), r.getEndereco(),
                estatistica == null ? null : estatistica.getNotaMedia().setScale(2, RoundingMode.HALF_UP),
                estatistica == null ? 0 : estatistica.getQuantidade());
    }
    public record DadosLocais(UUID id, String googlePlaceId, String nomeLegado, String enderecoLegado,
                             BigDecimal notaMedia, long quantidadeAvaliacoes) {}
}
