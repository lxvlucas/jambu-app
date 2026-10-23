package com.jambuapp.api.autenticacao;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name = "tokens_acesso")
public class TokenAcesso {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Sessao sessao;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant expiraEm;
    protected TokenAcesso() {}
    public TokenAcesso(Sessao sessao, String hash, Instant expira) {
        id = UUID.randomUUID(); this.sessao = sessao; tokenHash = hash; expiraEm = expira;
    }
    public Sessao getSessao() { return sessao; }
    public Instant getExpiraEm() { return expiraEm; }
}
