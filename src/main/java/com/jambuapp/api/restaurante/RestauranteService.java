package com.jambuapp.api.restaurante;

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
    public RestauranteService(RestauranteLocalService local, ProvedorRestaurantes places, CotaPlacesService cota) {
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
        if (!locais.isEmpty() && !ampliar)
            return new SugestoesRestaurantesResponse(locais, List.of(), "NAO_SOLICITADO", true, true, sessao, null);
        if (usuarioId != null && !cota.permitirBusca(usuarioId))
            return new SugestoesRestaurantesResponse(locais, List.of(), "AGUARDE", true, true, sessao, null);
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
        var id = local.porGoogle(request.googlePlaceId()).orElseThrow(() ->
                new RegraNegocioException(HttpStatus.UNPROCESSABLE_CONTENT, "cadastro_necessario",
                        "Informe nome, pin e categoria próprios em POST /api/restaurantes/cadastros."));
        local.exigirSelecionavel(id);
        return resposta(local.consultar(id), null);
    }
    public RestauranteResponse consultar(UUID id) {
        var dados = local.consultar(id);
        // Conteúdo próprio/Overture torna o perfil independente do Google.
        if (dados.restaurante().nome() != null || dados.restaurante().googlePlaceId() == null)
            return resposta(dados, null);
        try { return resposta(dados, places.consultar(dados.restaurante().googlePlaceId(), null)); }
        catch (PlacesException ex) { return resposta(dados, null); }
    }
    public void exigirExistente(UUID id) { local.exigirExistente(id); }
    private RestauranteResponse resposta(RestauranteLocalService.DadosLocais dados, DadosLugar externo) {
        var r = dados.restaurante();
        return new RestauranteResponse(r.id(), r.googlePlaceId(),
                externo != null ? externo.nome() : r.nome(), externo != null ? externo.endereco() : r.endereco(),
                externo != null, r.origem().name(), externo == null ? null : externo.googleMapsUri(),
                externo == null ? List.of() : externo.atribuicoes(), externo == null ? null : "Google Maps",
                dados.notaMedia(), dados.quantidadeAvaliacoes(), r.situacao(), r.latitude(), r.longitude(),
                r.categoria(), r.overtureId(), r.atribuicaoCatalogo(), r.versao());
    }
}
