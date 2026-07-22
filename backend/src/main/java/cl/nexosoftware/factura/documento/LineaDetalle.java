package cl.nexosoftware.factura.documento;

import jakarta.persistence.*;
import lombok.*;

/** Linea de detalle de un DTE. Los montos se calculan al construir el documento. */
@Entity
@Table(name = "linea_detalle")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class LineaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoTributario documento;

    @Column(name = "numero_linea", nullable = false)
    private int numeroLinea;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private double cantidad;

    @Column(nullable = false)
    private String unidad;

    @Column(name = "precio_unitario", nullable = false)
    private long precioUnitario;

    /** Descuento en pesos aplicado a la linea. */
    @Column(name = "descuento_monto", nullable = false)
    private long descuentoMonto;

    /**
     * Descuento porcentual de la linea (DescuentoPct del SII); null = sin
     * porcentaje. Cuando esta presente, {@code descuentoMonto} guarda el monto
     * derivado (round(bruto * pct / 100)) para que XML, PDF y totales usen la
     * misma cifra.
     */
    @Column(name = "descuento_pct")
    private Double descuentoPct;

    /** Si es false, la linea es exenta de IVA. */
    @Column(nullable = false)
    private boolean afecto;

    /**
     * Codigo del otro impuesto que afecta a la linea (catalogo {@link
     * cl.nexosoftware.factura.tributario.TipoImpuesto}); null = solo IVA estandar.
     * Solo aplica a lineas afectas de documentos de precios netos (facturas/notas).
     */
    @Column(name = "cod_imp_adic")
    private Integer codImpAdic;

    /** Monto de la linea = cantidad * precioUnitario - descuento. */
    @Column(name = "monto_linea", nullable = false)
    private long montoLinea;
}
