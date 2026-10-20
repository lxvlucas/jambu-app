package com.jambuapp.api.foto;

import java.util.Set;
import java.util.regex.Pattern;
import com.jambuapp.api.comum.RegraNegocioException;

public interface ArmazenamentoFotos {
    Pattern PADRAO_CHAVE = Pattern.compile("^fotos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/(principal|miniatura)\\.jpg$");

    void gravar(String chave, byte[] conteudo);
    byte[] ler(String chave);
    void apagar(String chave);
    boolean existe(String chave);

    default Set<String> listarChaves() {
        return Set.of();
    }

    static void validarChave(String chave) {
        if (chave == null || !PADRAO_CHAVE.matcher(chave).matches())
            throw RegraNegocioException.invalido("Chave de armazenamento de foto inválida.");
    }
}
