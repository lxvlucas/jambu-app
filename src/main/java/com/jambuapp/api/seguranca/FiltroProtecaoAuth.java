package com.jambuapp.api.seguranca;

import java.io.IOException;
import java.time.Clock;
import java.util.*;
import com.jambuapp.api.autenticacao.AuthProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroProtecaoAuth extends OncePerRequestFilter {
    private final AuthProperties config;
    private final LimiteAutenticacao login;
    private final Clock clock;
    private final Map<String, Janela> janelas = new HashMap<>();
    public FiltroProtecaoAuth(AuthProperties config, LimiteAutenticacao login, Clock clock) {
        this.config = config; this.login = login; this.clock = clock;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String caminho = req.getServletPath();
        boolean web = caminho.startsWith("/api/auth/web/");
        if (web) {
            if (config.origemWeb() == null || config.origemWeb().isBlank()) {
                FiltroSessaoBearer.erro(res, 503, "web_nao_configurada", "Acesso web ainda não configurado."); return;
            }
            if (!req.getMethod().equals("GET") && !config.origemWeb().equals(req.getHeader("Origin"))) {
                FiltroSessaoBearer.erro(res, 403, "origem_negada", "Origem não permitida."); return;
            }
            res.setHeader("Cache-Control", "no-store");
        }
        boolean post = req.getMethod().equals("POST");
        boolean tentativaLogin = post && (caminho.equals("/api/auth/login") || caminho.equals("/api/auth/cadastro") || caminho.equals("/api/auth/web/login"));
        boolean refresh = post && (caminho.equals("/api/auth/refresh") || caminho.equals("/api/auth/web/refresh")
                || caminho.equals("/api/auth/logout") || caminho.equals("/api/auth/web/logout"));
        boolean reautenticar = post && caminho.equals("/api/auth/reautenticar");
        boolean permitido = !tentativaLogin || login.permitir(req.getRemoteAddr());
        if (refresh) permitido = permitir("refresh:" + req.getRemoteAddr(), config.limiteRefreshMinuto());
        if (reautenticar) permitido = permitir("reautenticar:" + req.getRemoteAddr(), config.limiteReautenticacaoMinuto());
        if (!permitido) {
            res.setHeader("Retry-After", "60");
            FiltroSessaoBearer.erro(res, 429, "limite_autenticacao", "Muitas tentativas. Aguarde um minuto."); return;
        }
        chain.doFilter(req, res);
    }
    private synchronized boolean permitir(String chave, int limite) {
        long minuto = clock.instant().getEpochSecond() / 60;
        janelas.entrySet().removeIf(e -> e.getValue().minuto() != minuto);
        if (!janelas.containsKey(chave) && janelas.size() >= 10000) return false;
        var janela = janelas.getOrDefault(chave, new Janela(minuto, 0));
        janelas.put(chave, new Janela(minuto, janela.tentativas() + 1));
        return janela.tentativas() < limite;
    }
    private record Janela(long minuto, int tentativas) {}
}
