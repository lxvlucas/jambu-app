package com.jambuapp.api.restaurante;

import java.util.UUID;
import com.jambuapp.api.comum.PaginaResponse;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderacao/restaurantes")
public class RevisaoRestauranteController {
    private final RevisaoRestauranteService service;
    public RevisaoRestauranteController(RevisaoRestauranteService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<PaginaResponse<RestauranteResumo>> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(defaultValue = "PROVISORIO") SituacaoRestaurante situacao,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(principal.usuarioId(), situacao, pagina, limite));
    }
    @GetMapping("/retornos")
    public ResponseEntity<PaginaResponse<RevisaoRestauranteService.CorrecaoPendenteResponse>> correcoes(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.correcoesPendentes(principal.usuarioId(), pagina, limite));
    }
    @PostMapping("/{id}/revisao")
    public ResponseEntity<RestauranteResumo> revisar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID id, @Valid @RequestBody RevisaoRestauranteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.revisar(principal.usuarioId(), id, request));
    }
}
