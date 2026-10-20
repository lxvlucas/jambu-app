package com.jambuapp.api.meal;

import java.math.BigDecimal;
import java.util.UUID;
import com.jambuapp.api.comum.RegraNegocioException;
import jakarta.persistence.*;

@Entity
@Table(name = "dishes")
public class Dish {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Meal meal;
    @Column(nullable = false, length = 150)
    private String nome;
    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal nota;
    @Column(nullable = false)
    private int ordem;
    protected Dish() {}
    Dish(Meal meal, String nome, BigDecimal nota) {
        this.meal = meal;
        atualizar(nome, nota);
    }
    public void atualizar(String nome, BigDecimal nota) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 150)
            throw RegraNegocioException.invalido("Informe um nome de Dish com até 150 caracteres.");
        validarNota(nota);
        this.nome = nome.strip();
        this.nota = nota;
    }
    public static void validarNota(BigDecimal nota) {
        if (nota == null || nota.compareTo(new BigDecimal("0.5")) < 0 || nota.compareTo(new BigDecimal("5")) > 0
                || nota.remainder(new BigDecimal("0.5")).signum() != 0)
            throw RegraNegocioException.invalido("Nota deve estar entre 0,5 e 5, em passos de 0,5.");
    }
    void desassociar() { this.meal = null; }
    void ordenar(int ordem) { this.ordem = ordem; }
    public boolean estaAtivoParaLeitura() { return meal != null && meal.getExcluidaEm() == null; }
    public Meal getMeal() { return meal; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public BigDecimal getNota() { return nota; }
}
