package com.jambuapp.api.restaurante;

import java.time.*;
import java.util.UUID;
import com.jambuapp.api.restaurante.places.PlacesException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@EnableConfigurationProperties(CatalogoProperties.class)
public class CotaPlacesService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CatalogoProperties config;
    public CotaPlacesService(JdbcTemplate jdbc, Clock clock, CatalogoProperties config) {
        this.jdbc = jdbc; this.clock = clock; this.config = config;
    }
    /** Reserva antes do HTTP, sem reembolso em erro/timeout; uma chamada nunca passa do teto. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservar() {
        if (config.limiteGoogleDia() == 0) throw esgotada();
        var dia = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Sao_Paulo"));
        int alteradas = jdbc.update("""
                insert into consumo_places_diario(dia, chamadas) values (?, 1)
                on conflict(dia) do update set chamadas = consumo_places_diario.chamadas + 1
                where consumo_places_diario.chamadas < ?
                """, dia, config.limiteGoogleDia());
        if (alteradas == 0) throw esgotada();
    }
    /**
     * Uma busca ampliada por segundo e até limiteBuscasAmpliadasDia por dia (fuso de São Luís) por pessoa.
     * Devolve null quando liberada, ou o estado da busca: AGUARDE ou LIMITE_DIARIO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String verificarBusca(UUID usuarioId) {
        var agora = clock.instant();
        var dia = LocalDate.ofInstant(agora, ZoneId.of("America/Sao_Paulo"));
        int alteradas = jdbc.update("""
                insert into limites_busca_restaurantes(usuario_id, ultima_busca_em, dia, buscas_dia) values (?, ?, ?, 1)
                on conflict(usuario_id) do update set ultima_busca_em = excluded.ultima_busca_em,
                    buscas_dia = case when limites_busca_restaurantes.dia = excluded.dia
                                      then limites_busca_restaurantes.buscas_dia + 1 else 1 end,
                    dia = excluded.dia
                where limites_busca_restaurantes.ultima_busca_em <= ?
                  and (limites_busca_restaurantes.dia is distinct from excluded.dia
                       or limites_busca_restaurantes.buscas_dia < ?)
                """, usuarioId, java.sql.Timestamp.from(agora), dia,
                java.sql.Timestamp.from(agora.minusSeconds(1)), config.limiteBuscasAmpliadasDia());
        if (alteradas == 1) return null;
        Integer feitas = jdbc.queryForObject(
                "select case when dia = ? then buscas_dia else 0 end from limites_busca_restaurantes where usuario_id = ?",
                Integer.class, dia, usuarioId);
        return feitas != null && feitas >= config.limiteBuscasAmpliadasDia() ? "LIMITE_DIARIO" : "AGUARDE";
    }
    private PlacesException esgotada() {
        return new PlacesException(HttpStatus.TOO_MANY_REQUESTS, "places_cota_esgotada",
                "Limite diário de consultas externas atingido. Use a busca local ou adicione o restaurante.");
    }
}
