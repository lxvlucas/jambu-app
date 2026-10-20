package com.jambuapp.api.restaurante;

import java.util.UUID;
import com.jambuapp.api.meal.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurantes")
public class RestauranteController {
    private final RestauranteService restaurantes;
    private final ConsultaMealService meals;
    public RestauranteController(RestauranteService restaurantes, ConsultaMealService meals) {
        this.restaurantes = restaurantes;
        this.meals = meals;
    }
    @GetMapping({"", "/sugestoes"})
    public ResponseEntity<SugestoesRestaurantesResponse> buscar(@RequestParam String termo, @RequestParam UUID tokenSessao) {
        return semCache(restaurantes.buscar(termo, tokenSessao));
    }
    @PostMapping("/selecoes")
    public ResponseEntity<RestauranteResponse> selecionar(@Valid @RequestBody SelecionarRestauranteRequest request) {
        return semCache(restaurantes.selecionar(request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<RestauranteResponse> consultar(@PathVariable UUID id) { return semCache(restaurantes.consultar(id)); }
    @GetMapping("/{id}/meals")
    public PaginaCursor<MealResponse> meals(@PathVariable UUID id, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limite) {
        restaurantes.exigirExistente(id);
        return meals.listar(null, id, null, cursor, limite);
    }
    private static <T> ResponseEntity<T> semCache(T resposta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
