package com.jambuapp.api.foto;

import java.util.List;
import java.util.UUID;
import com.jambuapp.api.comum.Hash;
import com.jambuapp.api.comum.PaginaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.NEVER)
public class ModeracaoService {
    private final ModeracaoTransacionalService transacional;
    private final FotoService fotoService;
    private final ArmazenamentoFotos armazenamento;
    private final ProvedorModeracaoImagem provedor;
    private final PoliticaModeracao politica;
    private final FotoProperties config;

    public ModeracaoService(ModeracaoTransacionalService transacional, FotoService fotoService,
            ArmazenamentoFotos armazenamento, ProvedorModeracaoImagem provedor,
            PoliticaModeracao politica, FotoProperties config) {
        this.transacional = transacional;
        this.fotoService = fotoService;
        this.armazenamento = armazenamento;
        this.provedor = provedor;
        this.politica = politica;
        this.config = config;
    }

    public boolean processar(UUID tarefaId) {
        var reservaOpt = transacional.reservar(tarefaId);
        if (reservaOpt.isEmpty()) return false;
        var reserva = reservaOpt.get();

        byte[] conteudo;
        try {
            conteudo = armazenamento.ler(reserva.chaveArquivo());
        } catch (RuntimeException ex) {
            transacional.registrarFalhaTecnica(reserva.tarefaId(), reserva.tokenReserva(),
                    "Arquivo da foto indisponível no armazenamento privado durante a análise.");
            return false;
        }

        String hashLido = Hash.sha256Bytes(conteudo);
        if (!hashLido.equals(reserva.hashConteudo())) {
            transacional.registrarFalhaTecnica(reserva.tarefaId(), reserva.tokenReserva(),
                    "Hash do arquivo divergente do registrado na tarefa de moderação.");
            return false;
        }

        ResultadoProvedor resultadoProvedor;
        try {
            resultadoProvedor = provedor.analisar(conteudo);
        } catch (RuntimeException ex) {
            transacional.registrarFalhaTecnica(reserva.tarefaId(), reserva.tokenReserva(),
                    "Não foi possível concluir a análise automática no provedor configurado.");
            return false;
        }

        var decisao = politica.avaliar(resultadoProvedor);
        return transacional.concluirAutomatica(
                reserva.tarefaId(), reserva.tokenReserva(), hashLido, resultadoProvedor, decisao);
    }

    public int processarPendentes() {
        return processarLote(config.loteModeracao());
    }

    public int processarLote(int limite) {
        int processadas = 0;
        List<UUID> ids = transacional.buscarIdsProntos(limite);
        for (UUID id : ids) {
            if (processar(id)) processadas++;
        }
        return processadas;
    }

    public FotoResponse revisar(UUID revisorId, String revisorUsername, UUID fotoId,
            ResultadoModeracao resultado, String motivo) {
        var revisao = transacional.revisarHumana(revisorId, revisorUsername, fotoId, resultado, motivo);
        fotoService.limparSubstituidas(revisao.removidas());
        return revisao.resposta();
    }

    public void removerAdministrativamente(UUID revisorId, String revisorUsername, UUID fotoId, String motivo) {
        fotoService.limparSubstituidas(transacional.removerAdministrativamente(revisorId, revisorUsername, fotoId, motivo));
    }

    public byte[] obterConteudoParaModerador(String revisorUsername, UUID fotoId) {
        String chave = transacional.obterChaveParaInspecaoModerador(revisorUsername, fotoId);
        return armazenamento.ler(chave);
    }

    public FotoModeracaoAdminResponse consultarDetalhe(String revisorUsername, UUID fotoId) {
        return transacional.consultarDetalheAdmin(revisorUsername, fotoId);
    }

    public PaginaResponse<FotoModeracaoAdminResponse> listarFila(String revisorUsername,
            StatusModeracaoFoto status, int pagina, int limite) {
        return transacional.listarFilaAdmin(revisorUsername, status, pagina, limite);
    }
}
