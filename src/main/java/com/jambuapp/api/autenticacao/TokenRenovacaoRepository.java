package com.jambuapp.api.autenticacao;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface TokenRenovacaoRepository extends JpaRepository<TokenRenovacao, UUID> {
    Optional<TokenRenovacao> findByTokenHash(String hash);
    @Modifying
    @Query("update TokenRenovacao t set t.respostaCifrada = null, t.repeticaoAte = null where t.repeticaoAte <= :agora")
    int limparRespostas(Instant agora);
}
