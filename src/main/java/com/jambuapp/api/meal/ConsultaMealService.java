package com.jambuapp.api.meal;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.foto.FotoDishRepository;
import com.jambuapp.api.foto.FotoPublicaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ConsultaMealService {
    private final MealRepository meals;
    private final FotoDishRepository fotosDish;
    public ConsultaMealService(MealRepository meals, FotoDishRepository fotosDish) {
        this.meals = meals;
        this.fotosDish = fotosDish;
    }
    public MealResponse consultar(UUID id) {
        var meal = meals.findByIdAndExcluidaEmIsNull(id).orElseThrow(RegraNegocioException::inexistente);
        return MealResponse.de(meal, fotosAprovadas(List.of(meal)));
    }
    public PaginaCursor<MealResponse> listar(UUID autor, UUID restaurante, UUID timeline, String cursor, int limite) {
        return pagina(cursor, limite, (posicao, pagina) -> meals.buscarIds(autor, restaurante, timeline,
                posicao.instante(), posicao.id(), pagina), (meal, resposta) -> resposta);
    }
    /** Feed (T19): a Meal e o nome do restaurante, que vem do catálogo do Jambu (nunca do Google). */
    public PaginaCursor<ItemFeed> feed(UUID usuario, AbaFeed aba, String cursor, int limite) {
        return pagina(cursor, limite, (posicao, pagina) -> meals.buscarIdsFeed(usuario, aba == AbaFeed.SEGUINDO,
                posicao.instante(), posicao.id(), pagina), (meal, resposta) -> new ItemFeed(resposta, meal.getRestaurante().getNome()));
    }
    public record ItemFeed(MealResponse meal, String nomeRestaurante) {}
    private <T> PaginaCursor<T> pagina(String cursor, int limite,
            java.util.function.BiFunction<CursorMeal, PageRequest, List<UUID>> buscar,
            java.util.function.BiFunction<Meal, MealResponse, T> item) {
        Paginacao.limite(limite);
        var ids = buscar.apply(CursorMeal.ler(cursor), PageRequest.of(0, limite + 1));
        if (ids.isEmpty()) return new PaginaCursor<>(List.of(), null);
        boolean mais = ids.size() > limite;
        var selecionados = ids.subList(0, Math.min(limite, ids.size()));
        var carregadas = meals.buscarCompletas(selecionados);
        var fotosPorDish = fotosAprovadas(carregadas);
        var porId = carregadas.stream().collect(Collectors.toMap(Meal::getId, Function.identity()));
        var ultima = porId.get(selecionados.getLast());
        var itens = selecionados.stream().map(porId::get).map(m -> item.apply(m, MealResponse.de(m, fotosPorDish))).toList();
        return new PaginaCursor<>(itens, mais ? new CursorMeal(ultima.getPublicadaEm(), ultima.getId()).codificar() : null);
    }
    private Map<UUID, FotoPublicaResponse> fotosAprovadas(Collection<Meal> listaMeals) {
        List<UUID> dishIds = listaMeals.stream().flatMap(m -> m.getDishes().stream()).map(Dish::getId).toList();
        if (dishIds.isEmpty()) return Map.of();
        Map<UUID, FotoPublicaResponse> mapa = new HashMap<>();
        for (var foto : fotosDish.buscarAprovadasPorDishes(dishIds)) {
            var dto = FotoPublicaResponse.de(foto);
            if (dto != null && foto.getDishAtivoId() != null) {
                mapa.put(foto.getDishAtivoId(), dto);
            }
        }
        return mapa;
    }
}
