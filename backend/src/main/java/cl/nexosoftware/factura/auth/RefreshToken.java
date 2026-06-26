package cl.nexosoftware.factura.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Refresh token revocable. Se persiste SOLO el hash SHA-256 (hex) del token
 * opaco; el valor crudo se entrega una vez al cliente y nunca se almacena.
 */
@Entity
@Table(name = "refresh_token")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
    }
}
