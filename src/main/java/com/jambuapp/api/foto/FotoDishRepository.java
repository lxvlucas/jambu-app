package com.jambuapp.api.foto;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FotoDishRepository extends JpaRepository<FotoDish, UUID> {
    Optional<FotoDish> findByDishAtivoId(UUID dishId);

    @Query("""
            select fd from FotoDish fd
            join fetch fd.dish d
            join fetch d.meal m
            where fd.dishAtivoId in :dishIds
              and fd.status = com.jambuapp.api.foto.StatusModeracaoFoto.APROVADA
              and m.excluidaEm is null
            """)
    List<FotoDish> buscarAprovadasPorDishes(Collection<UUID> dishIds);

    @Query("select fd from FotoDish fd where fd.dish.id in :dishIds")
    List<FotoDish> buscarPorDishes(Collection<UUID> dishIds);

    @Query("select fd from FotoDish fd join fetch fd.dish d join fetch d.meal m where fd.id = :id")
    Optional<FotoDish> buscarComDishEMeal(UUID id);
}
