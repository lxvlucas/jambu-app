package com.jambuapp.api.foto;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.jambuapp.api.comum.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModeracaoTransacionalService {
    private final FotoRepository fotos;
    private final TarefaModeracaoRepository tarefas;
    private final RegistroModeracaoRepository registros;
    private final FotoProperties config;
    private final Clock clock;
    private final JsonMapper json;

    public ModeracaoTransacionalService(FotoRepository fotos,
            TarefaModeracaoRepository tarefas, RegistroModeracaoRepository registros,
            FotoProperties config, Clock clock, JsonMapper json) {
        this.fotos = fotos;
        this.tarefas = tarefas;
        this.registros = registros;
        this.config = config;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public List<UUID> buscarIdsProntos(int limite) {
        return tarefas.buscarIdsProntosParaReserva(agora(), limite);
    }

    @Transactional
    public Optional<ReservaTarefa> reservar(UUID tarefaId) {
        var opt = tarefas.buscarParaAtualizacao(tarefaId);
        if (opt.isEmpty()) return Optional.empty();
        var tarefa = opt.get();
        Instant agora = agora();
        if (tarefa.getEstado() == EstadoTarefa.CONCLUIDA || tarefa.getEstado() == EstadoTarefa.CANCELADA) {
            return Optional.empty();
        }
        var foto = fotos.buscarParaAtualizacao(tarefa.getFoto().getId()).orElseThrow();
        if (!foto.isAtual()
                || (foto.getStatus() != StatusModeracaoFoto.PENDENTE && foto.getStatus() != StatusModeracaoFoto.EM_ANALISE)
                || !foto.getHashConteudo().equals(tarefa.getHashConteudo())) {
            tarefa.cancelar(agora);
            return Optional.empty();
        }
        if (!tarefa.podeReservar(agora)) return Optional.empty();

        UUID token = tarefa.reservar(agora, agora.plus(config.duracaoReservaModeracao()));
        foto.iniciarAnalise(agora);
        fotos.flush();
        tarefas.flush();
        return Optional.of(new ReservaTarefa(
                tarefa.getId(), foto.getId(), token, foto.getChaveArquivo(),
                tarefa.getHashConteudo(), tarefa.getVersaoPolitica()));
    }

    @Transactional
    public boolean concluirAutomatica(UUID tarefaId, UUID tokenReserva, String hashAnalisado,
            ResultadoProvedor resultadoProvedor, PoliticaModeracao.DecisaoPolitica decisao) {
        var opt = tarefas.buscarParaAtualizacao(tarefaId);
        if (opt.isEmpty()) return false;
        var tarefa = opt.get();
        Instant agora = agora();
        if (tarefa.getEstado() != EstadoTarefa.EM_EXECUCAO || !Objects.equals(tokenReserva, tarefa.getTokenReserva())) {
            return false;
        }
        var foto = fotos.buscarParaAtualizacao(tarefa.getFoto().getId()).orElseThrow();
        if (!foto.isAtual() || foto.getStatus() != StatusModeracaoFoto.EM_ANALISE
                || !foto.getHashConteudo().equals(hashAnalisado)
                || !tarefa.getHashConteudo().equals(hashAnalisado)) {
            tarefa.cancelar(agora);
            return false;
        }
        foto.aplicarResultadoAutomatico(decisao.resultado(), hashAnalisado, agora);
        tarefa.concluir(tokenReserva, agora);
        String sinaisJson = resultadoProvedor.sinais() == null ? null : json.writeValueAsString(resultadoProvedor.sinais());
        registros.save(RegistroModeracao.automatico(
                foto, decisao.resultado(), decisao.motivo(), tarefa.getVersaoPolitica(),
                resultadoProvedor.provedor(), resultadoProvedor.versaoModelo(), sinaisJson, agora));
        fotos.flush();
        tarefas.flush();
        return true;
    }

    @Transactional
    public boolean registrarFalhaTecnica(UUID tarefaId, UUID tokenReserva, String motivo) {
        var opt = tarefas.buscarParaAtualizacao(tarefaId);
        if (opt.isEmpty()) return false;
        var tarefa = opt.get();
        Instant agora = agora();
        if (tarefa.getEstado() != EstadoTarefa.EM_EXECUCAO || !Objects.equals(tokenReserva, tarefa.getTokenReserva())) {
            return false;
        }
        var foto = fotos.buscarParaAtualizacao(tarefa.getFoto().getId()).orElseThrow();
        if (!foto.isAtual() || (foto.getStatus() != StatusModeracaoFoto.EM_ANALISE && foto.getStatus() != StatusModeracaoFoto.PENDENTE)) {
            tarefa.cancelar(agora);
            return false;
        }
        int proximaTentativaNumero = tarefa.getTentativas() + 1;
        long multiplicador = 1L << Math.min(10, Math.max(0, proximaTentativaNumero - 1));
        Instant proxima = agora.plus(config.atrasoBaseModeracao().multipliedBy(multiplicador));
        int tentativasRealizadas = tarefa.registrarFalha(tokenReserva, config.maxTentativasModeracao(), proxima, agora);

        if (tentativasRealizadas >= config.maxTentativasModeracao()) {
            foto.encaminharParaRevisaoPorFalha(agora);
            registros.save(RegistroModeracao.automatico(
                    foto, ResultadoModeracao.FALHA_TECNICA,
                    motivo + " Limite de tentativas atingido; encaminhada para revisão humana.",
                    tarefa.getVersaoPolitica(), null, null, null, agora));
        } else {
            foto.aplicarResultadoAutomatico(ResultadoModeracao.FALHA_TECNICA, foto.getHashConteudo(), agora);
            registros.save(RegistroModeracao.automatico(
                    foto, ResultadoModeracao.FALHA_TECNICA, motivo,
                    tarefa.getVersaoPolitica(), null, null, null, agora));
        }
        fotos.flush();
        tarefas.flush();
        return true;
    }

    @Transactional
    public FotoResponse revisarHumana(UUID revisorId, String revisorUsername, UUID fotoId,
            ResultadoModeracao resultado, String motivo) {
        exigirModerador(revisorUsername);
        if (resultado == null || resultado == ResultadoModeracao.FALHA_TECNICA)
            throw RegraNegocioException.invalido("Revisão humana exige APROVAR, REJEITAR ou REVISAR.");
        if (motivo == null || motivo.isBlank() || motivo.strip().length() > 500)
            throw RegraNegocioException.invalido("Informe um motivo de revisão com até 500 caracteres.");

        var foto = fotos.buscarParaAtualizacao(fotoId).orElseThrow(RegraNegocioException::inexistente);
        Instant agora = agora();
        for (TarefaModeracao tarefa : tarefas.buscarAtivasPorFotoParaAtualizacao(fotoId)) {
            tarefa.cancelar(agora);
        }
        foto.aplicarRevisaoHumana(resultado, agora);
        registros.save(RegistroModeracao.humano(
                foto, resultado, motivo.strip(), config.versaoPolitica(), revisorId, agora));
        fotos.flush();
        return FotoResponse.de(foto);
    }

    @Transactional
    public FotoTransacionalService.FotoSubstituida removerAdministrativamente(UUID revisorId, String revisorUsername,
            UUID fotoId, String motivo) {
        exigirModerador(revisorUsername);
        if (motivo == null || motivo.isBlank() || motivo.strip().length() > 500)
            throw RegraNegocioException.invalido("Informe um motivo administrativo com até 500 caracteres.");

        var foto = fotos.buscarParaAtualizacao(fotoId).orElseThrow(RegraNegocioException::inexistente);
        Instant agora = agora();
        for (TarefaModeracao tarefa : tarefas.buscarAtivasPorFotoParaAtualizacao(fotoId)) {
            tarefa.cancelar(agora);
        }
        foto.remover(agora);
        registros.save(RegistroModeracao.humano(
                foto, ResultadoModeracao.REJEITAR, "Remoção administrativa: " + motivo.strip(),
                config.versaoPolitica(), revisorId, agora));
        fotos.flush();
        return new FotoTransacionalService.FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura());
    }

    @Transactional(readOnly = true)
    public String obterChaveParaInspecaoModerador(String revisorUsername, UUID fotoId) {
        exigirModerador(revisorUsername);
        var foto = fotos.findById(fotoId).orElseThrow(RegraNegocioException::inexistente);
        if (foto.getArquivosLimposEm() != null) throw RegraNegocioException.inexistente();
        return foto.getChaveArquivo();
    }

    @Transactional(readOnly = true)
    public FotoModeracaoAdminResponse consultarDetalheAdmin(String revisorUsername, UUID fotoId) {
        exigirModerador(revisorUsername);
        var foto = fotos.buscarComAutor(fotoId).orElseThrow(RegraNegocioException::inexistente);
        var hist = registros.findByFotoIdOrderByCriadoEmAscIdAsc(fotoId);
        return FotoModeracaoAdminResponse.de(foto, hist);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<FotoModeracaoAdminResponse> listarFilaAdmin(String revisorUsername,
            StatusModeracaoFoto status, int pagina, int limite) {
        exigirModerador(revisorUsername);
        var pageable = Paginacao.pagina(pagina, limite, "criadaEm");
        Page<Foto> page = status == null
                ? fotos.findAllByOrderByCriadaEmDescIdDesc(pageable)
                : fotos.findByStatusOrderByCriadaEmAscIdAsc(status, pageable);
        return PaginaResponse.de(page.map(f -> FotoModeracaoAdminResponse.de(
                f, registros.findByFotoIdOrderByCriadoEmAscIdAsc(f.getId()))));
    }

    public void exigirModerador(String username) {
        if (!config.ehModerador(username)) throw RegraNegocioException.proibido();
    }

    private Instant agora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public record ReservaTarefa(UUID tarefaId, UUID fotoId, UUID tokenReserva, String chaveArquivo,
            String hashConteudo, String versaoPolitica) {}
}
