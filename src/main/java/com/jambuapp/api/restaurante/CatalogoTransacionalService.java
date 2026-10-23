package com.jambuapp.api.restaurante;

import java.time.Clock;
import java.util.*;
import com.jambuapp.api.comum.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class CatalogoTransacionalService {
    private final RestauranteRepository restaurantes;
    private final JdbcTemplate jdbc;
    private final CatalogoProperties config;
    private final Clock clock;
    private final JsonMapper json;
    public CatalogoTransacionalService(RestauranteRepository restaurantes, JdbcTemplate jdbc,
            CatalogoProperties config, Clock clock, JsonMapper json) {
        this.restaurantes = restaurantes; this.jdbc = jdbc; this.config = config; this.clock = clock; this.json = json;
    }
    /** Escritas raras do catálogo, serializadas entre instâncias; nenhum HTTP sob este lock. */
    public void bloquearCatalogo() {
        jdbc.query("select pg_advisory_xact_lock(74182026)", rs -> { return null; });
    }
    @Transactional(readOnly = true)
    public Optional<CadastroRestauranteResponse> anterior(UUID autor, String chave, CadastroRestauranteRequest request) {
        var ids = jdbc.query("select hash_requisicao, restaurante_id from cadastros_restaurantes where autor_id = ? and chave = ?",
                (rs, n) -> {
                    if (!rs.getString(1).equals(hash(request)))
                        throw RegraNegocioException.conflito("Chave de cadastro já usada com outros dados.");
                    return rs.getObject(2, UUID.class);
                }, autor, chave);
        return ids.stream().findFirst().map(id -> CadastroRestauranteResponse.selecionado("REUTILIZADO",
                restaurantes.findById(id).orElseThrow(RegraNegocioException::inexistente)));
    }
    public CadastroRestauranteResponse cadastrar(UUID autor, String chave, CadastroRestauranteRequest request, boolean googleValidado) {
        bloquearCatalogo();
        var anterior = anterior(autor, chave, request);
        if (anterior.isPresent()) return anterior.get();
        if (request.googlePlaceId() != null) {
            var existente = restaurantes.findByGooglePlaceId(request.googlePlaceId());
            if (existente.isPresent()) return concluir(autor, chave, request, existente.get(), "REUTILIZADO");
        }
        var candidatos = candidatos(request.nome(), request.latitude(), request.longitude(), null);
        var confirmados = request.distintosConfirmados() == null ? Set.<UUID>of() : request.distintosConfirmados();
        if (candidatos.stream().anyMatch(c -> !confirmados.contains(c.restaurante().id())))
            return new CadastroRestauranteResponse("CONFIRMAR_DUPLICATAS", null, candidatos);
        var r = Restaurante.proprio(request.nome(), request.latitude(), request.longitude(), request.categoria(), autor, clock.instant());
        if (googleValidado) r.vincularGoogle(request.googlePlaceId(), clock.instant());
        restaurantes.saveAndFlush(r);
        return concluir(autor, chave, request, r, request.googlePlaceId() != null && !googleValidado ? "CRIADO_SEM_GOOGLE" : "CRIADO");
    }
    public List<CadastroRestauranteResponse.Candidato> candidatos(String nome, double lat, double lon, UUID ignorar) {
        double deltaLat = config.raioDuplicataMetros() / 111000.0;
        double deltaLon = deltaLat / Math.cos(Math.toRadians(lat));
        String normalizado = NomesRestaurantes.normalizar(nome);
        return restaurantes.proximos(lat - deltaLat, lat + deltaLat, lon - deltaLon, lon + deltaLon).stream()
                .filter(r -> !r.getId().equals(ignorar) && r.getNomeNormalizado() != null)
                .map(r -> new CadastroRestauranteResponse.Candidato(RestauranteResumo.de(r),
                        Math.round(NomesRestaurantes.distancia(lat, lon, r.getLatitude(), r.getLongitude())),
                        NomesRestaurantes.similaridade(normalizado, r.getNomeNormalizado())))
                .filter(c -> c.distanciaMetros() <= config.raioDuplicataMetros() && c.similaridade() >= config.similaridadeMinima())
                .sorted(Comparator.comparingDouble(CadastroRestauranteResponse.Candidato::similaridade).reversed()
                        .thenComparingLong(CadastroRestauranteResponse.Candidato::distanciaMetros))
                .toList();
    }
    private CadastroRestauranteResponse concluir(UUID autor, String chave, CadastroRestauranteRequest request,
            Restaurante restaurante, String situacao) {
        restaurante.exigirSelecionavel();
        jdbc.update("insert into cadastros_restaurantes(autor_id,chave,hash_requisicao,restaurante_id,criado_em) values (?,?,?,?,?)",
                autor, chave, hash(request), restaurante.getId(), java.sql.Timestamp.from(clock.instant()));
        return CadastroRestauranteResponse.selecionado(situacao, restaurante);
    }
    private String hash(CadastroRestauranteRequest request) {
        // tokenSessao é transporte; confirmar candidatos não muda a identidade do cadastro.
        return Hash.sha256(json.writeValueAsString(Arrays.asList(request.nome(), request.latitude(),
                request.longitude(), request.categoria(), request.googlePlaceId())));
    }
}
