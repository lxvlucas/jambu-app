package com.jambuapp.api.restaurante;

import java.util.UUID;
import com.jambuapp.api.meal.*;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurantes")
public class RestauranteController {
    private final RestauranteService restaurantes;
    private final CadastroRestauranteService cadastros;
    private final ConsultaMealService meals;
    public RestauranteController(RestauranteService restaurantes, CadastroRestauranteService cadastros, ConsultaMealService meals) {
        this.restaurantes = restaurantes; this.cadastros = cadastros; this.meals = meals;
    }
    @GetMapping({"", "/sugestoes"})
    public ResponseEntity<SugestoesRestaurantesResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @RequestParam String termo,
            @RequestParam(required = false) UUID tokenSessao, @RequestParam(defaultValue = "false") boolean ampliar) {
        return semCache(restaurantes.buscar(termo, tokenSessao, ampliar, principal.usuarioId()));
    }
    @PostMapping("/cadastros")
    public ResponseEntity<CadastroRestauranteResponse> cadastrar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chave, @Valid @RequestBody CadastroRestauranteRequest request) {
        return semCache(cadastros.cadastrar(principal.usuarioId(), chave, request));
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
