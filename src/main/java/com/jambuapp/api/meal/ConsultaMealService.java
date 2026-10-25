package com.jambuapp.api.meal;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.foto.FotoPublicaResponse;
import com.jambuapp.api.foto.FotoTransacionalService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ConsultaMealService {
    private final MealRepository meals;
    private final FotoTransacionalService fotos;
    private final CurtidaService curtidas;
    private final ComentarioService comentarios;
    public ConsultaMealService(MealRepository meals, FotoTransacionalService fotos, CurtidaService curtidas,
            ComentarioService comentarios) {
        this.meals = meals;
        this.fotos = fotos;
        this.curtidas = curtidas;
        this.comentarios = comentarios;
    }
    public MealResponse consultar(UUID id) {
        var meal = meals.findByIdAndExcluidaEmIsNull(id).orElseThrow(RegraNegocioException::inexistente);
        return MealResponse.de(meal, fotos.aprovadasPorMeal(List.of(meal.getId())).getOrDefault(meal.getId(), List.of()));
    }
    public PaginaCursor<MealResponse> listar(UUID autor, UUID restaurante, UUID timeline, String cursor, int limite) {
        return pagina(cursor, limite, (posicao, pagina) -> meals.buscarIds(autor, restaurante, timeline,
                posicao.instante(), posicao.id(), pagina), ids -> (meal, resposta) -> resposta);
    }
    /**
     * Feed (T19): a Meal, o nome do restaurante (do catálogo do Jambu, nunca do Google), as curtidas e o total
     * de comentários (T21). Os contadores ficam no item do feed, e não na MealResponse, porque a resposta da
     * publicação é guardada para a idempotência e os totais mudam com o tempo.
     */
    public PaginaCursor<ItemFeed> feed(UUID usuario, AbaFeed aba, String cursor, int limite) {
        return pagina(cursor, limite, (posicao, pagina) -> meals.buscarIdsFeed(usuario, aba == AbaFeed.SEGUINDO,
                posicao.instante(), posicao.id(), pagina), ids -> {
                    var resumos = curtidas.resumos(usuario, ids);
                    var totais = comentarios.totais(ids);
                    return (meal, resposta) -> {
                        var c = resumos.getOrDefault(meal.getId(), new CurtidaService.ResumoCurtidas(0, false));
                        return new ItemFeed(resposta, meal.getRestaurante().getNome(), c.quantidade(), c.curtidaPorMim(),
                                totais.getOrDefault(meal.getId(), 0L));
                    };
                });
    }
    public record ItemFeed(MealResponse meal, String nomeRestaurante, long curtidas, boolean curtidaPorMim, long comentarios) {}
    private <T> PaginaCursor<T> pagina(String cursor, int limite,
            java.util.function.BiFunction<CursorMeal, PageRequest, List<UUID>> buscar,
            Function<List<UUID>, java.util.function.BiFunction<Meal, MealResponse, T>> montador) {
        Paginacao.limite(limite);
        var ids = buscar.apply(CursorMeal.ler(cursor), PageRequest.of(0, limite + 1));
        if (ids.isEmpty()) return new PaginaCursor<>(List.of(), null);
        boolean mais = ids.size() > limite;
        var selecionados = ids.subList(0, Math.min(limite, ids.size()));
        var carregadas = meals.buscarCompletas(selecionados);
        Map<UUID, List<FotoPublicaResponse>> fotosPorMeal = fotos.aprovadasPorMeal(selecionados);
        var porId = carregadas.stream().collect(Collectors.toMap(Meal::getId, Function.identity()));
        var ultima = porId.get(selecionados.getLast());
        var item = montador.apply(selecionados);
        var itens = selecionados.stream().map(porId::get)
                .map(m -> item.apply(m, MealResponse.de(m, fotosPorMeal.getOrDefault(m.getId(), List.of())))).toList();
        return new PaginaCursor<>(itens, mais ? new CursorMeal(ultima.getPublicadaEm(), ultima.getId()).codificar() : null);
    }
}
