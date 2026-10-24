package com.jambuapp.api.meal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import com.jambuapp.api.comum.*;
import com.jambuapp.api.usuario.UsuarioResumoResponse;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comentários (T21, RN-34) em Meal ativa, do mais antigo para o mais novo. */
@Service
@Transactional
public class ComentarioService {
    private final MealRepository meals;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ComentarioService(MealRepository meals, JdbcTemplate jdbc, Clock clock) {
        this.meals = meals; this.jdbc = jdbc; this.clock = clock;
    }
    /** Mesma chave e mesmo texto devolvem o comentário já criado; mesma chave com outro texto é conflito. */
    public ComentarioResponse comentar(UUID autorId, UUID mealId, String chave, ComentarioRequest request) {
        if (chave == null || !chave.matches("[a-zA-Z0-9._:-]{1,128}"))
            throw RegraNegocioException.invalido("Informe Idempotency-Key com 1 a 128 caracteres alfanuméricos, ponto, _, : ou -.");
        String texto = request.texto() == null ? "" : request.texto().strip();
        if (texto.isEmpty()) throw RegraNegocioException.invalido("Escreva o comentário.");
        exigirAtiva(mealId);
        String hash = Hash.sha256(mealId + "\n" + texto);
        // A restrição única (autor, chave) decide quem ganha se o app reenviar ao mesmo tempo.
        jdbc.update("""
                insert into comentarios(id, meal_id, autor_id, texto, chave, hash_requisicao, criado_em)
                values (?, ?, ?, ?, ?, ?, ?) on conflict (autor_id, chave) do nothing
                """, UUID.randomUUID(), mealId, autorId, texto, chave, hash,
                Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)));
        var gravado = jdbc.queryForObject("select id, hash_requisicao from comentarios where autor_id = ? and chave = ?",
                (rs, i) -> new Gravado(rs.getObject(1, UUID.class), rs.getString(2)), autorId, chave);
        if (!hash.equals(gravado.hash())) throw RegraNegocioException.conflito("Chave de idempotência já usada com outro conteúdo.");
        return buscar(gravado.id());
    }
    /**
     * Só o autor do comentário ou o dono do post excluem (RN-34). Repetir a exclusão devolve sucesso;
     * para outra conta, continua 403, mesmo depois de excluído.
     */
    public void excluir(UUID usuarioId, UUID mealId, UUID comentarioId) {
        var meal = meals.findByIdAndExcluidaEmIsNull(mealId).orElseThrow(RegraNegocioException::inexistente);
        var autores = jdbc.query("select autor_id from comentarios where id = ? and meal_id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), comentarioId, mealId);
        if (autores.isEmpty()) throw RegraNegocioException.inexistente();
        if (!usuarioId.equals(autores.getFirst()) && !usuarioId.equals(meal.getAutor().getId())) throw RegraNegocioException.proibido();
        jdbc.update("update comentarios set excluido_em = ? where id = ? and excluido_em is null",
                Timestamp.from(clock.instant()), comentarioId);
    }
    @Transactional(readOnly = true)
    public PaginaCursor<ComentarioResponse> listar(UUID mealId, String cursor, int limite) {
        Paginacao.limite(limite);
        exigirAtiva(mealId);
        var posicao = CursorMeal.ler(cursor);
        var itens = jdbc.query(SELECT + """
                where c.meal_id = ? and c.excluido_em is null
                  and (cast(? as timestamptz) is null or (c.criado_em, c.id) > (?, ?))
                order by c.criado_em, c.id limit ?
                """, (rs, i) -> linha(rs), mealId, ts(posicao), ts(posicao), posicao.id(), limite + 1);
        boolean mais = itens.size() > limite;
        var pagina = itens.subList(0, Math.min(limite, itens.size()));
        var ultimo = pagina.isEmpty() ? null : pagina.getLast();
        return new PaginaCursor<>(pagina, mais ? new CursorMeal(ultimo.criadoEm(), ultimo.id()).codificar() : null);
    }
    /** Total de comentários ativos de várias Meals numa consulta só, para o feed; sem comentário, fora do mapa. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, Long> totais(java.util.Collection<UUID> mealIds) {
        if (mealIds.isEmpty()) return java.util.Map.of();
        var mapa = new java.util.HashMap<UUID, Long>();
        jdbc.query("select meal_id, count(*) from comentarios where meal_id = any(?) and excluido_em is null group by meal_id",
                rs -> { mapa.put(rs.getObject(1, UUID.class), rs.getLong(2)); }, (Object) mealIds.toArray(UUID[]::new));
        return mapa;
    }
    private ComentarioResponse buscar(UUID id) {
        return jdbc.queryForObject(SELECT + " where c.id = ?", (rs, i) -> linha(rs), id);
    }
    private void exigirAtiva(UUID mealId) {
        if (meals.findByIdAndExcluidaEmIsNull(mealId).isEmpty()) throw RegraNegocioException.inexistente();
    }
    private static Timestamp ts(CursorMeal posicao) {
        return posicao.instante() == null ? null : Timestamp.from(posicao.instante());
    }
    private static ComentarioResponse linha(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ComentarioResponse(rs.getObject(1, UUID.class),
                new UsuarioResumoResponse(rs.getObject(2, UUID.class), rs.getString(3)), rs.getString(4), rs.getTimestamp(5).toInstant());
    }
    private static final String SELECT = """
            select c.id, u.id, u.username, c.texto, c.criado_em from comentarios c join usuarios u on u.id = c.autor_id
            """;
    private record Gravado(UUID id, String hash) {}
    public record ComentarioRequest(@NotBlank @Size(max = 1000) String texto) {}
    public record ComentarioResponse(UUID id, UsuarioResumoResponse autor, String texto, java.time.Instant criadoEm) {}
}
