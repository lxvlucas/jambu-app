package com.jambuapp.api.restaurante;

import java.util.UUID;
import java.time.Instant;
import com.jambuapp.api.comum.RegraNegocioException;
import jakarta.persistence.*;

@Entity
@Table(name = "restaurantes")
public class Restaurante {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Version private long versao;
    @Column(length = 150) private String nome;
    @Column(length = 150) private String nomeNormalizado;
    @Column(length = 300) private String endereco;
    @Column(unique = true, length = 1024) private String googlePlaceId;
    private Instant placesValidadoEm;
    @Enumerated(EnumType.STRING) @Column(length = 20, nullable = false)
    private OrigemRestaurante origem = OrigemRestaurante.LEGADO;
    @Enumerated(EnumType.STRING) @Column(length = 20, nullable = false)
    private SituacaoRestaurante situacao = SituacaoRestaurante.ATIVO;
    private Double latitude;
    private Double longitude;
    @Enumerated(EnumType.STRING) @Column(length = 40) private CategoriaRestaurante categoria;
    @Column(unique = true, length = 128) private String overtureId;
    @Column(length = 30) private String overtureRelease;
    private Integer overtureVersao;
    @Column(columnDefinition = "text") private String fontesOverture;
    @Column(columnDefinition = "text") private String licencasOverture;
    @Column(length = 1000) private String atribuicaoOverture;
    private boolean corrigidoLocalmente;
    private UUID criadoPor;
    private Instant criadoEm = Instant.now();
    private Instant atualizadoEm = Instant.now();

    protected Restaurante() {}
    /** Representação das referências anteriores ao catálogo; não usada para novos cadastros HTTP. */
    public static Restaurante doPlaces(String placeId, Instant validadoEm) {
        var r = new Restaurante();
        r.vincularGoogle(java.util.Objects.requireNonNull(placeId), java.util.Objects.requireNonNull(validadoEm));
        r.origem = OrigemRestaurante.GOOGLE_PLACES;
        return r;
    }
    public static Restaurante proprio(String nome, double latitude, double longitude,
            CategoriaRestaurante categoria, UUID autor, Instant agora) {
        var r = new Restaurante();
        r.definirDados(nome, latitude, longitude, categoria);
        r.origem = OrigemRestaurante.USUARIO;
        r.situacao = SituacaoRestaurante.PROVISORIO;
        r.criadoPor = autor;
        r.criadoEm = agora;
        r.atualizadoEm = agora;
        return r;
    }
    public static Restaurante importado(String nome, double latitude, double longitude,
            CategoriaRestaurante categoria, Instant agora) {
        var r = proprio(nome, latitude, longitude, categoria, null, agora);
        r.origem = OrigemRestaurante.OVERTURE;
        r.situacao = SituacaoRestaurante.ATIVO;
        return r;
    }
    private void definirDados(String nome, double latitude, double longitude, CategoriaRestaurante categoria) {
        this.nome = nome.strip();
        this.nomeNormalizado = NomesRestaurantes.normalizar(nome);
        this.latitude = latitude; this.longitude = longitude; this.categoria = categoria;
    }
    public void vincularGoogle(String placeId, Instant agora) {
        googlePlaceId = placeId; placesValidadoEm = agora;
    }
    public void atualizarOverture(String id, String release, int versao, String fontes, String licencas, String atribuicao,
            String nome, double lat, double lon, CategoriaRestaurante categoria, String endereco, Instant agora) {
        overtureId = id; overtureRelease = release; overtureVersao = versao;
        fontesOverture = fontes; licencasOverture = licencas;
        atribuicaoOverture = atribuicao;
        if (!corrigidoLocalmente) {
            definirDados(nome, lat, lon, categoria);
            this.endereco = endereco;
        }
        atualizadoEm = agora;
    }
    public void revisar(SituacaoRestaurante situacao, Instant agora) {
        this.situacao = situacao; atualizadoEm = agora;
    }
    public void corrigir(String nome, double latitude, double longitude, CategoriaRestaurante categoria, Instant agora) {
        definirDados(nome, latitude, longitude, categoria);
        corrigidoLocalmente = true; atualizadoEm = agora;
    }
    public void exigirSelecionavel() {
        if (situacao == SituacaoRestaurante.REJEITADO)
            throw RegraNegocioException.conflito("Este restaurante não está disponível para novas publicações.");
        if (nome != null && !nome.isBlank()) return;
        if (googlePlaceId != null && placesValidadoEm != null) return;
        throw RegraNegocioException.conflito("Complete os dados locais do restaurante antes de publicar.");
    }
    public UUID getId() { return id; }
    public long getVersao() { return versao; }
    public String getNome() { return nome; }
    public String getNomeNormalizado() { return nomeNormalizado; }
    public String getEndereco() { return endereco; }
    public String getGooglePlaceId() { return googlePlaceId; }
    public OrigemRestaurante getOrigem() { return origem; }
    public SituacaoRestaurante getSituacao() { return situacao; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public CategoriaRestaurante getCategoria() { return categoria; }
    public String getOvertureId() { return overtureId; }
    public String getOvertureRelease() { return overtureRelease; }
    public Integer getOvertureVersao() { return overtureVersao; }
    public String getFontesOverture() { return fontesOverture; }
    public String getLicencasOverture() { return licencasOverture; }
    public String getAtribuicaoOverture() { return atribuicaoOverture; }
    public boolean isCorrigidoLocalmente() { return corrigidoLocalmente; }
}
