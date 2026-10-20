package com.jambuapp.api.autenticacao;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {
    @EntityGraph(attributePaths = "usuario")
    Optional<Sessao> findByTokenHash(String hash);
}
