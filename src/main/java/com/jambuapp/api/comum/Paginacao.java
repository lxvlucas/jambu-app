package com.jambuapp.api.comum;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Paginacao {
    private Paginacao() {}
    public static PageRequest pagina(int pagina, int limite, String campo) {
        limite(limite);
        if (pagina < 0 || pagina > 10000) throw RegraNegocioException.invalido("Página fora do intervalo permitido.");
        return PageRequest.of(pagina, limite, Sort.by(campo).and(Sort.by("id")));
    }
    public static void limite(int limite) {
        if (limite < 1 || limite > 50) throw RegraNegocioException.invalido("O limite deve estar entre 1 e 50.");
    }
    public static String termo(String termo) {
        if (termo == null) return "";
        if (termo.length() > 100) throw RegraNegocioException.invalido("Busca limitada a 100 caracteres.");
        return termo.strip();
    }
}
