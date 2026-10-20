package com.jambuapp.api.foto;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.meal.Dish;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "fotos_dish")
public class FotoDish extends Foto {
    @Column(name = "dish_id_historico", nullable = false)
    private UUID dishIdHistorico;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dish_id")
    private Dish dish;
    @Column(name = "dish_ativo_id", unique = true)
    private UUID dishAtivoId;

    protected FotoDish() {}

    public FotoDish(UUID id, Usuario autor, Dish dish, String chaveArquivo, String chaveMiniatura, String hashConteudo,
            int largura, int altura, int tamanhoBytes, int tamanhoMiniaturaBytes, Instant agora) {
        super(id, autor, chaveArquivo, chaveMiniatura, hashConteudo, largura, altura, tamanhoBytes, tamanhoMiniaturaBytes, agora);
        this.dishIdHistorico = dish.getId();
        this.dish = dish;
        this.dishAtivoId = dish.getId();
    }

    @Override
    protected boolean vinculoAtivoParaLeitura() {
        return dishAtivoId != null && dish != null && dish.estaAtivoParaLeitura();
    }

    @Override
    protected void desativarVinculoAtual() {
        this.dishAtivoId = null;
    }

    public void desassociarDishRemovido(Instant agora) {
        remover(agora);
        this.dish = null;
    }

    public UUID getDishIdHistorico() { return dishIdHistorico; }
    public Dish getDish() { return dish; }
    public UUID getDishAtivoId() { return dishAtivoId; }
}
