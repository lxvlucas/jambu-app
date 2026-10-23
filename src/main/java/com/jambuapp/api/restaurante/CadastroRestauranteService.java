package com.jambuapp.api.restaurante;

import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.restaurante.places.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.NEVER)
public class CadastroRestauranteService {
    private final CatalogoTransacionalService catalogo;
    private final RestauranteLocalService local;
    private final ProvedorRestaurantes places;
    private final PlacesProperties config;
    private final AreaCatalogo area;
    private final Validator validator;
    public CadastroRestauranteService(CatalogoTransacionalService catalogo, RestauranteLocalService local,
            ProvedorRestaurantes places, PlacesProperties config, AreaCatalogo area, Validator validator) {
        this.catalogo = catalogo; this.local = local; this.places = places; this.config = config;
        this.area = area; this.validator = validator;
    }
    public CadastroRestauranteResponse cadastrar(UUID autor, String chave, CadastroRestauranteRequest entrada) {
        if (entrada == null || !validator.validate(entrada).isEmpty()
                || chave == null || !chave.matches("[a-zA-Z0-9._:-]{1,128}"))
            throw RegraNegocioException.invalido("Dados ou Idempotency-Key do cadastro inválidos.");
        if (NomesRestaurantes.normalizar(entrada.nome()).length() < 2)
            throw RegraNegocioException.invalido("Informe um nome com pelo menos duas letras ou números.");
        if (!entrada.categoria().aceitaCadastro())
            throw RegraNegocioException.invalido(CategoriaRestaurante.MENSAGEM_CATEGORIAS);
        area.exigir(entrada.latitude(), entrada.longitude());
        var r = new CadastroRestauranteRequest(entrada.nome().strip(), entrada.latitude(), entrada.longitude(),
                entrada.categoria(), entrada.googlePlaceId(), entrada.tokenSessao(), entrada.distintosConfirmados());
        var anterior = catalogo.anterior(autor, chave, r);
        if (anterior.isPresent()) return anterior.get();
        if (r.googlePlaceId() != null && local.porGoogle(r.googlePlaceId()).isPresent())
            return catalogo.cadastrar(autor, chave, r, false);
        boolean validado = false;
        if (r.googlePlaceId() != null) {
            if (r.tokenSessao() == null) throw RegraNegocioException.invalido("Informe tokenSessao na seleção Google.");
            try {
                var externo = places.consultar(r.googlePlaceId(), r.tokenSessao());
                if (!r.googlePlaceId().equals(externo.googlePlaceId())) throw PlacesException.respostaInvalida();
                if (externo.tipos().stream().noneMatch(config.tiposAceitos()::contains))
                    throw new PlacesException(HttpStatus.UNPROCESSABLE_CONTENT, "tipo_lugar_nao_permitido", "Selecione um estabelecimento de alimentação.");
                validado = true;
            } catch (PlacesException ex) {
                if (ex.getStatus().is4xxClientError() && ex.getStatus() != HttpStatus.TOO_MANY_REQUESTS) throw ex;
                // Os dados próprios bastam para prosseguir; a referência não validada não é gravada.
            }
        }
        return catalogo.cadastrar(autor, chave, r, validado);
    }
}
