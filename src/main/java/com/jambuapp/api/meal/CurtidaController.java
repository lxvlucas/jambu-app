package com.jambuapp.api.meal;

import java.util.UUID;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals/{id}")
public class CurtidaController {
    private final CurtidaService curtidas;
    public CurtidaController(CurtidaService curtidas) { this.curtidas = curtidas; }
    @PutMapping("/curtida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void curtir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) {
        curtidas.curtir(principal.usuarioId(), id);
    }
    @DeleteMapping("/curtida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descurtir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) {
        curtidas.descurtir(principal.usuarioId(), id);
    }
    @GetMapping("/curtidas")
    public CurtidaService.ResumoCurtidas resumo(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) {
        return curtidas.resumo(principal.usuarioId(), id);
    }
}
