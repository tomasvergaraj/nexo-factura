package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.documento.TipoDte;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Codigo de Autorizacion de Folios (CAF) emitido por el SII.
 *
 * Cada CAF habilita un rango de folios [folioDesde, folioHasta] para un tipo de
 * DTE. El campo {@code folioActual} avanza a medida que se emiten documentos;
 * su asignacion debe ser atomica para evitar folios duplicados bajo concurrencia.
 */
@Entity
@Table(name = "caf")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Caf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dte", nullable = false, length = 20)
    private TipoDte tipoDte;

    @Column(name = "folio_desde", nullable = false)
    private long folioDesde;

    @Column(name = "folio_hasta", nullable = false)
    private long folioHasta;

    /** Ultimo folio asignado. El proximo a emitir es folioActual + 1. */
    @Column(name = "folio_actual", nullable = false)
    private long folioActual;

    /** Contenido XML del CAF firmado por el SII (se usa al timbrar el DTE). */
    @Column(name = "xml_caf", columnDefinition = "text")
    private String xmlCaf;

    @Column(name = "fecha_autorizacion")
    private LocalDate fechaAutorizacion;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(nullable = false)
    private boolean agotado;

    /** Control de concurrencia optimista a nivel de version. */
    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
    }

    public long foliosDisponibles() {
        return folioHasta - folioActual;
    }
}
