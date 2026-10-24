package com.jambuapp.api.meal;

import com.jambuapp.api.seguranca.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final ConsultaMealService meals;
    public FeedController(ConsultaMealService meals) { this.meals = meals; }
    @GetMapping
    public PaginaCursor<ConsultaMealService.ItemFeed> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam AbaFeed aba, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limite) {
        return meals.feed(principal.usuarioId(), aba, cursor, limite);
    }
}
