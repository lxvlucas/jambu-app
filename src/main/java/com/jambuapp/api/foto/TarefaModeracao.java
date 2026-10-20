package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.comum.RegraNegocioException;
import jakarta.persistence.*;

@Entity
@Table(name = "tarefas_moderacao")
public class TarefaModeracao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Version
    private long versao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "foto_id", nullable = false)
    private Foto foto;
    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;
    @Column(name = "versao_politica", nullable = false, length = 50)
    private String versaoPolitica;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTarefa estado;
    @Column(nullable = false)
    private int tentativas;
    @Column(name = "proxima_tentativa_em", nullable = false)
    private Instant proximaTentativaEm;
    @Column(name = "reserva_ate")
    private Instant reservaAte;
    @Column(name = "token_reserva")
    private UUID tokenReserva;
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;
    @Column(name = "concluida_em")
    private Instant concluidaEm;

    protected TarefaModeracao() {}

    public TarefaModeracao(Foto foto, String versaoPolitica, Instant agora) {
        this.foto = foto;
        this.hashConteudo = foto.getHashConteudo();
        this.versaoPolitica = versaoPolitica;
        this.estado = EstadoTarefa.AGUARDANDO;
        this.tentativas = 0;
        this.proximaTentativaEm = agora;
        this.criadaEm = agora;
    }

    public boolean podeReservar(Instant agora) {
        return estado == EstadoTarefa.AGUARDANDO && !proximaTentativaEm.isAfter(agora)
                || estado == EstadoTarefa.EM_EXECUCAO && reservaAte != null && !reservaAte.isAfter(agora);
    }

    public UUID reservar(Instant agora, Instant ate) {
        if (!podeReservar(agora))
            throw RegraNegocioException.conflito("Tarefa não está disponível para reserva.");
        this.tokenReserva = UUID.randomUUID();
        this.estado = EstadoTarefa.EM_EXECUCAO;
        this.reservaAte = ate;
        return this.tokenReserva;
    }

    public boolean possuiReservaAtiva(UUID token, Instant agora) {
        return estado == EstadoTarefa.EM_EXECUCAO && token != null && token.equals(tokenReserva)
                && reservaAte != null && reservaAte.isAfter(agora);
    }

    public void concluir(UUID token, Instant agora) {
        exigirTokenReserva(token);
        this.estado = EstadoTarefa.CONCLUIDA;
        this.reservaAte = null;
        this.tokenReserva = null;
        this.concluidaEm = agora;
    }

    public int registrarFalha(UUID token, int maxTentativas, Instant proxima, Instant agora) {
        exigirTokenReserva(token);
        this.tentativas++;
        this.reservaAte = null;
        this.tokenReserva = null;
        if (this.tentativas >= maxTentativas) {
            this.estado = EstadoTarefa.CONCLUIDA;
            this.concluidaEm = agora;
        } else {
            this.estado = EstadoTarefa.AGUARDANDO;
            this.proximaTentativaEm = proxima;
        }
        return this.tentativas;
    }

    public void cancelar(Instant agora) {
        if (estado == EstadoTarefa.AGUARDANDO || estado == EstadoTarefa.EM_EXECUCAO) {
            this.estado = EstadoTarefa.CANCELADA;
            this.reservaAte = null;
            this.tokenReserva = null;
            this.concluidaEm = agora;
        }
    }

    private void exigirTokenReserva(UUID token) {
        if (estado != EstadoTarefa.EM_EXECUCAO || token == null || !token.equals(tokenReserva))
            throw RegraNegocioException.conflito("Token de reserva inválido ou expirado para concluir a tarefa.");
    }

    public UUID getId() { return id; }
    public Foto getFoto() { return foto; }
    public String getHashConteudo() { return hashConteudo; }
    public String getVersaoPolitica() { return versaoPolitica; }
    public EstadoTarefa getEstado() { return estado; }
    public int getTentativas() { return tentativas; }
    public Instant getProximaTentativaEm() { return proximaTentativaEm; }
    public Instant getReservaAte() { return reservaAte; }
    public UUID getTokenReserva() { return tokenReserva; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getConcluidaEm() { return concluidaEm; }
}
