package com.jambuapp.api;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.restaurante.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** T05: com o casamento desligado (padrão da API), a seleção não busca no Google e segue como antes. */
@SpringBootTest
@ActiveProfiles("test")
@Import(JambuApiApplicationTests.TestConfig.class)
@TestPropertySource(properties = "jambu.restaurantes.casamento-overture=false")
class CasamentoOvertureDesligadoTest {
    @Autowired RestauranteService service;
    @Autowired JambuApiApplicationTests.PlacesSimulado places;

    @Test void casamentoDesligadoNaoBuscaNoGoogle() {
        places.idsPorNome.put("Qualquer nome", List.of("ChIJ_desligado"));
        int buscas = places.buscasIds.get();
        assertThatThrownBy(() -> service.selecionar(new SelecionarRestauranteRequest("ChIJ_desligado", UUID.randomUUID(), "Qualquer nome")))
                .isInstanceOfSatisfying(RegraNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("cadastro_necessario"));
        assertThat(places.buscasIds.get()).isEqualTo(buscas);
    }
}
