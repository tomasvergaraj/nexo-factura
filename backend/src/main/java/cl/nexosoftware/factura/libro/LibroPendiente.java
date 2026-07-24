package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Marcador de un libro IECV pendiente de envio, resultado de la revision
 * automatica mensual ({@link RevisionLibroJob}).
 *
 * El job PREPARA el libro del mes anterior (lo firma y valida contra el esquema
 * sin postearlo al SII) y deja aqui el resultado: {@link Estado#PREPARADO}
 * (listo para que el usuario apriete "Enviar") o {@link Estado#ERROR} con el
 * motivo. Hay a lo sumo un marcador por (empresa, periodo, operacion): el job
 * hace upsert en cada corrida.
 */
@Entity
@Table(name = "libro_pendiente")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class LibroPendiente {

    /** Estado de la preparacion del libro por el job. */
    public enum Estado { PREPARADO, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Periodo tributario del libro (AAAA-MM). */
    @Column(nullable = false, updatable = false, length = 7)
    private String periodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, updatable = false, length = 10)
    private TipoOperacion tipoOperacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado;

    /** Motivo cuando {@code estado == ERROR}; null si PREPARADO. */
    @Column(columnDefinition = "text")
    private String detalle;

    @Column(name = "tmst_revision", nullable = false)
    private OffsetDateTime tmstRevision;

    @PrePersist
    @PreUpdate
    void onSave() {
        this.tmstRevision = OffsetDateTime.now();
    }
}
