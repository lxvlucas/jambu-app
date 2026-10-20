package com.jambuapp.api.meal;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "publicacoes_idempotentes", uniqueConstraints = @UniqueConstraint(columnNames = {"autor_id", "chave"}))
public class PublicacaoIdempotente {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario autor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Meal resultado;
    @Column(nullable = false, length = 128)
    private String chave;
    @Column(nullable = false, length = 64)
    private String hashRequisicao;
    @Column(nullable = false, columnDefinition = "text")
    private String respostaJson;
    @Column(nullable = false)
    private Instant criadaEm;
    protected PublicacaoIdempotente() {}
    public PublicacaoIdempotente(Usuario autor, Meal resultado, String chave, String hashRequisicao, String respostaJson, Instant criadaEm) {
        this.autor = autor;
        this.resultado = resultado;
        this.chave = chave;
        this.hashRequisicao = hashRequisicao;
        this.respostaJson = respostaJson;
        this.criadaEm = criadaEm;
    }
    public boolean correspondeAo(String hash) { return hashRequisicao.equals(hash); }
    public String getRespostaJson() { return respostaJson; }
}
