package com.jambuapp.api.foto;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroModeracaoRepository extends JpaRepository<RegistroModeracao, UUID> {
    List<RegistroModeracao> findByFotoIdOrderByCriadoEmAscIdAsc(UUID fotoId);
}
