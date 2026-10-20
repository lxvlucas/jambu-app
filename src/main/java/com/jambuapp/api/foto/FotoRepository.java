package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface FotoRepository extends JpaRepository<Foto, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Foto f join fetch f.autor where f.id = :id")
    Optional<Foto> buscarParaAtualizacao(UUID id);

    @Query("select f from Foto f join fetch f.autor where f.id = :id")
    Optional<Foto> buscarComAutor(UUID id);

    @EntityGraph(attributePaths = {"autor"})
    Page<Foto> findByStatusOrderByCriadaEmAscIdAsc(StatusModeracaoFoto status, Pageable pagina);

    @EntityGraph(attributePaths = {"autor"})
    Page<Foto> findAllByOrderByCriadaEmDescIdDesc(Pageable pagina);

    @Query("""
            select f from Foto f
            where f.arquivosLimposEm is null
              and (f.status = com.jambuapp.api.foto.StatusModeracaoFoto.REMOVIDA
                   or (f.status = com.jambuapp.api.foto.StatusModeracaoFoto.REJEITADA and f.atualizadaEm <= :limiteRetencao))
            """)
    List<Foto> buscarElegiveisParaLimpeza(Instant limiteRetencao);

    @Query("select f.chaveArquivo from Foto f where f.arquivosLimposEm is null")
    List<String> buscarChavesPrincipaisRetidas();

    @Query("select f.chaveMiniatura from Foto f where f.arquivosLimposEm is null")
    List<String> buscarChavesMiniaturasRetidas();
}
