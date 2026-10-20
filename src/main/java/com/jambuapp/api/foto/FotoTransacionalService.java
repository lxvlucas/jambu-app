package com.jambuapp.api.foto;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.meal.Dish;
import com.jambuapp.api.meal.MealRepository;
import com.jambuapp.api.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FotoTransacionalService {
    private final UsuarioRepository usuarios;
    private final MealRepository meals;
    private final FotoRepository fotos;
    private final FotoPerfilRepository fotosPerfil;
    private final FotoDishRepository fotosDish;
    private final TarefaModeracaoRepository tarefas;
    private final RegistroModeracaoRepository registros;
    private final FotoProperties config;
    private final Clock clock;

    public FotoTransacionalService(UsuarioRepository usuarios, MealRepository meals, FotoRepository fotos,
            FotoPerfilRepository fotosPerfil, FotoDishRepository fotosDish,
            TarefaModeracaoRepository tarefas, RegistroModeracaoRepository registros,
            FotoProperties config, Clock clock) {
        this.usuarios = usuarios;
        this.meals = meals;
        this.fotos = fotos;
        this.fotosPerfil = fotosPerfil;
        this.fotosDish = fotosDish;
        this.tarefas = tarefas;
        this.registros = registros;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void validarAutorPerfil(UUID autorId) {
        if (!usuarios.existsById(autorId)) throw RegraNegocioException.inexistente();
    }

    @Transactional(readOnly = true)
    public void validarAutorEDish(UUID autorId, UUID dishId) {
        var meal = meals.buscarAtivaPorDishId(dishId).orElseThrow(RegraNegocioException::inexistente);
        if (!meal.getAutor().getId().equals(autorId)) throw RegraNegocioException.proibido();
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
        var tarefa = tarefas.saveAndFlush(new TarefaModeracao(nova, config.versaoPolitica(), agora));
        return new ResultadoPersistenciaUpload(FotoResponse.de(nova), tarefa.getId(), List.copyOf(substituidas));
    }

    @Transactional
    public ResultadoPersistenciaUpload registrarFotoDish(UUID autorId, UUID dishId, UUID fotoId, String chavePrincipal,
            String chaveMiniatura, ProcessadorImagemFoto.ImagemProcessada imagem) {
        var autor = usuarios.buscarParaAtualizacao(autorId).orElseThrow(RegraNegocioException::inexistente);
        var meal = meals.buscarAtivaPorDishId(dishId).orElseThrow(RegraNegocioException::inexistente);
        if (!meal.getAutor().getId().equals(autorId)) throw RegraNegocioException.proibido();
        Dish dish = meal.getDishes().stream().filter(d -> d.getId().equals(dishId)).findFirst()
                .orElseThrow(RegraNegocioException::inexistente);

        Instant agora = agora();
        List<FotoSubstituida> substituidas = new ArrayList<>();
        fotosDish.findByDishAtivoId(dishId).ifPresent(anterior -> {
            cancelarTarefasAtivas(anterior.getId(), agora);
            anterior.remover(agora);
            fotosDish.saveAndFlush(anterior);
            substituidas.add(new FotoSubstituida(anterior.getId(), anterior.getChaveArquivo(), anterior.getChaveMiniatura()));
        });

        var nova = fotosDish.saveAndFlush(new FotoDish(
                fotoId, autor, dish, chavePrincipal, chaveMiniatura, imagem.hashConteudo(),
                imagem.largura(), imagem.altura(), imagem.principal().length, imagem.miniatura().length, agora));
        var tarefa = tarefas.saveAndFlush(new TarefaModeracao(nova, config.versaoPolitica(), agora));
        return new ResultadoPersistenciaUpload(FotoResponse.de(nova), tarefa.getId(), List.copyOf(substituidas));
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
        if (foto instanceof FotoDish) {
            foto = fotosDish.buscarComDishEMeal(fotoId).orElseThrow(RegraNegocioException::inexistente);
        }
        if (!foto.estaAprovada()) throw RegraNegocioException.inexistente();
        return miniatura ? foto.getChaveMiniatura() : foto.getChaveArquivo();
    }

    @Transactional
    public FotoSubstituida removerPorAutor(UUID autorId, UUID fotoId) {
        var foto = fotos.buscarParaAtualizacao(fotoId).orElseThrow(RegraNegocioException::inexistente);
        if (!foto.getAutor().getId().equals(autorId)) throw RegraNegocioException.proibido();
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

    @Transactional
    public List<FotoSubstituida> desassociarFotosDeDishesRemovidos(Collection<UUID> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) return List.of();
        List<FotoDish> encontradas = fotosDish.buscarPorDishes(dishIds);
        if (encontradas.isEmpty()) return List.of();
        Instant agora = agora();
        List<FotoSubstituida> removidas = new ArrayList<>();
        for (FotoDish foto : encontradas) {
            cancelarTarefasAtivas(foto.getId(), agora);
            foto.desassociarDishRemovido(agora);
            fotosDish.saveAndFlush(foto);
            if (foto.getArquivosLimposEm() == null) {
                removidas.add(new FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura()));
            }
        }
        return List.copyOf(removidas);
    }

    @Transactional
    public List<FotoSubstituida> encerrarFotosDeDishesDaMealExcluida(Collection<UUID> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) return List.of();
        List<FotoDish> encontradas = fotosDish.buscarPorDishes(dishIds);
        if (encontradas.isEmpty()) return List.of();
        Instant agora = agora();
        List<FotoSubstituida> removidas = new ArrayList<>();
        for (FotoDish foto : encontradas) {
            if (foto.isAtual() || foto.getStatus() != StatusModeracaoFoto.REMOVIDA) {
                cancelarTarefasAtivas(foto.getId(), agora);
                foto.remover(agora);
                fotosDish.saveAndFlush(foto);
            }
            if (foto.getArquivosLimposEm() == null) {
                removidas.add(new FotoSubstituida(foto.getId(), foto.getChaveArquivo(), foto.getChaveMiniatura()));
            }
        }
        return List.copyOf(removidas);
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
}
