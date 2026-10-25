package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FotoMealRepository extends JpaRepository<FotoMeal, UUID> {
    /** Soltas que ninguém usou: o app só envia a foto ao publicar, então em 24 h ela já deveria estar num post. */
    @Query("select f from FotoMeal f where f.atual = true and f.meal is null and f.criadaEm <= :limite")
    List<FotoMeal> buscarSoltasVencidas(Instant limite);
}
