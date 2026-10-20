package com.jambuapp.api.seguranca;

import java.io.IOException;
import java.util.List;
import com.jambuapp.api.autenticacao.AutenticacaoService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroSessaoBearer extends OncePerRequestFilter {
    private final AutenticacaoService service;
    private final LimiteAutenticacao limite;
    public FiltroSessaoBearer(AutenticacaoService service, LimiteAutenticacao limite) {
        this.service = service;
        this.limite = limite;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String caminho = req.getServletPath();
        if (req.getMethod().equals("POST") && (caminho.equals("/api/auth/login") || caminho.equals("/api/auth/cadastro"))
                && !limite.permitir(req.getRemoteAddr())) {
            res.setHeader("Retry-After", "60");
            erro(res, 429, "limite_autenticacao", "Muitas tentativas. Aguarde um minuto.");
            return;
        }
        String header = req.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ")) {
                erro(res, 401, "sessao_invalida", "Sessão inválida ou expirada.");
                return;
            }
            var principal = service.autenticar(header.substring(7));
            if (principal.isEmpty()) {
                erro(res, 401, "sessao_invalida", "Sessão inválida ou expirada.");
                return;
            }
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new UsernamePasswordAuthenticationToken(principal.get(), null, List.of()));
            SecurityContextHolder.setContext(contexto);
        }
        chain.doFilter(req, res);
    }
    static void erro(HttpServletResponse res, int status, String codigo, String mensagem) throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        if (status == 401) res.setHeader("WWW-Authenticate", "Bearer");
        // Somente textos constantes definidos pelo servidor chegam aqui.
        res.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status
                + ",\"codigo\":\"" + codigo + "\",\"detail\":\"" + mensagem + "\"}");
    }
}
