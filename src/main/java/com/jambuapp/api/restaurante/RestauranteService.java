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
    private final PlacesProperties config;
    public RestauranteService(RestauranteLocalService local, ProvedorRestaurantes places, PlacesProperties config) {
        this.local = local;
        this.places = places;
        this.config = config;
    }
    public SugestoesRestaurantesResponse buscar(String termo, UUID tokenSessao) {
        if (termo == null || termo.strip().length() < 2 || termo.length() > 100 || tokenSessao == null)
            throw RegraNegocioException.invalido("Informe termo de 2 a 100 caracteres e tokenSessao UUID do Places.");
        return new SugestoesRestaurantesResponse(places.sugerir(termo, tokenSessao), "Google Maps");
    }
    public RestauranteResponse selecionar(SelecionarRestauranteRequest request) {
        GooglePlacesAdapter.validarPlaceId(request.googlePlaceId());
        if (request.tokenSessao() == null) throw RegraNegocioException.invalido("Informe tokenSessao UUID do Places.");
        var dados = places.consultar(request.googlePlaceId(), request.tokenSessao());
        if (!request.googlePlaceId().equals(dados.googlePlaceId())) throw PlacesException.respostaInvalida();
        if (dados.tipos().stream().noneMatch(config.tiposAceitos()::contains))
            throw new PlacesException(HttpStatus.UNPROCESSABLE_CONTENT, "tipo_lugar_nao_permitido", "Selecione um estabelecimento de alimentação aceito.");
        // A chamada externa terminou antes de abrir a transação de registro da referência.
        return resposta(local.registrarSelecaoValidada(dados.googlePlaceId()), dados);
    }
    public RestauranteResponse consultar(UUID id) {
        var dados = local.consultar(id);
        if (dados.googlePlaceId() == null) return resposta(dados, null);
        try { return resposta(dados, places.consultar(dados.googlePlaceId(), null)); }
        catch (PlacesException ex) { return resposta(dados, null); }
    }
    public void exigirExistente(UUID id) { local.exigirExistente(id); }
    private RestauranteResponse resposta(RestauranteLocalService.DadosLocais local, DadosLugar externo) {
        boolean legado = local.googlePlaceId() == null;
        return new RestauranteResponse(local.id(), local.googlePlaceId(),
                externo != null ? externo.nome() : legado ? local.nomeLegado() : null,
                externo != null ? externo.endereco() : legado ? local.enderecoLegado() : null,
                externo != null, legado ? "LEGADO" : "GOOGLE_PLACES",
                externo == null ? null : externo.googleMapsUri(), externo == null ? List.of() : externo.atribuicoes(),
                externo == null ? null : "Google Maps", local.notaMedia(), local.quantidadeAvaliacoes());
    }
}
