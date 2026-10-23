package com.jambuapp.api.autenticacao;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name = "tokens_renovacao")
public class TokenRenovacao {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Sessao sessao;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant criadaEm;
    @Column(nullable = false) private Instant expiraEm;
    private Instant consumidoEm;
    private UUID operacaoId;
    private UUID sucessorId;
    @Column(columnDefinition = "text") private String respostaCifrada;
    private Instant repeticaoAte;
    protected TokenRenovacao() {}
    public TokenRenovacao(Sessao sessao, String hash, Instant agora, Instant expira) {
        id = UUID.randomUUID(); this.sessao = sessao; tokenHash = hash; criadaEm = agora; expiraEm = expira;
    }
    public void consumir(UUID operacao, TokenRenovacao sucessor, String resposta, Instant agora, Instant repeticao) {
        consumidoEm = agora; operacaoId = operacao; sucessorId = sucessor.id; respostaCifrada = resposta; repeticaoAte = repeticao;
    }
    public UUID getId() { return id; }
    public Sessao getSessao() { return sessao; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getConsumidoEm() { return consumidoEm; }
    public UUID getOperacaoId() { return operacaoId; }
    public UUID getSucessorId() { return sucessorId; }
    public String getRespostaCifrada() { return respostaCifrada; }
    public Instant getRepeticaoAte() { return repeticaoAte; }
}
