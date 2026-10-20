package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "registros_moderacao")
public class RegistroModeracao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "foto_id", nullable = false)
    private Foto foto;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemModeracao origem;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoModeracao resultado;
    @Column(nullable = false, length = 500)
    private String motivo;
    @Column(name = "versao_politica", nullable = false, length = 50)
    private String versaoPolitica;
    @Column(length = 100)
    private String provedor;
    @Column(name = "versao_modelo", length = 100)
    private String versaoModelo;
    @Column(name = "sinais_json")
    private String sinaisJson;
    @Column(name = "revisor_id")
    private UUID revisorId;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RegistroModeracao() {}

    private RegistroModeracao(Foto foto, OrigemModeracao origem, ResultadoModeracao resultado, String motivo,
            String versaoPolitica, String provedor, String versaoModelo, String sinaisJson, UUID revisorId, Instant criadoEm) {
        this.foto = foto;
        this.origem = origem;
        this.resultado = resultado;
        this.motivo = motivo;
        this.versaoPolitica = versaoPolitica;
        this.provedor = provedor;
        this.versaoModelo = versaoModelo;
        this.sinaisJson = sinaisJson;
        this.revisorId = revisorId;
        this.criadoEm = criadoEm;
    }

    public static RegistroModeracao automatico(Foto foto, ResultadoModeracao resultado, String motivo,
            String versaoPolitica, String provedor, String versaoModelo, String sinaisJson, Instant criadoEm) {
        return new RegistroModeracao(foto, OrigemModeracao.AUTOMATICA, resultado, motivo, versaoPolitica,
                provedor, versaoModelo, sinaisJson, null, criadoEm);
    }

    public static RegistroModeracao humano(Foto foto, ResultadoModeracao resultado, String motivo,
            String versaoPolitica, UUID revisorId, Instant criadoEm) {
        return new RegistroModeracao(foto, OrigemModeracao.HUMANA, resultado, motivo, versaoPolitica,
                null, null, null, revisorId, criadoEm);
    }

    public UUID getId() { return id; }
    public Foto getFoto() { return foto; }
    public OrigemModeracao getOrigem() { return origem; }
    public ResultadoModeracao getResultado() { return resultado; }
    public String getMotivo() { return motivo; }
    public String getVersaoPolitica() { return versaoPolitica; }
    public String getProvedor() { return provedor; }
    public String getVersaoModelo() { return versaoModelo; }
    public String getSinaisJson() { return sinaisJson; }
    public UUID getRevisorId() { return revisorId; }
    public Instant getCriadoEm() { return criadoEm; }
}
