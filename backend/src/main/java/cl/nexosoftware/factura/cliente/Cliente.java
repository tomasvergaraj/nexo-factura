package cl.nexosoftware.factura.cliente;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Cliente receptor del DTE. */
@Entity
@Table(name = "cliente", uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "rut"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa propietaria del registro (multi-emisor). */
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false, length = 12)
    private String rut;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    private String giro;

    private String direccion;

    private String comuna;

    private String ciudad;

    private String email;

    @Column(nullable = false)
    private boolean activo;

    /** Control de concurrencia optimista (evita el lost update en edicion). */
    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
        this.activo = true;
    }
}
