package com.jambuapp.api.restaurante;

import java.util.UUID;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurantes/{id}")
public class RetornoRestauranteController {
    private final RetornoRestauranteService retornos;
    public RetornoRestauranteController(RetornoRestauranteService retornos) { this.retornos = retornos; }
    @GetMapping("/confirmacao")
    public ResponseEntity<RetornoRestauranteService.PerguntaConfirmacaoResponse> pergunta(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(retornos.pergunta(principal.usuarioId(), id));
    }
    @PostMapping("/retornos")
    public ResponseEntity<RetornoRestauranteService.RetornoRestauranteResponse> responder(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id,
            @Valid @RequestBody RetornoRestauranteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(retornos.responder(principal.usuarioId(), id, request));
    }
}
