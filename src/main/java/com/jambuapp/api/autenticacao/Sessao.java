package com.jambuapp.api.autenticacao;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "sessoes")
public class Sessao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant criadaEm;
    @Column(nullable = false)
    private Instant expiraEm;
    private Instant revogadaEm;
    protected Sessao() {}
    public Sessao(Usuario usuario, String tokenHash, Instant criadaEm, Instant expiraEm) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
    }
    public boolean estaValida(Instant em) { return revogadaEm == null && em.isBefore(expiraEm); }
    public void revogar(Instant em) { revogadaEm = em; }
    public UUID getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Instant getExpiraEm() { return expiraEm; }
}
