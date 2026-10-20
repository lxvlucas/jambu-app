package com.jambuapp.api.autenticacao;

import com.jambuapp.api.seguranca.UsuarioAutenticado;
import com.jambuapp.api.usuario.ContaResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {
    private final AutenticacaoService service;
    public AutenticacaoController(AutenticacaoService service) { this.service = service; }
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse cadastrar(@Valid @RequestBody CadastroRequest request) { return service.cadastrar(request); }
    @PostMapping("/login")
    public ResponseEntity<SessaoResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(request));
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UsuarioAutenticado principal) { service.revogar(principal.sessaoId()); }
}
