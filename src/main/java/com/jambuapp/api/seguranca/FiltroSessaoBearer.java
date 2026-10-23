package com.jambuapp.api.seguranca;

import java.io.IOException;
import java.util.List;
import com.jambuapp.api.autenticacao.AutenticacaoService;
import com.jambuapp.api.comum.RegraNegocioException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroSessaoBearer extends OncePerRequestFilter {
    private final AutenticacaoService service;
    public FiltroSessaoBearer(AutenticacaoService service) {
        this.service = service;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String caminho = req.getServletPath();
        // Esses endpoints validam sua própria credencial; Bearer expirado não bloqueia refresh/logout.
        if (caminho.startsWith("/api/auth/web/") || caminho.equals("/api/auth/refresh") || caminho.equals("/api/auth/logout")) {
            chain.doFilter(req, res); return;
        }
        String header = req.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ")) {
                erro(res, 401, "sessao_invalida", "Sessão inválida ou expirada.");
                return;
            }
            com.jambuapp.api.seguranca.UsuarioAutenticado principal;
            try { principal = service.validarAcesso(header.substring(7)); }
            catch (RegraNegocioException ex) { erro(res, 401, ex.getCodigo(), ex.getMessage()); return; }
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
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
