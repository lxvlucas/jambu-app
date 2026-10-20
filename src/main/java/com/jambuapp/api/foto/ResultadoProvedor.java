package com.jambuapp.api.foto;

import java.util.Map;

public record ResultadoProvedor(String provedor, String versaoModelo, Map<String, String> sinais) {
    public ResultadoProvedor {
        if (sinais != null) sinais = Map.copyOf(sinais);
    }
}
