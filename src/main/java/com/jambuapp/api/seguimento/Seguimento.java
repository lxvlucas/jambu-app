package com.jambuapp.api.seguimento;

import java.time.Instant;
import java.util.UUID;
import com.jambuapp.api.usuario.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "seguimentos", uniqueConstraints = @UniqueConstraint(columnNames = {"seguidor_id", "seguido_id"}))
public class Seguimento {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario seguidor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario seguido;
    @Column(nullable = false)
    private Instant criadoEm;
    protected Seguimento() {}
    public Seguimento(Usuario seguidor, Usuario seguido, Instant criadoEm) {
        this.seguidor = seguidor;
        this.seguido = seguido;
        this.criadoEm = criadoEm;
    }
    public Usuario getSeguidor() { return seguidor; }
    public Usuario getSeguido() { return seguido; }
}
