package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.empresa.Empresa;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Usuario que accede a la plataforma. Pertenece a una empresa emisora. */
@Entity
@Table(name = "usuario")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
        if (!this.activo) this.activo = true;
    }
}
