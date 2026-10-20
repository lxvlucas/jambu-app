package com.jambuapp.api.autenticacao;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.seguranca.UsuarioAutenticado;
import com.jambuapp.api.usuario.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacaoService {
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final JambuProperties config;
    private final SecureRandom random = new SecureRandom();
    private final String hashSimulado;
    public AutenticacaoService(UsuarioRepository usuarios, SessaoRepository sessoes,
            PasswordEncoder encoder, Clock clock, JambuProperties config) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.encoder = encoder;
        this.clock = clock;
        this.config = config;
        this.hashSimulado = encoder.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public ContaResponse cadastrar(CadastroRequest request) {
        var usuario = new Usuario(request.username(), request.email(), request.celular(),
                encoder.encode(request.senha()), clock.instant());
        return ContaResponse.de(usuarios.saveAndFlush(usuario));
    }
    @Transactional
    public SessaoResponse login(LoginRequest request) {
        String identificador = Usuario.normalizar(request.identificador());
        var usuario = identificador.contains("@") ? usuarios.findByEmail(identificador) : usuarios.findByUsername(identificador);
        boolean senhaCorreta = encoder.matches(request.senha(), usuario.map(Usuario::getSenhaHash).orElse(hashSimulado));
        if (usuario.isEmpty() || !senhaCorreta)
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "credenciais_invalidas", "Credenciais inválidas.");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var agora = clock.instant();
        var sessao = sessoes.save(new Sessao(usuario.get(), Hash.sha256(token), agora, agora.plus(config.duracaoSessao())));
        return new SessaoResponse(token, sessao.getExpiraEm());
    }
    @Transactional(readOnly = true)
    public Optional<UsuarioAutenticado> autenticar(String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return sessoes.findByTokenHash(Hash.sha256(token)).filter(s -> s.estaValida(clock.instant()))
                .map(s -> new UsuarioAutenticado(s.getUsuario().getId(), s.getId(), s.getUsuario().getUsername()));
    }
    @Transactional
    public void revogar(UUID sessaoId) {
        sessoes.findById(sessaoId).ifPresent(s -> s.revogar(clock.instant()));
    }
}
