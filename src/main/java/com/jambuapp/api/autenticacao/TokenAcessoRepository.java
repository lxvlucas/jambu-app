package com.jambuapp.api.autenticacao;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface TokenAcessoRepository extends JpaRepository<TokenAcesso, UUID> {
    @EntityGraph(attributePaths = {"sessao", "sessao.usuario"})
    Optional<TokenAcesso> findByTokenHash(String hash);
}
