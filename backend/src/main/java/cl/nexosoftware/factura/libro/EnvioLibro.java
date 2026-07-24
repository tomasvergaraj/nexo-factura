package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Registro de un envio del libro IECV al SII: queda tras subir el libro firmado
 * por el canal clasico, con el TrackID que devuelve el servicio.
 *
 * El estado real del envio (RECIBIDO / ACEPTADO / ACEPTADO_CON_REPARO /
 * RECHAZADO) no viene en el POST: se resuelve luego por QueryEstUp, asi que
 * {@code estado} arranca nulo y se actualiza al consultar. Se guarda como texto
 * ({@code SiiGateway.EstadoEnvio.name()}) para no acoplar la tabla al enum.
 */
@Entity
@Table(name = "envio_libro")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class EnvioLibro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Periodo tributario del libro enviado (AAAA-MM). */
    @Column(nullable = false, updatable = false, length = 7)
    private String periodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, updatable = false, length = 10)
    private TipoOperacion tipoOperacion;

    @Column(name = "track_id", nullable = false, updatable = false, length = 40)
    private String trackId;

    /** Ultimo estado consultado al SII; null mientras no se consulte. */
    @Column(length = 24)
    private String estado;

    /** MENSUAL (envio normal) | ESPECIAL (set de pruebas de certificacion). */
    @Column(name = "tipo_libro", nullable = false, updatable = false, length = 12)
    private String tipoLibro;

    /** Numero de atencion del SII; solo para el libro ESPECIAL de certificacion. */
    @Column(name = "folio_notificacion", updatable = false)
    private Long folioNotificacion;

    @Column(name = "tmst_envio", nullable = false, updatable = false)
    private OffsetDateTime tmstEnvio;

    @PrePersist
    void onCreate() {
        this.tmstEnvio = OffsetDateTime.now();
    }
}
