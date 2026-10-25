package com.jambuapp.api.restaurante;

import java.net.URI;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.restaurante.places.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.NEVER)
public class RestauranteService {
    private final RestauranteLocalService local;
    private final ProvedorRestaurantes places;
    private final CotaPlacesService cota;
    private final CasamentoOvertureService casamento;
    private final CatalogoProperties config;
    private final BuscasSemResultadoService semResultado;
    public RestauranteService(RestauranteLocalService local, ProvedorRestaurantes places, CotaPlacesService cota,
            CasamentoOvertureService casamento, CatalogoProperties config, BuscasSemResultadoService semResultado) {
        this.casamento = casamento;
        this.config = config;
        this.semResultado = semResultado;
        this.local = local; this.places = places; this.cota = cota;
    }
    public SugestoesRestaurantesResponse buscar(String termo, UUID tokenSessao) {
        return buscar(termo, tokenSessao, false, null);
    }
    public SugestoesRestaurantesResponse buscar(String termo, UUID tokenSessao, boolean ampliar, UUID usuarioId) {
        if (termo == null || termo.strip().length() < 2 || termo.length() > 100
                || NomesRestaurantes.normalizar(termo).length() < 2)
            throw RegraNegocioException.invalido("Informe termo de 2 a 100 caracteres.");
        UUID sessao = tokenSessao == null ? UUID.randomUUID() : tokenSessao;
        var locais = local.buscar(termo);
        if (locais.isEmpty()) semResultado.registrar(termo);
        if (!locais.isEmpty() && !ampliar)
            return new SugestoesRestaurantesResponse(locais, List.of(), "NAO_SOLICITADO", true, true, sessao, null);
        // O Google só é consultado a partir de 3 letras; a busca local já roda com 2.
        if (NomesRestaurantes.normalizar(termo).length() < 3)
            return new SugestoesRestaurantesResponse(locais, List.of(), "TERMO_CURTO", false, true, sessao, null);
        String bloqueio = usuarioId == null ? null : cota.verificarBusca(usuarioId);
        if (bloqueio != null)
            return new SugestoesRestaurantesResponse(locais, List.of(), bloqueio, !"LIMITE_DIARIO".equals(bloqueio), true, sessao, null);
        try {
            var externos = places.sugerir(termo.strip(), sessao);
            return new SugestoesRestaurantesResponse(locais, externos,
                    externos.isEmpty() ? "SEM_RESULTADOS" : "DISPONIVEL", false, true, sessao, "Google Maps");
        } catch (PlacesException ex) {
            String estado = switch (ex.getCodigo()) {
                case "places_cota_esgotada" -> "COTA_ESGOTADA";
                case "places_nao_configurado" -> "DESATIVADO";
                default -> "INDISPONIVEL";
            };
            return new SugestoesRestaurantesResponse(locais, List.of(), estado, false, true, sessao, null);
        }
    }
    /** Compatibilidade: referências Google já existentes são reutilizadas sem HTTP. */
    public RestauranteResponse selecionar(SelecionarRestauranteRequest request) {
        GooglePlacesAdapter.validarPlaceId(request.googlePlaceId());
        var id = local.porGoogle(request.googlePlaceId()).or(() -> casar(request)).orElseThrow(() ->
                new RegraNegocioException(HttpStatus.UNPROCESSABLE_CONTENT, "cadastro_necessario",
                        "Informe nome, pin e categoria próprios em POST /api/restaurantes/cadastros."));
        local.exigirSelecionavel(id);
        return resposta(local.consultar(id));
    }
    /**
     * Caso (b) da RN-50 (T05): para cada escondido parecido com o texto digitado, pergunta ao Google, com o nosso
     * nome e o nosso ponto, se o lugar é o escolhido. Google fora do ar não impede o fluxo: vira caso (c).
     */
    private Optional<UUID> casar(SelecionarRestauranteRequest request) {
        if (!config.casamentoOverture() || request.textoBusca() == null) return Optional.empty();
        try {
            for (var c : casamento.candidatos(request.textoBusca()))
                if (places.buscarIds(c.nome(), c.latitude(), c.longitude()).contains(request.googlePlaceId()))
                    return casamento.criar(c.overtureId(), request.googlePlaceId());
        } catch (PlacesException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }
    /** O perfil nunca consulta o Google: a cota só afeta achar lugares novos. */
    public RestauranteResponse consultar(UUID id) {
        return resposta(local.consultar(id));
    }
    public void exigirExistente(UUID id) { local.exigirExistente(id); }
    /** "Você está aqui?" nunca consulta o Google. */
    public RestaurantesProximosResponse proximos(double latitude, double longitude) {
        return local.proximos(latitude, longitude);
    }
    private RestauranteResponse resposta(RestauranteLocalService.DadosLocais dados) {
        var r = dados.restaurante();
        return new RestauranteResponse(r.id(), r.googlePlaceId(), r.nome(), r.bairro(), r.endereco(),
                false, r.origem().name(), linkMaps(r), List.of(), null,
                dados.notaMedia(), dados.quantidadeAvaliacoes(), r.situacao(), r.latitude(), r.longitude(),
                r.categoria(), r.overtureId(), r.atribuicaoCatalogo(), r.versao(), dados.ultimaAvaliacaoEm());
    }
    /** Maps URLs não exigem chave nem são cobradas; query_place_id prevalece sobre query. */
    static URI linkMaps(RestauranteResumo r) {
        String base = "https://www.google.com/maps/search/?api=1&query=";
        String ponto = r.latitude() == null || r.longitude() == null ? null : r.latitude() + "," + r.longitude();
        if (r.googlePlaceId() != null) {
            String consulta = r.nome() != null ? r.nome() : ponto != null ? ponto : "restaurante";
            return URI.create(base + codificar(consulta) + "&query_place_id=" + codificar(r.googlePlaceId()));
        }
        return ponto == null ? null : URI.create(base + codificar(ponto));
    }
    private static String codificar(String valor) {
        return java.net.URLEncoder.encode(valor, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
