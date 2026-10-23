package com.jambuapp.api.autenticacao;

import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.jambuapp.api.comum.RegraNegocioException;

/** O refresh web nunca é devolvido ao JavaScript no corpo da resposta. */
@RestController @RequestMapping("/api/auth/web")
public class AutenticacaoWebController {
    private static final String COOKIE = "jambu_refresh";
    private final AutenticacaoService service;
    private final AuthProperties config;
    private final Clock clock;
    public AutenticacaoWebController(AutenticacaoService service, AuthProperties config, Clock clock) {
        this.service = service; this.config = config; this.clock = clock;
    }
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }
    @PostMapping("/login")
    public ResponseEntity<AcessoWebResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String descricao) {
        return responder(service.loginWeb(request, descricao));
    }
    @PostMapping("/refresh")
    public ResponseEntity<AcessoWebResponse> refresh(@CookieValue(name = COOKIE, required = false) String token,
            @Valid @RequestBody OperacaoWebRequest request) {
        return responder(service.renovar(token, request.operacaoId(), "WEB"));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token) {
        if (token != null) {
            try { service.logoutRefresh(token, "WEB"); }
            catch (RegraNegocioException ex) { if (ex.getStatus() != HttpStatus.UNAUTHORIZED) throw ex; }
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }
    private ResponseEntity<AcessoWebResponse> responder(SessaoResponse s) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), Duration.between(clock.instant(), s.expiraSessaoEm())))
                .body(new AcessoWebResponse(s.token(), s.expiraEm(), s.sessaoId(), s.expiraSessaoEm()));
    }
    private String cookie(String token, Duration prazo) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(config.cookieSeguro()).sameSite("Strict")
                .path("/api/auth/web").maxAge(prazo.isNegative() ? Duration.ZERO : prazo).build().toString();
    }
    public record OperacaoWebRequest(@NotNull UUID operacaoId) {}
    public record AcessoWebResponse(String token, Instant expiraEm, UUID sessaoId, Instant expiraSessaoEm) {}
}
