package com.jambuapp.api.foto;

import java.net.URI;
import java.util.UUID;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FotoController {
    private final FotoService fotos;

    public FotoController(FotoService fotos) {
        this.fotos = fotos;
    }

    @PostMapping(value = {"/api/usuarios/me/foto", "/api/fotos/perfil"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoResponse> enviarPerfil(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam("arquivo") MultipartFile arquivo) {
        var resposta = fotos.enviarPerfil(principal.usuarioId(), arquivo);
        return ResponseEntity.created(URI.create("/api/fotos/" + resposta.id()))
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    /** T16: foto do post, enviada quando a pessoa toca em Publicar; vai na Meal pelo `id` em `fotoIds`. */
    @PostMapping(value = "/api/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoResponse> enviarSolta(@AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam("arquivo") MultipartFile arquivo) {
        var resposta = fotos.enviarSolta(principal.usuarioId(), arquivo);
        return ResponseEntity.created(URI.create("/api/fotos/" + resposta.id()))
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    @DeleteMapping("/api/usuarios/me/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerPerfilAtual(@AuthenticationPrincipal UsuarioAutenticado principal) {
        fotos.removerFotoPerfilAtual(principal.usuarioId());
    }

    @GetMapping({"/api/fotos/{fotoId}", "/api/fotos/{fotoId}/status"})
    public ResponseEntity<FotoResponse> consultarStatus(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID fotoId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fotos.consultarStatus(principal.usuarioId(), fotoId));
    }

    @GetMapping("/api/fotos/{fotoId}/conteudo")
    public ResponseEntity<byte[]> obterConteudo(@PathVariable UUID fotoId,
            @RequestParam(defaultValue = "false") boolean miniatura) {
        return respostaImagem(fotos.obterConteudoAprovado(fotoId, miniatura));
    }

    @GetMapping("/api/fotos/{fotoId}/miniatura")
    public ResponseEntity<byte[]> obterMiniatura(@PathVariable UUID fotoId) {
        return respostaImagem(fotos.obterConteudoAprovado(fotoId, true));
    }

    @PostMapping("/api/fotos/{fotoId}/contestacao")
    public ResponseEntity<FotoResponse> contestar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable UUID fotoId, @Valid @RequestBody ContestacaoFotoRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fotos.contestar(principal.usuarioId(), fotoId, request.motivo()));
    }

    @DeleteMapping("/api/fotos/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable UUID fotoId) {
        fotos.remover(principal.usuarioId(), fotoId);
    }

    static ResponseEntity<byte[]> respostaImagem(byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
