package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.meal.Meal;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

/** Foto do post (T16): solta (sem Meal) até a publicação; depois, da Meal, na posição `ordem` (1 = capa). */
@Entity
@Table(name = "fotos_meal")
public class FotoMeal extends Foto {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id")
    private Meal meal;
    private Integer ordem;

    protected FotoMeal() {}

    public FotoMeal(UUID id, Usuario autor, String chaveArquivo, String chaveMiniatura, String hashConteudo,
            int largura, int altura, int tamanhoBytes, int tamanhoMiniaturaBytes, Instant agora) {
        super(id, autor, chaveArquivo, chaveMiniatura, hashConteudo, largura, altura, tamanhoBytes, tamanhoMiniaturaBytes, agora);
    }

    @Override
    protected boolean vinculoAtivoParaLeitura() {
        return meal != null && meal.getExcluidaEm() == null;
    }

    @Override
    protected void desativarVinculoAtual() {
        this.meal = null;
        this.ordem = null;
    }

    public Meal getMeal() { return meal; }
    public Integer getOrdem() { return ordem; }
}
