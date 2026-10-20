package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "fotos")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Foto {
    @Id
    private UUID id;
    @Version
    private long versao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @Column(name = "chave_arquivo", nullable = false, unique = true, length = 255)
    private String chaveArquivo;
    @Column(name = "chave_miniatura", nullable = false, unique = true, length = 255)
    private String chaveMiniatura;
    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;
    @Column(nullable = false)
    private int largura;
    @Column(nullable = false)
    private int altura;
    @Column(name = "tamanho_bytes", nullable = false)
    private int tamanhoBytes;
    @Column(name = "tamanho_miniatura_bytes", nullable = false)
    private int tamanhoMiniaturaBytes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusModeracaoFoto status;
    @Column(nullable = false)
    private boolean atual;
    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;
    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;
    @Column(name = "removida_em")
    private Instant removidaEm;
    @Column(name = "arquivos_limpos_em")
    private Instant arquivosLimposEm;

    protected Foto() {}

    protected Foto(UUID id, Usuario autor, String chaveArquivo, String chaveMiniatura, String hashConteudo,
            int largura, int altura, int tamanhoBytes, int tamanhoMiniaturaBytes, Instant agora) {
        this.id = id;
        this.autor = autor;
        this.chaveArquivo = chaveArquivo;
        this.chaveMiniatura = chaveMiniatura;
        this.hashConteudo = hashConteudo;
        this.largura = largura;
        this.altura = altura;
        this.tamanhoBytes = tamanhoBytes;
        this.tamanhoMiniaturaBytes = tamanhoMiniaturaBytes;
        this.status = StatusModeracaoFoto.PENDENTE;
        this.atual = true;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public boolean estaAprovada() {
        return atual && removidaEm == null && status == StatusModeracaoFoto.APROVADA && vinculoAtivoParaLeitura();
    }

    protected abstract boolean vinculoAtivoParaLeitura();
    protected abstract void desativarVinculoAtual();

    public void iniciarAnalise(Instant agora) {
        if (!atual || status == StatusModeracaoFoto.REMOVIDA)
            throw RegraNegocioException.conflito("Foto não está elegível para análise.");
        this.status = StatusModeracaoFoto.EM_ANALISE;
        this.atualizadaEm = agora;
    }

    public void aplicarResultadoAutomatico(ResultadoModeracao resultado, String hashAnalisado, Instant agora) {
        if (!atual || status != StatusModeracaoFoto.EM_ANALISE || !hashConteudo.equals(hashAnalisado))
            throw RegraNegocioException.conflito("A foto mudou ou não aguarda resultado automático para este arquivo.");
        this.status = switch (resultado) {
            case APROVAR -> StatusModeracaoFoto.APROVADA;
            case REJEITAR -> StatusModeracaoFoto.REJEITADA;
            case REVISAR -> StatusModeracaoFoto.EM_REVISAO;
            case FALHA_TECNICA -> StatusModeracaoFoto.PENDENTE;
        };
        this.atualizadaEm = agora;
    }

    public void encaminharParaRevisaoPorFalha(Instant agora) {
        if (atual && (status == StatusModeracaoFoto.EM_ANALISE || status == StatusModeracaoFoto.PENDENTE)) {
            this.status = StatusModeracaoFoto.EM_REVISAO;
            this.atualizadaEm = agora;
        }
    }

    public void aplicarRevisaoHumana(ResultadoModeracao resultado, Instant agora) {
        if (!atual || status == StatusModeracaoFoto.REMOVIDA)
            throw RegraNegocioException.conflito("Foto removida ou substituída não pode ser revisada.");
        this.status = switch (resultado) {
            case APROVAR -> StatusModeracaoFoto.APROVADA;
            case REJEITAR -> StatusModeracaoFoto.REJEITADA;
            case REVISAR -> StatusModeracaoFoto.EM_REVISAO;
            case FALHA_TECNICA -> throw RegraNegocioException.invalido("Revisão humana exige decisão APROVAR, REJEITAR ou REVISAR.");
        };
        this.atualizadaEm = agora;
    }

    public void solicitarContestacao(Instant agora) {
        if (!atual || status != StatusModeracaoFoto.REJEITADA)
            throw RegraNegocioException.conflito("Somente uma foto atual rejeitada pode ser contestada.");
        this.status = StatusModeracaoFoto.EM_REVISAO;
        this.atualizadaEm = agora;
    }

    public void remover(Instant agora) {
        this.atual = false;
        this.status = StatusModeracaoFoto.REMOVIDA;
        this.atualizadaEm = agora;
        if (this.removidaEm == null) this.removidaEm = agora;
        desativarVinculoAtual();
    }

    public void marcarArquivosLimpos(Instant agora) {
        this.arquivosLimposEm = agora;
    }

    public UUID getId() { return id; }
    public long getVersao() { return versao; }
    public Usuario getAutor() { return autor; }
    public String getChaveArquivo() { return chaveArquivo; }
    public String getChaveMiniatura() { return chaveMiniatura; }
    public String getHashConteudo() { return hashConteudo; }
    public int getLargura() { return largura; }
    public int getAltura() { return altura; }
    public int getTamanhoBytes() { return tamanhoBytes; }
    public int getTamanhoMiniaturaBytes() { return tamanhoMiniaturaBytes; }
    public StatusModeracaoFoto getStatus() { return status; }
    public boolean isAtual() { return atual; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public Instant getRemovidaEm() { return removidaEm; }
    public Instant getArquivosLimposEm() { return arquivosLimposEm; }
}
