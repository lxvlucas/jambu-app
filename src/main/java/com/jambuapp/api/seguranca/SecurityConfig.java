package com.jambuapp.api.seguranca;

import com.jambuapp.api.autenticacao.AutenticacaoService;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import com.jambuapp.api.autenticacao.AuthProperties;
import java.time.Clock;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("pbkdf2", java.util.Map.of("pbkdf2",
                new Pbkdf2PasswordEncoder("", 16, 600000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256)));
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AutenticacaoService service, LimiteAutenticacao limite,
            AuthProperties config, Clock clock) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(c -> c.secure(config.cookieSeguro()).httpOnly(true).sameSite("Strict").path("/api/auth/web"));
        return http
                .csrf(c -> c.csrfTokenRepository(csrf).requireCsrfProtectionMatcher(req ->
                        req.getServletPath().startsWith("/api/auth/web/")
                        && !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cadastro", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/web/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> FiltroSessaoBearer.erro(res, 401, "autenticacao_necessaria", "Entre para continuar."))
                        .accessDeniedHandler((req, res, ex) -> FiltroSessaoBearer.erro(res, 403, "acesso_negado", "Operação não permitida.")))
                .addFilterBefore(new FiltroProtecaoAuth(config, limite, clock), CsrfFilter.class)
                .addFilterBefore(new FiltroSessaoBearer(service), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
