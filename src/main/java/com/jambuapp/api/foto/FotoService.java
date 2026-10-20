package com.jambuapp.api.foto;

import java.io.IOException;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(propagation = Propagation.NEVER)
public class FotoService {
    private final FotoTransacionalService transacional;
    private final ProcessadorImagemFoto processador;
    private final ArmazenamentoFotos armazenamento;

    public FotoService(FotoTransacionalService transacional, ProcessadorImagemFoto processador,
            ArmazenamentoFotos armazenamento) {
        this.transacional = transacional;
        this.processador = processador;
        this.armazenamento = armazenamento;
    }

    public FotoResponse enviarPerfil(UUID autorId, MultipartFile arquivo) {
        return enviarPerfilBytes(autorId, lerBytes(arquivo));
    }

    public FotoResponse enviarPerfilBytes(UUID autorId, byte[] bytes) {
        transacional.validarAutorPerfil(autorId);
        var imagem = processador.processar(bytes);
        UUID fotoId = UUID.randomUUID();
        String chavePrincipal = chavePrincipal(fotoId);
        String chaveMiniatura = chaveMiniatura(fotoId);

        gravarComRollbackEmFalha(chavePrincipal, imagem.principal(), chaveMiniatura, imagem.miniatura());
        FotoTransacionalService.ResultadoPersistenciaUpload resultado;
        try {
            resultado = transacional.registrarFotoPerfil(autorId, fotoId, chavePrincipal, chaveMiniatura, imagem);
        } catch (RuntimeException ex) {
            apagarSilenciosamente(chavePrincipal);
            apagarSilenciosamente(chaveMiniatura);
            throw ex;
        }
        limparSubstituidas(resultado.substituidas());
        return resultado.resposta();
    }

    public FotoResponse enviarDish(UUID autorId, UUID dishId, MultipartFile arquivo) {
        return enviarDishBytes(autorId, dishId, lerBytes(arquivo));
    }

    public FotoResponse enviarDishBytes(UUID autorId, UUID dishId, byte[] bytes) {
        transacional.validarAutorEDish(autorId, dishId);
        var imagem = processador.processar(bytes);
        UUID fotoId = UUID.randomUUID();
        String chavePrincipal = chavePrincipal(fotoId);
        String chaveMiniatura = chaveMiniatura(fotoId);

        gravarComRollbackEmFalha(chavePrincipal, imagem.principal(), chaveMiniatura, imagem.miniatura());
        FotoTransacionalService.ResultadoPersistenciaUpload resultado;
        try {
            resultado = transacional.registrarFotoDish(autorId, dishId, fotoId, chavePrincipal, chaveMiniatura, imagem);
        } catch (RuntimeException ex) {
            apagarSilenciosamente(chavePrincipal);
            apagarSilenciosamente(chaveMiniatura);
            throw ex;
        }
        limparSubstituidas(resultado.substituidas());
        return resultado.resposta();
    }

    public FotoResponse consultarStatus(UUID solicitanteId, String solicitanteUsername, UUID fotoId) {
        return transacional.consultarStatus(solicitanteId, solicitanteUsername, fotoId);
    }

    public byte[] obterConteudoAprovado(UUID fotoId) {
        return obterConteudoAprovado(fotoId, false);
    }

    public byte[] obterConteudoAprovado(UUID fotoId, boolean miniatura) {
        String chave = transacional.obterChaveAprovadaParaLeitura(fotoId, miniatura);
        return armazenamento.ler(chave);
    }

    public void remover(UUID autorId, UUID fotoId) {
        var removida = transacional.removerPorAutor(autorId, fotoId);
        limparSubstituidas(List.of(removida));
    }

    public void removerFotoPerfilAtual(UUID autorId) {
        transacional.removerFotoPerfilAtual(autorId).ifPresent(f -> limparSubstituidas(List.of(f)));
    }

    public FotoResponse contestar(UUID autorId, UUID fotoId, String motivo) {
        return transacional.contestar(autorId, fotoId, motivo);
    }

    public int limparArquivosExpiradosEOrfaos() {
        int removidos = 0;
        var elegiveis = transacional.listarElegiveisParaLimpeza();
        for (var item : elegiveis) {
            apagarSilenciosamente(item.chaveArquivo());
            apagarSilenciosamente(item.chaveMiniatura());
            transacional.marcarArquivosLimpos(item.fotoId());
            removidos++;
        }
        Set<String> retidas = transacional.buscarChavesRetidasNoBanco();
        for (String chaveNoStorage : armazenamento.listarChaves()) {
            if (!retidas.contains(chaveNoStorage)) {
                apagarSilenciosamente(chaveNoStorage);
                removidos++;
            }
        }
        return removidos;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void limparSubstituidas(List<FotoTransacionalService.FotoSubstituida> itens) {
        for (var item : itens) {
            apagarSilenciosamente(item.chaveArquivo());
            apagarSilenciosamente(item.chaveMiniatura());
            transacional.marcarArquivosLimpos(item.fotoId());
        }
    }

    private void gravarComRollbackEmFalha(String chavePrincipal, byte[] principal, String chaveMiniatura, byte[] miniatura) {
        armazenamento.gravar(chavePrincipal, principal);
        try {
            armazenamento.gravar(chaveMiniatura, miniatura);
        } catch (RuntimeException ex) {
            apagarSilenciosamente(chavePrincipal);
            throw ex;
        }
    }

    private void apagarSilenciosamente(String chave) {
        try {
            armazenamento.apagar(chave);
        } catch (RuntimeException ignored) {
        }
    }

    private static byte[] lerBytes(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty())
            throw RegraNegocioException.invalido("Envie um arquivo de imagem JPEG ou PNG válido.");
        if (arquivo.getSize() > ProcessadorImagemFoto.LIMITE_UPLOAD_BYTES)
            throw RegraNegocioException.invalido("O arquivo excede o limite de 8 MiB.");
        try {
            return arquivo.getBytes();
        } catch (IOException ex) {
            throw RegraNegocioException.invalido("Não foi possível ler o arquivo enviado.");
        }
    }

    public static String chavePrincipal(UUID fotoId) {
        return "fotos/" + fotoId + "/principal.jpg";
    }

    public static String chaveMiniatura(UUID fotoId) {
        return "fotos/" + fotoId + "/miniatura.jpg";
    }
}
