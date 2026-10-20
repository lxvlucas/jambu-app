package com.jambuapp.api.usuario;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 30)
    private String username;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 20)
    private String celular;
    @Column(nullable = false, length = 255)
    private String senhaHash;
    @Column(nullable = false)
    private Instant criadoEm;
    protected Usuario() {}
    public Usuario(String username, String email, String celular, String senhaHash, Instant criadoEm) {
        atualizar(username, email, celular);
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }
    public void atualizar(String username, String email, String celular) {
        this.username = normalizar(username);
        this.email = normalizar(email);
        this.celular = celular.strip();
    }
    public static String normalizar(String texto) { return texto.strip().toLowerCase(Locale.ROOT); }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getCelular() { return celular; }
    public String getSenhaHash() { return senhaHash; }
}
