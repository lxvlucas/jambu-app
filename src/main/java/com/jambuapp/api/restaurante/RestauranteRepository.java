package com.jambuapp.api.restaurante;

import java.util.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;

public interface RestauranteRepository extends JpaRepository<Restaurante, UUID> {
    Optional<Restaurante> findByGooglePlaceId(String googlePlaceId);
    @Modifying
    @Query(value = """
            insert into restaurantes (id, google_place_id, places_validado_em)
            values (:id, :placeId, :validadoEm)
            on conflict (google_place_id) do update set places_validado_em = excluded.places_validado_em
            """, nativeQuery = true)
    int registrarSelecao(UUID id, String placeId, Instant validadoEm);
}
