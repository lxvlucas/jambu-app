package com.jambuapp.api.meal;

import java.net.URI;
import java.util.UUID;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals")
public class MealController {
    private final MealService meals;
    private final ConsultaMealService consultas;
    public MealController(MealService meals, ConsultaMealService consultas) { this.meals = meals; this.consultas = consultas; }
    @PostMapping
    public ResponseEntity<MealResponse> publicar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chave, @Valid @RequestBody MealRequest request) {
        var resposta = meals.publicar(principal.usuarioId(), chave, request);
        return ResponseEntity.created(URI.create("/api/meals/" + resposta.id())).body(resposta);
    }
    @GetMapping("/{id}")
    public MealResponse consultar(@PathVariable UUID id) { return consultas.consultar(id); }
    @PutMapping("/{id}")
    public MealResponse editar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id,
            @Valid @RequestBody MealRequest request) { return meals.editar(principal.usuarioId(), id, request); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) { meals.excluir(principal.usuarioId(), id); }
    @GetMapping("/{id}/link")
    public LinkResponse link(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) {
        return new LinkResponse(meals.gerarLink(principal.usuarioId(), id));
    }
    public record LinkResponse(URI url) {}
}
