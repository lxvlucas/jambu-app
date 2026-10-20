package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface TarefaModeracaoRepository extends JpaRepository<TarefaModeracao, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TarefaModeracao t join fetch t.foto f where t.id = :id")
    Optional<TarefaModeracao> buscarParaAtualizacao(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from TarefaModeracao t
            where t.foto.id = :fotoId
              and t.estado in (com.jambuapp.api.foto.EstadoTarefa.AGUARDANDO, com.jambuapp.api.foto.EstadoTarefa.EM_EXECUCAO)
            """)
    List<TarefaModeracao> buscarAtivasPorFotoParaAtualizacao(UUID fotoId);

    @Query(value = """
            select t.id from tarefas_moderacao t
            where (t.estado = 'AGUARDANDO' and t.proxima_tentativa_em <= :agora)
               or (t.estado = 'EM_EXECUCAO' and t.reserva_ate <= :agora)
            order by t.proxima_tentativa_em asc, t.criada_em asc, t.id asc
            limit :limite
            for update skip locked
            """, nativeQuery = true)
    List<UUID> buscarIdsProntosParaReserva(Instant agora, int limite);
}
