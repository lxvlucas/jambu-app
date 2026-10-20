package com.jambuapp.api.usuario;

import java.util.UUID;
import com.jambuapp.api.comum.PaginaResponse;
import com.jambuapp.api.meal.*;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import com.jambuapp.api.seguimento.SeguimentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService usuarios;
    private final SeguimentoService seguimentos;
    private final ConsultaMealService meals;
    public UsuarioController(UsuarioService usuarios, SeguimentoService seguimentos, ConsultaMealService meals) {
        this.usuarios = usuarios;
        this.seguimentos = seguimentos;
        this.meals = meals;
    }
    @GetMapping
    public PaginaResponse<UsuarioResumoResponse> buscar(@RequestParam(defaultValue = "") String termo,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int limite) {
        return usuarios.buscar(termo, pagina, limite);
    }
    @GetMapping("/me")
    public ContaResponse conta(@AuthenticationPrincipal UsuarioAutenticado principal) { return usuarios.conta(principal.usuarioId()); }
    @PutMapping("/me")
    public ContaResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado principal, @Valid @RequestBody AtualizarContaRequest request) {
        return usuarios.atualizar(principal.usuarioId(), request);
    }
    @GetMapping("/{id}")
    public PerfilResponse consultar(@PathVariable UUID id) { return usuarios.consultar(id); }
    @PutMapping("/{id}/seguimento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seguir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) { seguimentos.seguir(principal.usuarioId(), id); }
    @DeleteMapping("/{id}/seguimento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deixarDeSeguir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID id) { seguimentos.deixarDeSeguir(principal.usuarioId(), id); }
    @GetMapping("/{id}/seguidores")
    public PaginaResponse<UsuarioResumoResponse> seguidores(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int limite) {
        return seguimentos.listar(id, true, pagina, limite);
    }
    @GetMapping("/{id}/seguindo")
    public PaginaResponse<UsuarioResumoResponse> seguindo(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int limite) {
        return seguimentos.listar(id, false, pagina, limite);
    }
    @GetMapping("/{id}/meals")
    public PaginaCursor<MealResponse> meals(@PathVariable UUID id, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limite) {
        usuarios.consultar(id);
        return meals.listar(id, null, null, cursor, limite);
    }
}
