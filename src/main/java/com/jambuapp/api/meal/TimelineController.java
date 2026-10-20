package com.jambuapp.api.meal;

import com.jambuapp.api.seguranca.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {
    private final ConsultaMealService meals;
    public TimelineController(ConsultaMealService meals) { this.meals = meals; }
    @GetMapping
    public PaginaCursor<MealResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limite) {
        return meals.listar(null, null, principal.usuarioId(), cursor, limite);
    }
}
