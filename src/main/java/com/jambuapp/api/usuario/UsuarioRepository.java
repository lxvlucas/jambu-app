package com.jambuapp.api.usuario;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    Page<Usuario> findByUsernameContainingIgnoreCase(String termo, Pageable pagina);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> buscarParaAtualizacao(UUID id);
}
