package com.jambuapp.api.autenticacao;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sessao s where s.id = :id")
    Optional<Sessao> bloquear(UUID id);
    List<Sessao> findByUsuarioIdAndRevogadaEmIsNullAndExpiraEmAfterOrderByCriadaEmDesc(UUID usuarioId, java.time.Instant agora);
}
