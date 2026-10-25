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
    /**
     * Busca local (T06): prefixo, palavras, semelhança por trigramas (pg_trgm, tolera erro de
     * digitação) e nome de prato em Meal ativa. Exato primeiro, verificados antes de provisórios.
     * T07: também por "nome + bairro" ("coisas do cohajap"), com o bairro preenchido pelo banco.
     */
    @Query(value = """
            select r.* from restaurantes r where r.situacao <> 'REJEITADO'
              and (r.nome_normalizado like :prefixo or r.busca @@ to_tsquery('simple', :consulta)
                   or r.nome_normalizado % :termo or :termo <% r.nome_normalizado
                   or (r.nome_normalizado || ' ' || coalesce(r.bairro_normalizado, '')) % :termo
                   or exists (select 1 from meals m join dishes d on d.meal_id = m.id
                              where m.restaurante_id = r.id and m.excluida_em is null
                                and (trim(regexp_replace(translate(lower(d.nome), 'áàâãäéèêëíìîïóòôõöúùûüç',
                                         'aaaaaeeeeiiiiooooouuuuc'), '[^a-z0-9]+', ' ', 'g')) like :prefixo
                                     or trim(regexp_replace(translate(lower(d.nome), 'áàâãäéèêëíìîïóòôõöúùûüç',
                                         'aaaaaeeeeiiiiooooouuuuc'), '[^a-z0-9]+', ' ', 'g')) % :termo)))
            order by case when r.nome_normalizado = :termo then 0 else 1 end,
                     case when r.situacao = 'ATIVO' then 0 else 1 end,
                     greatest(similarity(r.nome_normalizado, :termo),
                              similarity(r.nome_normalizado || ' ' || coalesce(r.bairro_normalizado, ''), :termo)) desc,
                     r.nome_normalizado, r.id
            limit 20
            """, nativeQuery = true)
    List<Restaurante> buscar(String termo, String prefixo, String consulta);
    @Query("select r from Restaurante r where r.situacao <> com.jambuapp.api.restaurante.SituacaoRestaurante.REJEITADO "
            + "and r.latitude between :sul and :norte and r.longitude between :oeste and :leste")
    List<Restaurante> proximos(double sul, double norte, double oeste, double leste);
    Page<Restaurante> findBySituacao(SituacaoRestaurante situacao, Pageable pageable);
}
