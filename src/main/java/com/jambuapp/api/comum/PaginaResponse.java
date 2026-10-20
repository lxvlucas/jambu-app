package com.jambuapp.api.comum;

import java.util.List;
import org.springframework.data.domain.Page;

public record PaginaResponse<T>(List<T> itens, int pagina, long total) {
    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getTotalElements());
    }
}
