package cl.nexosoftware.factura.compra;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Documento tributario RECIBIDO (compra), registrado manualmente por la empresa
 * para construir el libro de compras (IECV). Los montos son enteros CLP.
 *
 * A diferencia del DTE emitido, aca no hay ciclo de vida ni XML: es el registro
 * contable del documento que emitio el proveedor. La recepcion automatica de DTE
 * (intercambio) queda gateada por la integracion SII real.
 */
@Entity
@Table(name = "documento_compra",
        uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "tipo_dte", "folio", "rut_proveedor"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Codigo SII del tipo de documento recibido (33, 34, 46, 56, 61). */
    @Column(name = "tipo_dte", nullable = false, updatable = false)
    private int tipoDte;

    @Column(nullable = false, updatable = false)
    private long folio;

    @Column(name = "rut_proveedor", nullable = false, length = 12, updatable = false)
    private String rutProveedor;

    @Column(name = "razon_social", nullable = false, updatable = false)
    private String razonSocial;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private LocalDate fechaEmision;

    @Column(nullable = false, updatable = false)
    private long neto;

    @Column(nullable = false, updatable = false)
    private long exento;

    @Column(nullable = false, updatable = false)
    private long iva;

    /** IVA retenido por el comprador (cambio de sujeto); resta del total. */
    @Column(name = "iva_retenido", nullable = false, updatable = false)
    private long ivaRetenido;

    @Column(nullable = false, updatable = false)
    private long total;

    @Column(columnDefinition = "text", updatable = false)
    private String observacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
    }
}
