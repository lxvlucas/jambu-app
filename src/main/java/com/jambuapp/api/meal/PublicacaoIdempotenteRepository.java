package com.jambuapp.api.meal;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacaoIdempotenteRepository extends JpaRepository<PublicacaoIdempotente, UUID> {
    Optional<PublicacaoIdempotente> findByAutorIdAndChave(UUID autorId, String chave);
}
