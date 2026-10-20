package com.jambuapp.api.seguimento;

import java.time.Clock;
import java.util.UUID;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.usuario.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeguimentoService {
    private final UsuarioRepository usuarios;
    private final SeguimentoRepository seguimentos;
    private final Clock clock;
    public SeguimentoService(UsuarioRepository usuarios, SeguimentoRepository seguimentos, Clock clock) {
        this.usuarios = usuarios;
        this.seguimentos = seguimentos;
        this.clock = clock;
    }
    @Transactional
    public void seguir(UUID seguidorId, UUID seguidoId) {
        if (seguidorId.equals(seguidoId)) throw RegraNegocioException.invalido("Não é possível seguir a própria conta.");
        var seguidor = usuarios.buscarParaAtualizacao(seguidorId).orElseThrow(RegraNegocioException::inexistente);
        var seguido = usuarios.findById(seguidoId).orElseThrow(RegraNegocioException::inexistente);
        if (!seguimentos.existsBySeguidorIdAndSeguidoId(seguidorId, seguidoId))
            seguimentos.save(new Seguimento(seguidor, seguido, clock.instant()));
    }
    @Transactional
    public void deixarDeSeguir(UUID seguidorId, UUID seguidoId) {
        usuarios.buscarParaAtualizacao(seguidorId).orElseThrow(RegraNegocioException::inexistente);
        seguimentos.deleteBySeguidorIdAndSeguidoId(seguidorId, seguidoId);
    }
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResumoResponse> listar(UUID id, boolean seguidores, int pagina, int limite) {
        if (!usuarios.existsById(id)) throw RegraNegocioException.inexistente();
        var paginacao = Paginacao.pagina(pagina, limite, "criadoEm");
        var resultado = seguidores ? seguimentos.findBySeguidoId(id, paginacao) : seguimentos.findBySeguidorId(id, paginacao);
        return PaginaResponse.de(resultado.map(s -> UsuarioResumoResponse.de(seguidores ? s.getSeguidor() : s.getSeguido())));
    }
}
