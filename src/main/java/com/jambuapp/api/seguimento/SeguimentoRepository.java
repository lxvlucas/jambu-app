package com.jambuapp.api.seguimento;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SeguimentoRepository extends JpaRepository<Seguimento, UUID> {
    boolean existsBySeguidorIdAndSeguidoId(UUID seguidorId, UUID seguidoId);
    long deleteBySeguidorIdAndSeguidoId(UUID seguidorId, UUID seguidoId);
    long countBySeguidorId(UUID id);
    long countBySeguidoId(UUID id);
    @EntityGraph(attributePaths = "seguidor")
    Page<Seguimento> findBySeguidoId(UUID id, Pageable pagina);
    @EntityGraph(attributePaths = "seguido")
    Page<Seguimento> findBySeguidorId(UUID id, Pageable pagina);
}
