package com.jambuapp.api.restaurante;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** RN-61: conta por termo normalizado e dia as buscas sem resultado local, sem guardar quem buscou. */
@Service
public class BuscasSemResultadoService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public BuscasSemResultadoService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String termo) {
        var dia = LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Sao_Paulo"));
        jdbc.update("""
                insert into buscas_sem_resultado(termo, dia, buscas) values (?, ?, 1)
                on conflict(termo, dia) do update set buscas = buscas_sem_resultado.buscas + 1
                """, NomesRestaurantes.normalizar(termo), dia);
    }
}
