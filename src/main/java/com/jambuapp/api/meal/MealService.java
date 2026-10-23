package com.jambuapp.api.meal;

import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.foto.*;
import com.jambuapp.api.usuario.*;
import com.jambuapp.api.restaurante.*;
import com.jambuapp.api.seguimento.SeguimentoRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MealService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final UsuarioRepository usuarios;
    private final RestauranteRepository restaurantes;
    private final MealRepository meals;
    private final PublicacaoIdempotenteRepository idempotencias;
    private final SeguimentoRepository seguimentos;
    private final FotoDishRepository fotosDish;
    private final FotoTransacionalService fotoTransacional;
    private final FotoService fotoService;
    private final JambuProperties config;
    private final Clock clock;
    private final JsonMapper json;
    private final Validator validator;
    public MealService(UsuarioRepository usuarios, RestauranteRepository restaurantes, MealRepository meals,
            PublicacaoIdempotenteRepository idempotencias, SeguimentoRepository seguimentos,
            FotoDishRepository fotosDish, FotoTransacionalService fotoTransacional, FotoService fotoService,
            JambuProperties config, Clock clock, JsonMapper json, Validator validator) {
        this.usuarios = usuarios;
        this.restaurantes = restaurantes;
        this.meals = meals;
        this.idempotencias = idempotencias;
        this.seguimentos = seguimentos;
        this.fotosDish = fotosDish;
        this.fotoTransacional = fotoTransacional;
        this.fotoService = fotoService;
        this.config = config;
        this.clock = clock;
        this.json = json;
        this.validator = validator;
    }
    @Transactional
    public MealResponse publicar(UUID autorId, String chave, MealRequest entrada) {
        if (chave == null || !chave.matches("[a-zA-Z0-9._:-]{1,128}"))
            throw RegraNegocioException.invalido("Informe Idempotency-Key com 1 a 128 caracteres alfanuméricos, ponto, _, : ou -.");
        var request = normalizar(entrada);
        if (request.versao() != null || request.dishes().stream().anyMatch(d -> d.id() != null))
            throw RegraNegocioException.invalido("Criação não aceita versão ou IDs de Dishes.");
        String hash = Hash.sha256(json.writeValueAsString(request));
        var autor = usuarios.buscarParaAtualizacao(autorId).orElseThrow(RegraNegocioException::inexistente);
        var anterior = idempotencias.findByAutorIdAndChave(autorId, chave);
        if (anterior.isPresent()) {
            if (!anterior.get().correspondeAo(hash)) throw RegraNegocioException.conflito("Chave de idempotência já usada com outro conteúdo.");
            return json.readValue(anterior.get().getRespostaJson(), MealResponse.class);
        }
        var restaurante = restaurantes.buscarParaSelecao(request.restauranteId()).orElseThrow(RegraNegocioException::inexistente);
        restaurante.exigirSelecionavel();
        // PostgreSQL armazena microssegundos. O snapshot e o cursor usam a mesma precisão.
        var agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var data = request.dataRefeicao() == null ? agora.atZone(FUSO).toLocalDate() : request.dataRefeicao();
        var meal = meals.saveAndFlush(new Meal(autor, restaurante, request, agora, data));
        var resposta = MealResponse.de(meal);
        idempotencias.save(new PublicacaoIdempotente(autor, meal, chave, hash, json.writeValueAsString(resposta), agora));
        return resposta;
    }
    @Transactional
    public MealResponse editar(UUID autorId, UUID id, MealRequest entrada) {
        var meal = propria(autorId, id);
        var request = normalizar(entrada);
        var restaurante = restaurantes.buscarParaSelecao(request.restauranteId()).orElseThrow(RegraNegocioException::inexistente);
        // Edição histórica preservada; trocar de restaurante exige um local selecionável.
        if (!meal.getRestaurante().getId().equals(restaurante.getId())) restaurante.exigirSelecionavel();
        Set<UUID> mantidos = request.dishes().stream().map(DishRequest::id).filter(Objects::nonNull).collect(Collectors.toSet());
        List<UUID> removidos = meal.getDishes().stream().map(Dish::getId).filter(dId -> !mantidos.contains(dId)).toList();
        var fotosParaLimpar = fotoTransacional.desassociarFotosDeDishesRemovidos(removidos);
        var data = request.dataRefeicao() == null ? meal.getDataRefeicao() : request.dataRefeicao();
        meal.editar(restaurante, request, clock.instant().truncatedTo(ChronoUnit.MICROS), data);
        meals.flush();
        agendarLimpezaPosCommit(fotosParaLimpar);
        List<UUID> dishIdsAtuais = meal.getDishes().stream().map(Dish::getId).toList();
        Map<UUID, FotoPublicaResponse> fotosAprovadas = new HashMap<>();
        if (!dishIdsAtuais.isEmpty()) {
            for (var foto : fotosDish.buscarAprovadasPorDishes(dishIdsAtuais)) {
                var dto = FotoPublicaResponse.de(foto);
                if (dto != null && foto.getDishAtivoId() != null) fotosAprovadas.put(foto.getDishAtivoId(), dto);
            }
        }
        return MealResponse.de(meal, fotosAprovadas);
    }
    @Transactional
    public void excluir(UUID autorId, UUID id) {
        var meal = propria(autorId, id);
        List<UUID> dishIds = meal.getDishes().stream().map(Dish::getId).toList();
        var fotosParaLimpar = fotoTransacional.encerrarFotosDeDishesDaMealExcluida(dishIds);
        meal.excluir(clock.instant());
        meals.flush();
        agendarLimpezaPosCommit(fotosParaLimpar);
    }
    @Transactional(readOnly = true)
    public URI gerarLink(UUID usuarioId, UUID id) {
        var meal = meals.findByIdAndExcluidaEmIsNull(id).orElseThrow(RegraNegocioException::inexistente);
        if (!meal.getAutor().getId().equals(usuarioId)
                && !seguimentos.existsBySeguidorIdAndSeguidoId(usuarioId, meal.getAutor().getId()))
            throw RegraNegocioException.proibido();
        String base = config.urlCompartilhamento().toString();
        return URI.create(base + (base.endsWith("/") ? "" : "/") + id);
    }
    private void agendarLimpezaPosCommit(List<FotoTransacionalService.FotoSubstituida> fotosParaLimpar) {
        if (fotosParaLimpar == null || fotosParaLimpar.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fotoService.limparSubstituidas(fotosParaLimpar);
                }
            });
        } else {
            fotoService.limparSubstituidas(fotosParaLimpar);
        }
    }
    private Meal propria(UUID autorId, UUID id) {
        var meal = meals.findByIdAndExcluidaEmIsNull(id).orElseThrow(RegraNegocioException::inexistente);
        if (!meal.getAutor().getId().equals(autorId)) throw RegraNegocioException.proibido();
        return meal;
    }
    private MealRequest normalizar(MealRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw RegraNegocioException.invalido("Dados da Meal inválidos.");
        if (request.dishes().size() > config.limiteDishes()) throw RegraNegocioException.invalido("Quantidade de Dishes excede o limite.");
        if (request.review() != null && request.review().length() > config.limiteReview())
            throw RegraNegocioException.invalido("Review excede o limite de caracteres.");
        request.dishes().forEach(d -> Dish.validarNota(d.nota()));
        if (request.dataRefeicao() != null && request.dataRefeicao().isAfter(clock.instant().atZone(FUSO).toLocalDate()))
            throw RegraNegocioException.invalido("A data da refeição não pode estar no futuro.");
        String review = request.review() == null || request.review().isBlank() ? null : request.review().strip();
        return new MealRequest(request.restauranteId(), request.dishes().stream()
                .map(d -> new DishRequest(d.id(), d.nome().strip(), d.nota().setScale(1), d.comeriaNovamente(),
                        d.preco() == null ? null : d.preco().setScale(2, java.math.RoundingMode.HALF_UP))).toList(),
                review, request.comeriaNovamente(), request.modalidade(), request.dataRefeicao(), request.versao());
    }
}
