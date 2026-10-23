package com.jambuapp.api.autenticacao;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "sessoes")
public class Sessao {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;
    @Column(nullable = false)
    private Instant criadaEm;
    @Column(nullable = false)
    private Instant expiraEm;
    private Instant revogadaEm;
    @Column(nullable = false) private Instant absolutaEm;
    @Column(nullable = false) private Instant ultimaAtividadeEm;
    @Column(nullable = false) private Instant autenticadaEm;
    @Column(nullable = false, length = 10) private String canal;
    @Column(nullable = false, length = 120) private String descricao;
    protected Sessao() {}
    public Sessao(Usuario usuario, Instant criadaEm, Instant expiraEm, Instant absolutaEm, String canal, String descricao) {
        this.id = UUID.randomUUID();
        this.usuario = usuario;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
        this.absolutaEm = absolutaEm;
        this.ultimaAtividadeEm = criadaEm;
        this.autenticadaEm = criadaEm;
        this.canal = canal;
        String limpa = descricao == null ? "Dispositivo" : descricao.replaceAll("[\\p{Cntrl}]", " ");
        this.descricao = limpa.substring(0, Math.min(120, limpa.length()));
    }
    public boolean estaValida(Instant em) { return revogadaEm == null && em.isBefore(expiraEm) && em.isBefore(absolutaEm); }
    public void revogar(Instant em) { if (revogadaEm == null) revogadaEm = em; }
    public void renovar(Instant agora, Instant expira) { ultimaAtividadeEm = agora; expiraEm = expira; }
    public void reautenticar(Instant agora) { autenticadaEm = agora; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAbsolutaEm() { return absolutaEm; }
    public Instant getUltimaAtividadeEm() { return ultimaAtividadeEm; }
    public Instant getAutenticadaEm() { return autenticadaEm; }
    public String getCanal() { return canal; }
    public String getDescricao() { return descricao; }
    public UUID getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Instant getExpiraEm() { return expiraEm; }
}
