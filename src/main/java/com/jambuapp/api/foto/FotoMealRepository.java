package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface FotoMealRepository extends JpaRepository<FotoMeal, UUID> {
    /** Soltas que ninguém usou: o app só envia a foto ao publicar, então em 24 h ela já deveria estar num post. */
    @Query("select f from FotoMeal f where f.atual = true and f.meal is null and f.criadaEm <= :limite")
    List<FotoMeal> buscarSoltasVencidas(Instant limite);

    /**
     * Trava as fotos pedidas e as que já são do post, sempre na ordem do ID: duas publicações com a mesma foto
     * esperam uma pela outra, e a segunda encontra a foto já usada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FotoMeal f where f.id in :ids or f.meal.id = :mealId order by f.id")
    List<FotoMeal> buscarParaVincular(Collection<UUID> ids, UUID mealId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FotoMeal f where f.meal.id = :mealId order by f.id")
    List<FotoMeal> buscarDaMealParaAtualizacao(UUID mealId);

    @Query("""
            select f from FotoMeal f join fetch f.meal m
            where m.id in :mealIds and m.excluidaEm is null and f.atual = true
              and f.status = com.jambuapp.api.foto.StatusModeracaoFoto.APROVADA
            order by f.ordem
            """)
    List<FotoMeal> buscarAprovadasPorMeals(Collection<UUID> mealIds);
}
