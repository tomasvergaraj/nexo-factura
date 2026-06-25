package cl.nexosoftware.factura.documento;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento Tributario Electronico (cabecera). Agrega lineas de detalle y, para
 * notas de credito/debito, referencias al documento corregido. Los montos se
 * almacenan como enteros en pesos chilenos (CLP).
 */
@Entity
@Table(name = "documento_tributario",
        uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "tipo_dte", "folio"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentoTributario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dte", nullable = false, length = 20)
    private TipoDte tipoDte;

    /** Folio asignado al emitir. Null mientras el documento es BORRADOR. */
    private Long folio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDte estado;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    // --- Snapshot del receptor (se congela al emitir) ---
    @Column(name = "receptor_rut", nullable = false, length = 12)
    private String receptorRut;

    @Column(name = "receptor_razon_social", nullable = false)
    private String receptorRazonSocial;

    @Column(name = "receptor_giro")
    private String receptorGiro;

    @Column(name = "receptor_direccion")
    private String receptorDireccion;

    @Column(name = "receptor_comuna")
    private String receptorComuna;

    // --- Totales ---
    @Column(nullable = false)
    private long neto;

    @Column(nullable = false)
    private long exento;

    /** Tasa de IVA vigente al emitir (ej: 19.0). */
    @Column(name = "tasa_iva", nullable = false)
    private double tasaIva;

    @Column(nullable = false)
    private long iva;

    @Column(nullable = false)
    private long total;

    // --- Trazas del proceso tributario ---
    @Column(name = "xml_dte", columnDefinition = "text")
    private String xmlDte;

    @Column(name = "track_id")
    private String trackId;

    @Column(columnDefinition = "text")
    private String observacion;

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroLinea asc")
    @Builder.Default
    private List<LineaDetalle> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Referencia> referencias = new ArrayList<>();

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
        this.actualizadoEn = this.creadoEn;
    }

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }

    public void agregarLinea(LineaDetalle linea) {
        linea.setDocumento(this);
        linea.setNumeroLinea(this.lineas.size() + 1);
        this.lineas.add(linea);
    }

    public void agregarReferencia(Referencia referencia) {
        referencia.setDocumento(this);
        this.referencias.add(referencia);
    }
}
