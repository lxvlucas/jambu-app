package com.jambuapp.api.restaurante;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

public final class NomesRestaurantes {
    private NomesRestaurantes() {}
    public static String normalizar(String nome) {
        return Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
    public static String consultaPrefixos(String normalizado) {
        return Arrays.stream(normalizado.split(" ")).filter(s -> !s.isBlank())
                .map(s -> s + ":*").collect(Collectors.joining(" & "));
    }
    public static double similaridade(String primeiro, String segundo) {
        if (primeiro.equals(segundo)) return 1;
        var a = trigramas(primeiro);
        var b = trigramas(segundo);
        var comuns = new HashSet<>(a);
        comuns.retainAll(b);
        return a.isEmpty() || b.isEmpty() ? 0 : 2.0 * comuns.size() / (a.size() + b.size());
    }
    private static Set<String> trigramas(String nome) {
        Set<String> trios = new HashSet<>();
        for (String palavra : nome.split(" ")) {
            String texto = "  " + palavra + " ";
            for (int i = 0; i + 3 <= texto.length(); i++) trios.add(texto.substring(i, i + 3));
        }
        return trios;
    }
    public static double distancia(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.sin(Math.toRadians(lat2 - lat1) / 2);
        double lon = Math.sin(Math.toRadians(lon2 - lon1) / 2);
        double a = lat * lat + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * lon * lon;
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
