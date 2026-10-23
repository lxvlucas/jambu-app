package com.jambuapp.api.autenticacao;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import com.jambuapp.api.comum.*;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
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
    private final AuthProperties config;
    private final TokenAcessoRepository acessos;
    private final TokenRenovacaoRepository refreshes;
    private final ProtecaoRepeticao protecao;
    private final TransactionTemplate transacoes;
    private final EntityManager em;
    private final JsonMapper json;
    private static final Logger log = LoggerFactory.getLogger(AutenticacaoService.class);
    private final SecureRandom random = new SecureRandom();
    private final String hashSimulado;
    public AutenticacaoService(UsuarioRepository usuarios, SessaoRepository sessoes,
            PasswordEncoder encoder, Clock clock, AuthProperties config,
            TokenAcessoRepository acessos, TokenRenovacaoRepository refreshes, ProtecaoRepeticao protecao,
            TransactionTemplate transacoes, EntityManager em, JsonMapper json) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.encoder = encoder;
        this.clock = clock;
        this.config = config;
        this.acessos = acessos; this.refreshes = refreshes; this.protecao = protecao;
        this.transacoes = transacoes; this.em = em; this.json = json;
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
        return criarLogin(request, "NATIVO", "Aplicativo / API");
    }
    @Transactional
    public SessaoResponse loginWeb(LoginRequest request, String descricao) {
        return criarLogin(request, "WEB", descricao);
    }
    private SessaoResponse criarLogin(LoginRequest request, String canal, String descricao) {
        String identificador = Usuario.normalizar(request.identificador());
        var usuario = identificador.contains("@") ? usuarios.findByEmail(identificador) : usuarios.findByUsername(identificador);
        boolean senhaCorreta = encoder.matches(request.senha(), usuario.map(Usuario::getSenhaHash).orElse(hashSimulado));
        if (usuario.isEmpty() || !senhaCorreta)
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "credenciais_invalidas", "Credenciais inválidas.");
        usuarios.buscarParaAtualizacao(usuario.get().getId()).orElseThrow();
        var agora = clock.instant();
        var sessao = sessoes.save(new Sessao(usuario.get(), agora, agora.plus(config.inatividade()),
                agora.plus(config.limiteAbsoluto()), canal, descricao));
        var emissao = emitir(sessao, agora);
        log.info("Sessão criada: {}", sessao.getId());
        return emissao.resposta();
    }
    @Transactional(readOnly = true)
    public Optional<UsuarioAutenticado> autenticar(String token) {
        try { return Optional.of(validarAcesso(token)); }
        catch (RegraNegocioException ex) { return Optional.empty(); }
    }
    @Transactional(readOnly = true)
    public UsuarioAutenticado validarAcesso(String token) {
        if (!formato(token)) throw erro("acesso_invalido", "Credencial de acesso inválida.");
        var acesso = acessos.findByTokenHash(Hash.sha256(token)).orElseThrow(() -> erro("acesso_invalido", "Credencial de acesso inválida."));
        var s = acesso.getSessao(); var agora = clock.instant();
        if (!s.estaValida(agora)) throw erro("sessao_encerrada", "Entre novamente para continuar.");
        if (!agora.isBefore(acesso.getExpiraEm())) throw erro("acesso_expirado", "Renove a credencial de acesso.");
        return new UsuarioAutenticado(s.getUsuario().getId(), s.getId(), s.getUsuario().getUsername());
    }
    @Transactional
    public void revogar(UUID sessaoId) {
        sessoes.bloquear(sessaoId).ifPresent(this::encerrar);
    }
    /** O resultado sai da transação antes de virar exceção, preservando a revogação. */
    public SessaoResponse renovar(String token, UUID operacao, String canal) {
        if (!formato(token) || operacao == null) throw erro("refresh_invalido", "Entre novamente para continuar.");
        var resultado = transacoes.execute(status -> renovarTransacional(token, operacao, canal));
        if (resultado.falha() != null) throw resultado.falha();
        return resultado.resposta();
    }
    private Resultado renovarTransacional(String token, UUID operacao, String canal) {
        var encontrado = refreshes.findByTokenHash(Hash.sha256(token));
        if (encontrado.isEmpty()) return falha("refresh_invalido");
        var r = encontrado.get();
        var s = sessoes.bloquear(r.getSessao().getId()).orElseThrow();
        // Outra transação pode ter consumido o token enquanto aguardávamos o bloqueio.
        em.refresh(r);
        var agora = clock.instant();
        if (!s.getCanal().equals(canal) || !s.estaValida(agora)) return falha("sessao_encerrada");
        if (r.getConsumidoEm() != null) {
            boolean naJanela = agora.isBefore(r.getConsumidoEm().plus(config.janelaRepeticao()));
            var sucessor = refreshes.findById(r.getSucessorId()).orElseThrow();
            if (naJanela && sucessor.getConsumidoEm() == null) {
                if (!operacao.equals(r.getOperacaoId()))
                    return new Resultado(null, new RegraNegocioException(HttpStatus.CONFLICT, "renovacao_concorrente", "Aguarde a renovação em andamento."));
                try {
                    if (r.getRespostaCifrada() == null || !agora.isBefore(r.getRepeticaoAte())) return falha("refresh_invalido");
                    return new Resultado(json.readValue(protecao.decifrar(r.getRespostaCifrada(), r.getId(), operacao), SessaoResponse.class), null);
                } catch (IllegalStateException ex) {
                    throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "renovacao_indisponivel", "Tente novamente em instantes.");
                }
            }
            encerrar(s);
            log.warn("Reutilização de refresh; sessão encerrada: {}", s.getId());
            return falha("sessao_encerrada");
        }
        if (!agora.isBefore(r.getExpiraEm())) return falha("refresh_expirado");
        s.renovar(agora, menor(agora.plus(config.inatividade()), s.getAbsolutaEm()));
        var emissao = emitir(s, agora);
        // O sucessor precisa existir antes do UPDATE que cria a referência entre tokens.
        refreshes.flush();
        String resposta = protecao.cifrar(json.writeValueAsString(emissao.resposta()), r.getId(), operacao);
        r.consumir(operacao, emissao.refresh(), resposta, agora, agora.plus(config.janelaRepeticao()));
        log.info("Sessão renovada: {}", s.getId());
        return new Resultado(emissao.resposta(), null);
    }
    @Transactional
    public void logoutRefresh(String token, String canal) {
        if (!formato(token)) throw erro("refresh_invalido", "Credencial inválida.");
        var r = refreshes.findByTokenHash(Hash.sha256(token)).orElseThrow(() -> erro("refresh_invalido", "Credencial inválida."));
        var s = sessoes.bloquear(r.getSessao().getId()).orElseThrow();
        if (!s.getCanal().equals(canal)) throw erro("refresh_invalido", "Credencial inválida.");
        encerrar(s);
    }
    @Transactional(readOnly = true)
    public List<SessaoResumoResponse> listar(UsuarioAutenticado principal) {
        return sessoes.findByUsuarioIdAndRevogadaEmIsNullAndExpiraEmAfterOrderByCriadaEmDesc(principal.usuarioId(), clock.instant())
                .stream().map(s -> new SessaoResumoResponse(s.getId(), s.getCanal(), s.getDescricao(), s.getCriadaEm(),
                    s.getUltimaAtividadeEm(), s.getExpiraEm(), s.getId().equals(principal.sessaoId()))).toList();
    }
    @Transactional
    public void revogarPropria(UsuarioAutenticado principal, UUID id) {
        var s = sessoes.bloquear(id).orElseThrow(RegraNegocioException::inexistente);
        if (!s.getUsuario().getId().equals(principal.usuarioId())) throw RegraNegocioException.inexistente();
        encerrar(s);
    }
    @Transactional
    public void revogarTodas(UsuarioAutenticado principal) {
        usuarios.buscarParaAtualizacao(principal.usuarioId()).orElseThrow(RegraNegocioException::inexistente);
        sessoes.findByUsuarioIdAndRevogadaEmIsNullAndExpiraEmAfterOrderByCriadaEmDesc(principal.usuarioId(), clock.instant())
                .stream().map(Sessao::getId).sorted().forEach(id -> sessoes.bloquear(id).ifPresent(this::encerrar));
    }
    @Transactional
    public void reautenticar(UsuarioAutenticado principal, String senha) {
        var s = sessoes.bloquear(principal.sessaoId()).orElseThrow();
        if (!s.estaValida(clock.instant())) throw erro("sessao_encerrada", "Entre novamente para continuar.");
        if (!encoder.matches(senha, s.getUsuario().getSenhaHash())) throw erro("credenciais_invalidas", "Credenciais inválidas.");
        s.reautenticar(clock.instant());
    }
    @Transactional(readOnly = true)
    public void exigirAutenticacaoRecente(UsuarioAutenticado principal) {
        var s = sessoes.findById(principal.sessaoId()).orElseThrow();
        if (!s.estaValida(clock.instant())) throw erro("sessao_encerrada", "Entre novamente para continuar.");
        if (!clock.instant().isBefore(s.getAutenticadaEm().plus(config.autenticacaoRecente())))
            throw new RegraNegocioException(HttpStatus.FORBIDDEN, "reautenticacao_necessaria", "Confirme sua senha para continuar.");
    }
    private Emissao emitir(Sessao s, Instant agora) {
        String acesso = gerarToken(), refresh = gerarToken();
        Instant expiraAcesso = menor(agora.plus(config.duracaoAcesso()), s.getExpiraEm());
        acessos.save(new TokenAcesso(s, Hash.sha256(acesso), expiraAcesso));
        var r = refreshes.save(new TokenRenovacao(s, Hash.sha256(refresh), agora, s.getExpiraEm()));
        return new Emissao(new SessaoResponse(acesso, expiraAcesso, refresh, s.getId(), s.getExpiraEm()), r);
    }
    private void encerrar(Sessao s) {
        s.revogar(clock.instant());
        log.info("Sessão revogada: {}", s.getId());
    }
    private String gerarToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static boolean formato(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }
    private static Instant menor(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private static RegraNegocioException erro(String codigo, String mensagem) { return new RegraNegocioException(HttpStatus.UNAUTHORIZED, codigo, mensagem); }
    private static Resultado falha(String codigo) { return new Resultado(null, erro(codigo, "Entre novamente para continuar.")); }
    private record Emissao(SessaoResponse resposta, TokenRenovacao refresh) {}
    private record Resultado(SessaoResponse resposta, RegraNegocioException falha) {}
    public record SessaoResumoResponse(UUID id, String canal, String descricao, Instant criadaEm,
            Instant ultimaAtividadeEm, Instant expiraEm, boolean atual) {}
}
