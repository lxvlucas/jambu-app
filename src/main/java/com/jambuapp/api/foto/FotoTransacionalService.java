package com.jambuapp.api.foto;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.meal.Meal;
import com.jambuapp.api.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FotoTransacionalService {
    public static final java.time.Duration PRAZO_FOTO_SOLTA = java.time.Duration.ofHours(24);
    /** Uma mensagem para todos os casos: não revela se a foto de outra pessoa existe (decisão de 25/10). */
    static final String FOTO_INVALIDA = "Foto inválida: envie de novo pelo app e publique em até 24 horas.";
    private final UsuarioRepository usuarios;
    private final FotoRepository fotos;
    private final FotoPerfilRepository fotosPerfil;
    private final FotoMealRepository fotosMeal;
    private final TarefaModeracaoRepository tarefas;
    private final RegistroModeracaoRepository registros;
    private final FotoProperties config;
    private final Clock clock;

    public FotoTransacionalService(UsuarioRepository usuarios, FotoRepository fotos,
            FotoPerfilRepository fotosPerfil, FotoMealRepository fotosMeal,
            TarefaModeracaoRepository tarefas, RegistroModeracaoRepository registros,
            FotoProperties config, Clock clock) {
        this.usuarios = usuarios;
        this.fotos = fotos;
        this.fotosPerfil = fotosPerfil;
        this.fotosMeal = fotosMeal;
        this.tarefas = tarefas;
        this.registros = registros;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void validarAutor(UUID autorId) {
        if (!usuarios.existsById(autorId)) throw RegraNegocioException.inexistente();
    }

    /** T16: foto do post enviada antes da publicação; fica solta, da pessoa, até entrar numa Meal. */
    @Transactional
    public ResultadoPersistenciaUpload registrarFotoSolta(UUID autorId, UUID fotoId, String chavePrincipal,
            String chaveMiniatura, ProcessadorImagemFoto.ImagemProcessada imagem) {
        var autor = usuarios.findById(autorId).orElseThrow(RegraNegocioException::inexistente);
        Instant agora = agora();
        var nova = fotosMeal.saveAndFlush(new FotoMeal(
                fotoId, autor, chavePrincipal, chaveMiniatura, imagem.hashConteudo(),
                imagem.largura(), imagem.altura(), imagem.principal().length, imagem.miniatura().length, agora));
        UUID tarefaId = iniciarModeracao(nova, agora);
        return new ResultadoPersistenciaUpload(FotoResponse.de(nova), tarefaId, List.of());
    }

    /**
     * T18 (RN-18, RN-71): com a moderação desligada (MVP), a foto nasce aprovada e não entra na fila; o registro
     * guarda o porquê. Com ela ligada, cria a tarefa de análise e devolve o ID dela.
     */
    private UUID iniciarModeracao(Foto foto, Instant agora) {
        if (config.moderacaoHabilitada())
            return tarefas.saveAndFlush(new TarefaModeracao(foto, config.versaoPolitica(), agora)).getId();
        foto.publicarSemModeracao(agora);
        registros.save(RegistroModeracao.automatico(foto, ResultadoModeracao.APROVAR,
                "Publicada na hora: moderação desligada no MVP.", config.versaoPolitica(), null, null, null, agora));
        return null;
    }

    /** Remove as fotos soltas com mais de 24 h; os arquivos saem na limpeza das removidas, logo em seguida. */
    @Transactional
    public int removerSoltasVencidas() {
        Instant agora = agora();
        var vencidas = fotosMeal.buscarSoltasVencidas(agora.minus(PRAZO_FOTO_SOLTA));
        for (var foto : vencidas) {
            cancelarTarefasAtivas(foto.getId(), agora);
            foto.remover(agora);
        }
        return vencidas.size();
    }

    /**
     * Fotos do post (T16), na transação da publicação ou da edição: cada ID tem de ser foto atual da própria
     * pessoa, não rejeitada, e solta (enviada há menos de 24 h) ou já deste post. As fotos do post que saíram da
     * lista são removidas. Devolve as aprovadas, na ordem, e as removidas, para apagar os arquivos após o commit.
     */
    @Transactional
    public ResultadoFotosMeal definirFotosDaMeal(UUID autorId, Meal meal, List<UUID> fotoIds) {
        Instant agora = agora();
        Instant limiteSolta = agora.minus(PRAZO_FOTO_SOLTA);
        Map<UUID, FotoMeal> travadas = new HashMap<>();
        for (var foto : fotosMeal.buscarParaVincular(fotoIds, meal.getId())) travadas.put(foto.getId(), foto);
        for (UUID id : fotoIds) {
            var foto = travadas.get(id);
            boolean valida = foto != null && foto.getAutor().getId().equals(autorId) && foto.isAtual()
                    && foto.getStatus() != StatusModeracaoFoto.REJEITADA
                    && (foto.getMeal() == null ? foto.getCriadaEm().isAfter(limiteSolta) : foto.getMeal().getId().equals(meal.getId()));
            if (!valida) throw RegraNegocioException.invalido(FOTO_INVALIDA);
        }
        List<FotoSubstituida> removidas = new ArrayList<>();
        for (var foto : travadas.values()) {
            if (foto.getMeal() != null && !fotoIds.contains(foto.getId())) {
                cancelarTarefasAtivas(foto.getId(), agora);
                foto.remover(agora);
                removidas.add(new FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura()));
            }
        }
        List<FotoPublicaResponse> publicas = new ArrayList<>();
        for (int i = 0; i < fotoIds.size(); i++) {
            var foto = travadas.get(fotoIds.get(i));
            foto.vincular(meal, i + 1);
            var publica = FotoPublicaResponse.de(foto);
            if (publica != null) publicas.add(publica);
        }
        return new ResultadoFotosMeal(List.copyOf(publicas), List.copyOf(removidas));
    }

    /** Post excluído: todas as fotos dele são removidas. */
    @Transactional
    public List<FotoSubstituida> encerrarFotosDaMealExcluida(Meal meal) {
        Instant agora = agora();
        List<FotoSubstituida> removidas = new ArrayList<>();
        for (var foto : fotosMeal.buscarDaMealParaAtualizacao(meal.getId())) {
            cancelarTarefasAtivas(foto.getId(), agora);
            foto.remover(agora);
            removidas.add(new FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura()));
        }
        return List.copyOf(removidas);
    }

    /** Fotos aprovadas de cada post, na ordem; post sem foto aprovada não aparece no mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, List<FotoPublicaResponse>> aprovadasPorMeal(Collection<UUID> mealIds) {
        if (mealIds.isEmpty()) return Map.of();
        Map<UUID, List<FotoPublicaResponse>> mapa = new HashMap<>();
        for (var foto : fotosMeal.buscarAprovadasPorMeals(mealIds)) {
            var publica = FotoPublicaResponse.de(foto);
            if (publica != null) mapa.computeIfAbsent(foto.getMeal().getId(), id -> new ArrayList<>()).add(publica);
        }
        return mapa;
    }

    @Transactional
    public ResultadoPersistenciaUpload registrarFotoPerfil(UUID autorId, UUID fotoId, String chavePrincipal,
            String chaveMiniatura, ProcessadorImagemFoto.ImagemProcessada imagem) {
        var autor = usuarios.buscarParaAtualizacao(autorId).orElseThrow(RegraNegocioException::inexistente);
        Instant agora = agora();
        List<FotoSubstituida> substituidas = new ArrayList<>();

        fotosPerfil.findByUsuarioAtivoId(autorId).ifPresent(anterior -> {
            cancelarTarefasAtivas(anterior.getId(), agora);
            anterior.remover(agora);
            fotosPerfil.saveAndFlush(anterior);
            substituidas.add(new FotoSubstituida(anterior.getId(), anterior.getChaveArquivo(), anterior.getChaveMiniatura()));
        });

        var nova = fotosPerfil.saveAndFlush(new FotoPerfil(
                fotoId, autor, chavePrincipal, chaveMiniatura, imagem.hashConteudo(),
                imagem.largura(), imagem.altura(), imagem.principal().length, imagem.miniatura().length, agora));
        UUID tarefaId = iniciarModeracao(nova, agora);
        return new ResultadoPersistenciaUpload(FotoResponse.de(nova), tarefaId, List.copyOf(substituidas));
    }

    @Transactional(readOnly = true)
    public FotoResponse consultarStatus(UUID solicitanteId, String solicitanteUsername, UUID fotoId) {
        var foto = fotos.buscarComAutor(fotoId).orElseThrow(RegraNegocioException::inexistente);
        if (!foto.getAutor().getId().equals(solicitanteId) && !config.ehModerador(solicitanteUsername))
            throw RegraNegocioException.proibido();
        return FotoResponse.de(foto);
    }

    @Transactional(readOnly = true)
    public String obterChaveAprovadaParaLeitura(UUID fotoId, boolean miniatura) {
        var foto = fotos.findById(fotoId).orElseThrow(RegraNegocioException::inexistente);
        if (!foto.estaAprovada()) throw RegraNegocioException.inexistente();
        return miniatura ? foto.getChaveMiniatura() : foto.getChaveArquivo();
    }

    @Transactional
    public FotoSubstituida removerPorAutor(UUID autorId, UUID fotoId) {
        var foto = fotos.buscarParaAtualizacao(fotoId).orElseThrow(RegraNegocioException::inexistente);
        if (!foto.getAutor().getId().equals(autorId)) throw RegraNegocioException.proibido();
        // Todo post tem pelo menos 1 foto (RN-10): a foto de um post sai pela edição dele.
        if (foto instanceof FotoMeal fotoMeal && fotoMeal.getMeal() != null)
            throw RegraNegocioException.conflito("Para tirar esta foto, edite o post.");
        Instant agora = agora();
        cancelarTarefasAtivas(fotoId, agora);
        foto.remover(agora);
        fotos.saveAndFlush(foto);
        return new FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura());
    }

    @Transactional
    public Optional<FotoSubstituida> removerFotoPerfilAtual(UUID autorId) {
        usuarios.buscarParaAtualizacao(autorId).orElseThrow(RegraNegocioException::inexistente);
        var atual = fotosPerfil.findByUsuarioAtivoId(autorId);
        if (atual.isEmpty()) return Optional.empty();
        var foto = atual.get();
        Instant agora = agora();
        cancelarTarefasAtivas(foto.getId(), agora);
        foto.remover(agora);
        fotosPerfil.saveAndFlush(foto);
        return Optional.of(new FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura()));
    }

    @Transactional
    public FotoResponse contestar(UUID autorId, UUID fotoId, String motivo) {
        var foto = fotos.buscarParaAtualizacao(fotoId).orElseThrow(RegraNegocioException::inexistente);
        if (!foto.getAutor().getId().equals(autorId)) throw RegraNegocioException.proibido();
        Instant agora = agora();
        foto.solicitarContestacao(agora);
        fotos.saveAndFlush(foto);
        registros.save(RegistroModeracao.humano(
                foto, ResultadoModeracao.REVISAR, "Contestação do autor: " + motivo.strip(),
                config.versaoPolitica(), autorId, agora));
        return FotoResponse.de(foto);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void marcarArquivosLimpos(UUID fotoId) {
        fotos.buscarParaAtualizacao(fotoId).ifPresent(f -> f.marcarArquivosLimpos(agora()));
    }

    @Transactional(readOnly = true)
    public List<FotoSubstituida> listarElegiveisParaLimpeza() {
        Instant limiteRetencao = agora().minus(config.retencaoRejeitadas());
        return fotos.buscarElegiveisParaLimpeza(limiteRetencao).stream()
                .map(f -> new FotoSubstituida(f.getId(), f.getChaveArquivo(), f.getChaveMiniatura()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> buscarChavesRetidasNoBanco() {
        Set<String> chaves = new HashSet<>(fotos.buscarChavesPrincipaisRetidas());
        chaves.addAll(fotos.buscarChavesMiniaturasRetidas());
        return Set.copyOf(chaves);
    }

    private void cancelarTarefasAtivas(UUID fotoId, Instant agora) {
        for (TarefaModeracao tarefa : tarefas.buscarAtivasPorFotoParaAtualizacao(fotoId)) {
            tarefa.cancelar(agora);
        }
    }

    private Instant agora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public record FotoSubstituida(UUID fotoId, String chaveArquivo, String chaveMiniatura) {}
    public record ResultadoPersistenciaUpload(FotoResponse resposta, UUID tarefaId, List<FotoSubstituida> substituidas) {}
    public record ResultadoFotosMeal(List<FotoPublicaResponse> publicas, List<FotoSubstituida> removidas) {}
}
