package com.jambuapp.api.restaurante.overture;

import com.jambuapp.api.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderacao/restaurantes/importacoes/overture")
public class ImportacaoOvertureController {
    private final ImportacaoOvertureService service;
    public ImportacaoOvertureController(ImportacaoOvertureService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ImportacaoOvertureService.Resultado> importar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody ImportacaoOvertureRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.importar(principal.usuarioId(), request));
    }
}
