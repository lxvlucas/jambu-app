package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "fotos_perfil")
public class FotoPerfil extends Foto {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(name = "usuario_ativo_id", unique = true)
    private UUID usuarioAtivoId;

    protected FotoPerfil() {}

    public FotoPerfil(UUID id, Usuario usuario, String chaveArquivo, String chaveMiniatura, String hashConteudo,
            int largura, int altura, int tamanhoBytes, int tamanhoMiniaturaBytes, Instant agora) {
        super(id, usuario, chaveArquivo, chaveMiniatura, hashConteudo, largura, altura, tamanhoBytes, tamanhoMiniaturaBytes, agora);
        this.usuario = usuario;
        this.usuarioAtivoId = usuario.getId();
    }

    @Override
    protected boolean vinculoAtivoParaLeitura() {
        return usuarioAtivoId != null;
    }

    @Override
    protected void desativarVinculoAtual() {
        this.usuarioAtivoId = null;
    }

    public Usuario getUsuario() { return usuario; }
    public UUID getUsuarioAtivoId() { return usuarioAtivoId; }
}
