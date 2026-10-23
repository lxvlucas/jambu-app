package com.jambuapp.api.restaurante;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface RestauranteRepository extends JpaRepository<Restaurante, UUID> {
    Optional<Restaurante> findByGooglePlaceId(String googlePlaceId);
    Optional<Restaurante> findByOvertureId(String overtureId);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from Restaurante r where r.id = :id")
    Optional<Restaurante> buscarParaSelecao(UUID id);
    @Query(value = """
            select * from restaurantes where situacao <> 'REJEITADO'
              and (nome_normalizado like :prefixo or busca @@ to_tsquery('simple', :consulta))
            order by case when nome_normalizado = :termo then 0 else 1 end, nome_normalizado, id
            limit 20
            """, nativeQuery = true)
    List<Restaurante> buscar(String termo, String prefixo, String consulta);
    @Query("select r from Restaurante r where r.situacao <> com.jambuapp.api.restaurante.SituacaoRestaurante.REJEITADO "
            + "and r.latitude between :sul and :norte and r.longitude between :oeste and :leste")
    List<Restaurante> proximos(double sul, double norte, double oeste, double leste);
    Page<Restaurante> findBySituacao(SituacaoRestaurante situacao, Pageable pageable);
}
