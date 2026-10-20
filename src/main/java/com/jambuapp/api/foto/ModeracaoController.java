package com.jambuapp.api.foto;

import java.util.UUID;
import com.jambuapp.api.comum.PaginaResponse;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderacao/fotos")
public class ModeracaoController {
    private final ModeracaoService moderacao;

    public ModeracaoController(ModeracaoService moderacao) {
        this.moderacao = moderacao;
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<FotoModeracaoAdminResponse>> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) StatusModeracaoFoto status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(moderacao.listarFila(principal.username(), status, pagina, limite));
    }

    @GetMapping("/{fotoId}")
    public ResponseEntity<FotoModeracaoAdminResponse> consultar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID fotoId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(moderacao.consultarDetalhe(principal.username(), fotoId));
    }

    @GetMapping("/{fotoId}/conteudo")
    public ResponseEntity<byte[]> obterConteudoParaRevisao(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID fotoId) {
        return FotoController.respostaImagem(moderacao.obterConteudoParaModerador(principal.username(), fotoId));
    }

    @PostMapping("/{fotoId}/revisao")
    public ResponseEntity<FotoResponse> revisar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID fotoId,
            @Valid @RequestBody RevisaoFotoRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(moderacao.revisar(principal.usuarioId(), principal.username(), fotoId, request.resultado(), request.motivo()));
    }

    @DeleteMapping("/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID fotoId,
            @RequestParam(defaultValue = "Removida pela moderação") String motivo) {
        moderacao.removerAdministrativamente(principal.usuarioId(), principal.username(), fotoId, motivo);
    }
}
