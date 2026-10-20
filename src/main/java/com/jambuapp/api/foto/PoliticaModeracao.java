package com.jambuapp.api.foto;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PoliticaModeracao {
    private static final Set<String> CATEGORIAS_PROIBIDAS = Set.of(
            "explicit_nudity", "nudity", "nudez_explicita",
            "pornography", "pornografia", "sexual_activity",
            "graphic_violence", "violence", "violencia_grafica",
            "visibly_disturbing", "corpses", "hanging", "self_harm", "child_exploitation");

    private static final Set<String> CATEGORIAS_SENSIVEIS_REVISAO = Set.of(
            "suggestive", "conteudo_sugestivo", "female_swimwear_or_underwear", "male_swimwear_or_underwear",
            "revealing_clothes", "hate_symbols", "simbolos_de_odio", "drugs", "drogas", "weapons", "armas",
            "rude_gestures", "gambling");

    // Contexto gastronômico legítimo: bebidas alcoólicas e pessoas ao fundo não reprovam fotos de pratos/ambiente.
    private static final Set<String> CATEGORIAS_PERMITIDAS_CONTEXTO = Set.of(
            "alcohol", "alcoholic_beverages", "bebida_alcoolica", "drinking", "wine", "beer",
            "person", "people", "pessoas_ao_fundo", "crowd", "food", "comida", "restaurant", "dining");

    private final FotoProperties config;

    public PoliticaModeracao(FotoProperties config) {
        this.config = config;
    }

    public String getVersao() {
        return config.versaoPolitica();
    }

    public ResultadoModeracao decidir(ResultadoProvedor resultado) {
        return avaliar(resultado).resultado();
    }

    public DecisaoPolitica avaliar(ResultadoProvedor resultado) {
        if (resultado == null || resultado.provedor() == null || resultado.provedor().isBlank() || resultado.sinais() == null)
            return new DecisaoPolitica(ResultadoModeracao.REVISAR, "Resultado do provedor incompleto ou sem sinais interpretáveis.");

        boolean requerRevisao = false;
        String motivoRevisao = null;

        for (var entry : resultado.sinais().entrySet()) {
            String bruta = entry.getKey();
            String valorTexto = entry.getValue();
            if (bruta == null || bruta.isBlank() || valorTexto == null || valorTexto.isBlank())
                return new DecisaoPolitica(ResultadoModeracao.REVISAR, "Sinal de moderação malformado requer revisão.");

            String categoria = RekognitionAdapter.normalizarCategoria(bruta);
            double confianca;
            try {
                confianca = Double.parseDouble(valorTexto.strip());
            } catch (NumberFormatException ex) {
                return new DecisaoPolitica(ResultadoModeracao.REVISAR, "Escala de sinal não numérica requer revisão humana.");
            }
            if (Double.isNaN(confianca) || Double.isInfinite(confianca) || confianca < 0.0 || confianca > 100.0)
                return new DecisaoPolitica(ResultadoModeracao.REVISAR, "Valor de confiança fora do intervalo esperado.");

            if (CATEGORIAS_PERMITIDAS_CONTEXTO.contains(categoria)) {
                continue;
            }
            if (CATEGORIAS_PROIBIDAS.contains(categoria)) {
                if (confianca >= config.limiarRejeicao()) {
                    return new DecisaoPolitica(ResultadoModeracao.REJEITAR,
                            "Conteúdo bloqueado pela política de moderação (" + categoria + ").");
                }
                if (confianca >= config.limiarRevisao()) {
                    requerRevisao = true;
                    motivoRevisao = "Sinal em faixa ambígua requer revisão humana (" + categoria + ").";
                }
            } else if (CATEGORIAS_SENSIVEIS_REVISAO.contains(categoria)) {
                if (confianca >= config.limiarRejeicao() && ("hate_symbols".equals(categoria) || "simbolos_de_odio".equals(categoria))) {
                    return new DecisaoPolitica(ResultadoModeracao.REJEITAR,
                            "Conteúdo bloqueado pela política de moderação (" + categoria + ").");
                }
                if (confianca >= config.limiarRevisao()) {
                    requerRevisao = true;
                    motivoRevisao = "Conteúdo sensível requer revisão humana (" + categoria + ").";
                }
            } else if (confianca >= config.limiarRevisao()) {
                requerRevisao = true;
                motivoRevisao = "Categoria não mapeada acima do limiar requer revisão (" + categoria + ").";
            }
        }

        if (requerRevisao) {
            return new DecisaoPolitica(ResultadoModeracao.REVISAR, motivoRevisao);
        }
        return new DecisaoPolitica(ResultadoModeracao.APROVAR, "Nenhum sinal proibido acima dos limiares da política.");
    }

    public record DecisaoPolitica(ResultadoModeracao resultado, String motivo) {}
}
