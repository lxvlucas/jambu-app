package com.jambuapp.api.restaurante;

import java.util.UUID;
import java.time.Instant;
import com.jambuapp.api.comum.RegraNegocioException;
import jakarta.persistence.*;

@Entity
@Table(name = "restaurantes")
public class Restaurante {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    // Dados próprios legados: mantidos para transição, nunca preenchidos com payload Google.
    @Column(length = 150)
    private String nome;
    @Column(length = 300)
    private String endereco;
    @Column(unique = true, length = 1024)
    private String googlePlaceId;
    private Instant placesValidadoEm;
    protected Restaurante() {}
    public static Restaurante doPlaces(String googlePlaceId, Instant validadoEm) {
        var restaurante = new Restaurante();
        restaurante.googlePlaceId = java.util.Objects.requireNonNull(googlePlaceId);
        restaurante.placesValidadoEm = java.util.Objects.requireNonNull(validadoEm);
        restaurante.exigirValidacaoPlaces();
        return restaurante;
    }
    public void exigirValidacaoPlaces() {
        if (googlePlaceId == null || googlePlaceId.isBlank() || placesValidadoEm == null)
            throw RegraNegocioException.conflito("Selecione um restaurante validado pelo Places para uma nova publicação ou troca de restaurante.");
    }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getEndereco() { return endereco; }
    public String getGooglePlaceId() { return googlePlaceId; }
}
