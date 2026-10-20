package com.jambuapp.api.foto;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import com.jambuapp.api.comum.RegraNegocioException;

public class ArmazenamentoFotosLocal implements ArmazenamentoFotos {
    private final Path diretorioBase;

    public ArmazenamentoFotosLocal(Path diretorioBase) {
        try {
            this.diretorioBase = diretorioBase.toAbsolutePath().normalize();
            Files.createDirectories(this.diretorioBase);
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível inicializar o diretório privado de fotos.", ex);
        }
    }

    @Override
    public void gravar(String chave, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0)
            throw RegraNegocioException.invalido("Conteúdo da foto ausente.");
        Path destino = resolverSeguro(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            throw RegraNegocioException.conflito("Chave de foto já existente; sobrescrita não é permitida.");
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gravar arquivo privado de foto.", ex);
        }
    }

    @Override
    public byte[] ler(String chave) {
        Path arquivo = resolverSeguro(chave);
        if (!Files.isRegularFile(arquivo)) throw RegraNegocioException.inexistente();
        try {
            return Files.readAllBytes(arquivo);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao ler arquivo privado de foto.", ex);
        }
    }

    @Override
    public void apagar(String chave) {
        Path arquivo = resolverSeguro(chave);
        try {
            Files.deleteIfExists(arquivo);
            Path pastaFoto = arquivo.getParent();
            if (pastaFoto != null && !pastaFoto.equals(diretorioBase) && Files.isDirectory(pastaFoto)) {
                try (Stream<Path> filhos = Files.list(pastaFoto)) {
                    if (filhos.findAny().isEmpty()) Files.deleteIfExists(pastaFoto);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao apagar arquivo privado de foto.", ex);
        }
    }

    @Override
    public boolean existe(String chave) {
        return Files.isRegularFile(resolverSeguro(chave));
    }

    @Override
    public Set<String> listarChaves() {
        if (!Files.isDirectory(diretorioBase)) return Set.of();
        Set<String> chaves = new HashSet<>();
        try (Stream<Path> caminhos = Files.walk(diretorioBase, 4)) {
            caminhos.filter(Files::isRegularFile).forEach(caminho -> {
                String relativa = diretorioBase.relativize(caminho).toString().replace('\\', '/');
                if (PADRAO_CHAVE.matcher(relativa).matches()) chaves.add(relativa);
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inspecionar arquivos de fotos.", ex);
        }
        return Set.copyOf(chaves);
    }

    private Path resolverSeguro(String chave) {
        ArmazenamentoFotos.validarChave(chave);
        Path resolvido = diretorioBase.resolve(chave).normalize();
        if (!resolvido.startsWith(diretorioBase))
            throw RegraNegocioException.invalido("Caminho fora do diretório de armazenamento.");
        return resolvido;
    }
}
