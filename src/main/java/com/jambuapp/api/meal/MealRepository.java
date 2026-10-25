package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface MealRepository extends JpaRepository<Meal, UUID> {
    @EntityGraph(attributePaths = {"autor", "restaurante", "dishes"})
    Optional<Meal> findByIdAndExcluidaEmIsNull(UUID id);
    long countByAutorIdAndExcluidaEmIsNull(UUID autorId);

    // Paginação apenas de IDs: nunca limitar um fetch join da coleção de Dishes.
    @Query(value = """
            select m.id from meals m
            where m.excluida_em is null
              and (cast(:autor as uuid) is null or m.autor_id = :autor)
              and (cast(:restaurante as uuid) is null or m.restaurante_id = :restaurante)
              and (cast(:timeline as uuid) is null or m.autor_id = :timeline or exists
                  (select 1 from seguimentos s where s.seguidor_id = :timeline and s.seguido_id = m.autor_id))
              and (cast(:instante as timestamptz) is null or (m.publicada_em, m.id) < (:instante, :ultimoId))
            order by m.publicada_em desc, m.id desc
            """, nativeQuery = true)
    List<UUID> buscarIds(UUID autor, UUID restaurante, UUID timeline, Instant instante, UUID ultimoId, Pageable pagina);

    // Feed (T19): nunca os próprios posts (RN-32); na aba SEGUINDO, só de quem a pessoa segue.
    @Query(value = """
            select m.id from meals m
            where m.excluida_em is null and m.autor_id <> :usuario
              and (not :seguindo or exists
                  (select 1 from seguimentos s where s.seguidor_id = :usuario and s.seguido_id = m.autor_id))
              and (cast(:instante as timestamptz) is null or (m.publicada_em, m.id) < (:instante, :ultimoId))
            order by m.publicada_em desc, m.id desc
            """, nativeQuery = true)
    List<UUID> buscarIdsFeed(UUID usuario, boolean seguindo, Instant instante, UUID ultimoId, Pageable pagina);

    @EntityGraph(attributePaths = {"autor", "restaurante", "dishes"})
    @Query("select m from Meal m where m.id in :ids and m.excluidaEm is null")
    List<Meal> buscarCompletas(Collection<UUID> ids);

    @Query(value = """
            select medias.restaurante_id as "restauranteId", avg(medias.nota) as "notaMedia", count(*) as "quantidade",
                   max(medias.publicada_em) as "ultimaAvaliacaoEm"
            from (select m.id, m.restaurante_id, m.publicada_em, avg(d.nota) as nota
                  from meals m join dishes d on d.meal_id = m.id
                  where m.excluida_em is null and m.restaurante_id in (:ids)
                  group by m.id, m.restaurante_id, m.publicada_em) medias
            group by medias.restaurante_id
            """, nativeQuery = true)
    List<EstatisticaRestaurante> estatisticas(Collection<UUID> ids);

    interface EstatisticaRestaurante {
        UUID getRestauranteId();
        BigDecimal getNotaMedia();
        long getQuantidade();
        Instant getUltimaAvaliacaoEm();
    }
}
