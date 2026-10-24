package com.jambuapp.api.meal;

import java.util.UUID;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals/{id}/comentarios")
public class ComentarioController {
    private final ComentarioService comentarios;
    public ComentarioController(ComentarioService comentarios) { this.comentarios = comentarios; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComentarioService.ComentarioResponse comentar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID id, @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody ComentarioService.ComentarioRequest request) {
        return comentarios.comentar(principal.usuarioId(), id, chave, request);
    }
    @DeleteMapping("/{comentarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id,
            @PathVariable UUID comentarioId) {
        comentarios.excluir(principal.usuarioId(), id, comentarioId);
    }
    @GetMapping
    public PaginaCursor<ComentarioService.ComentarioResponse> listar(@PathVariable UUID id,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limite) {
        return comentarios.listar(id, cursor, limite);
    }
}
