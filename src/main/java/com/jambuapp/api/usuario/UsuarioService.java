package com.jambuapp.api.usuario;

import java.util.UUID;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.foto.FotoPerfilRepository;
import com.jambuapp.api.foto.FotoPublicaResponse;
import com.jambuapp.api.foto.StatusModeracaoFoto;
import com.jambuapp.api.meal.MealRepository;
import com.jambuapp.api.seguimento.SeguimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final SeguimentoRepository seguimentos;
    private final MealRepository meals;
    private final FotoPerfilRepository fotosPerfil;
    public UsuarioService(UsuarioRepository usuarios, SeguimentoRepository seguimentos,
            MealRepository meals, FotoPerfilRepository fotosPerfil) {
        this.usuarios = usuarios;
        this.seguimentos = seguimentos;
        this.meals = meals;
        this.fotosPerfil = fotosPerfil;
    }
    public PaginaResponse<UsuarioResumoResponse> buscar(String termo, int pagina, int limite) {
        return PaginaResponse.de(usuarios.findByUsernameContainingIgnoreCase(Paginacao.termo(termo),
                Paginacao.pagina(pagina, limite, "username")).map(UsuarioResumoResponse::de));
    }
    public PerfilResponse consultar(UUID id) {
        var usuario = obter(id);
        var foto = fotosPerfil.findByUsuarioAtivoIdAndStatus(id, StatusModeracaoFoto.APROVADA)
                .map(FotoPublicaResponse::de)
                .orElse(null);
        return new PerfilResponse(id, usuario.getUsername(), seguimentos.countBySeguidoId(id),
                seguimentos.countBySeguidorId(id), meals.countByAutorIdAndExcluidaEmIsNull(id), foto);
    }
    public ContaResponse conta(UUID id) { return ContaResponse.de(obter(id)); }
    @Transactional
    public ContaResponse atualizar(UUID id, AtualizarContaRequest request) {
        var usuario = usuarios.buscarParaAtualizacao(id).orElseThrow(RegraNegocioException::inexistente);
        usuario.atualizar(request.username(), request.email(), request.celular());
        usuarios.flush();
        return ContaResponse.de(usuario);
    }
    private Usuario obter(UUID id) { return usuarios.findById(id).orElseThrow(RegraNegocioException::inexistente); }
}
