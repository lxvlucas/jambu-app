package com.jambuapp.api.foto;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FotoPerfilRepository extends JpaRepository<FotoPerfil, UUID> {
    Optional<FotoPerfil> findByUsuarioAtivoId(UUID usuarioId);
    Optional<FotoPerfil> findByUsuarioAtivoIdAndStatus(UUID usuarioId, StatusModeracaoFoto status);
}
