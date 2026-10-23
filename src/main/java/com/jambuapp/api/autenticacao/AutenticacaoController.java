package com.jambuapp.api.autenticacao;

import com.jambuapp.api.seguranca.UsuarioAutenticado;
import com.jambuapp.api.usuario.ContaResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import tools.jackson.databind.json.JsonMapper;
import com.jambuapp.api.comum.RegraNegocioException;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {
    private final AutenticacaoService service;
    private final AuthProperties config;
    private final JsonMapper json;
    private final Validator validator;
    public AutenticacaoController(AutenticacaoService service, AuthProperties config, JsonMapper json, Validator validator) {
        this.service = service; this.config = config; this.json = json; this.validator = validator;
    }
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse cadastrar(@Valid @RequestBody CadastroRequest request) { return service.cadastrar(request); }
    @PostMapping("/login")
    public ResponseEntity<SessaoResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(request));
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String header,
            HttpServletRequest request) throws java.io.IOException {
        // Clientes existentes enviam JSON/formulário vazio; o corpo opcional é limitado.
        byte[] corpo = request.getInputStream().readNBytes(1025);
        if (corpo.length > 1024) throw RegraNegocioException.invalido("Corpo de logout excede o limite.");
        if (corpo.length > 0) {
            LogoutRequest pedido;
            try {
                var tipo = MediaType.parseMediaType(request.getContentType());
                if (!MediaType.APPLICATION_JSON.isCompatibleWith(tipo)) throw new IllegalArgumentException();
                pedido = json.readValue(corpo, LogoutRequest.class);
            } catch (Exception ex) { throw RegraNegocioException.invalido("Informe refreshToken em JSON válido."); }
            if (pedido == null || !validator.validate(pedido).isEmpty()) throw RegraNegocioException.invalido("Informe refreshToken válido.");
            service.logoutRefresh(pedido.refreshToken(), "NATIVO");
            return;
        }
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        service.revogar(service.validarAcesso(token).sessaoId());
    }
    @PostMapping("/refresh")
    public ResponseEntity<SessaoResponse> renovar(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.renovar(request.refreshToken(), request.operacaoId(), "NATIVO"));
    }
    @GetMapping("/sessoes")
    public ResponseEntity<List<AutenticacaoService.SessaoResumoResponse>> sessoes(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(principal));
    }
    @DeleteMapping("/sessoes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revogar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) { service.revogarPropria(principal, id); }
    @PostMapping("/logout-todos")
    public ResponseEntity<Void> revogarTodas(@AuthenticationPrincipal UsuarioAutenticado principal) {
        service.revogarTodas(principal);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, ResponseCookie.from("jambu_refresh", "")
                .httpOnly(true).secure(config.cookieSeguro()).sameSite("Strict").path("/api/auth/web").maxAge(0).build().toString()).build();
    }
    @PostMapping("/reautenticar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reautenticar(@AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody ReautenticarRequest request) {
        service.reautenticar(principal, request.senha());
    }
}
