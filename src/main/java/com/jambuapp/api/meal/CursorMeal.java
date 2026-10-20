package com.jambuapp.api.meal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;

public record CursorMeal(Instant instante, UUID id) {
    public static CursorMeal ler(String valor) {
        if (valor == null) return new CursorMeal(null, null);
        try {
            if (valor.length() > 200) throw new IllegalArgumentException();
            String[] partes = new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8).split("\\|", -1);
            if (partes.length != 2) throw new IllegalArgumentException();
            Instant instante = Instant.parse(partes[0]);
            if (instante.isBefore(Instant.parse("2000-01-01T00:00:00Z")) || instante.isAfter(Instant.parse("9999-12-31T23:59:59Z")))
                throw new IllegalArgumentException();
            return new CursorMeal(instante, UUID.fromString(partes[1]));
        } catch (RuntimeException ex) { throw RegraNegocioException.invalido("Cursor inválido."); }
    }
    public String codificar() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((instante + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
}
