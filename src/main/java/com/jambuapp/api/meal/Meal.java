package com.jambuapp.api.meal;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.jambuapp.api.comum.RegraNegocioException;
import com.jambuapp.api.usuario.Usuario;
import com.jambuapp.api.restaurante.Restaurante;
import jakarta.persistence.*;

@Entity
@Table(name = "meals")
public class Meal {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Version
    private long versao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario autor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurante restaurante;
    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<Dish> dishes = new ArrayList<>();
    @Column(length = 10000)
    private String review;
    @Column(nullable = false)
    private boolean comeriaNovamente;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Modalidade modalidade;
    @Column(nullable = false)
    private LocalDate dataRefeicao;
    @Column(nullable = false)
    private Instant publicadaEm;
    private Instant editadaEm;
    private Instant excluidaEm;
    protected Meal() {}
    public Meal(Usuario autor, Restaurante restaurante, MealRequest request, Instant agora, LocalDate data) {
        this.autor = autor;
        this.publicadaEm = agora;
        if (request.dishes().stream().anyMatch(d -> d.id() != null))
            throw RegraNegocioException.invalido("Dishes novos não recebem ID do cliente.");
        aplicar(restaurante, request, data);
    }
    public void editar(Restaurante restaurante, MealRequest request, Instant agora, LocalDate data) {
        if (request.versao() == null || request.versao() != versao)
            throw RegraNegocioException.conflito("Informe a versão atual da Meal para editar.");
        aplicar(restaurante, request, data);
        // Garante alteração do agregado mesmo quando somente um Dish mudou ou o Clock foi fixado.
        editadaEm = editadaEm == null || agora.isAfter(editadaEm) ? agora : editadaEm.plusNanos(1000);
    }
    private void aplicar(Restaurante restaurante, MealRequest request, LocalDate data) {
        if (request.dishes() == null || request.dishes().isEmpty())
            throw RegraNegocioException.invalido("A Meal precisa de pelo menos um Dish.");
        var existentes = dishes.stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
        Set<UUID> recebidos = new HashSet<>();
        List<Dish> proximos = new ArrayList<>();
        for (var item : request.dishes()) {
            if (item.id() == null) proximos.add(new Dish(this, item.nome(), item.nota()));
            else {
                var dish = existentes.get(item.id());
                if (dish == null || !recebidos.add(item.id()))
                    throw RegraNegocioException.invalido("ID de Dish repetido ou pertencente a outra Meal.");
                dish.atualizar(item.nome(), item.nota());
                proximos.add(dish);
            }
        }
        dishes.stream().filter(d -> !proximos.contains(d)).forEach(Dish::desassociar);
        dishes.clear();
        dishes.addAll(proximos);
        for (int i = 0; i < dishes.size(); i++) dishes.get(i).ordenar(i);
        this.restaurante = restaurante;
        this.review = request.review();
        this.comeriaNovamente = request.comeriaNovamente();
        this.modalidade = request.modalidade();
        this.dataRefeicao = data;
    }
    @Transient
    public BigDecimal getNotaOverall() {
        return dishes.stream().map(Dish::getNota).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dishes.size()), 2, RoundingMode.HALF_UP);
    }
    public void excluir(Instant em) { excluidaEm = em; }
    public UUID getId() { return id; }
    public long getVersao() { return versao; }
    public Usuario getAutor() { return autor; }
    public Restaurante getRestaurante() { return restaurante; }
    public List<Dish> getDishes() { return Collections.unmodifiableList(dishes); }
    public String getReview() { return review; }
    public boolean isComeriaNovamente() { return comeriaNovamente; }
    public Modalidade getModalidade() { return modalidade; }
    public LocalDate getDataRefeicao() { return dataRefeicao; }
    public Instant getPublicadaEm() { return publicadaEm; }
    public Instant getEditadaEm() { return editadaEm; }
    public Instant getExcluidaEm() { return excluidaEm; }
}
