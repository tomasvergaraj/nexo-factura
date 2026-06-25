package cl.nexosoftware.factura.documento;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Referencia a otro documento (obligatoria en notas de credito/debito). */
@Entity
@Table(name = "referencia")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Referencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoTributario documento;

    /** Tipo del documento referenciado (codigo SII, ej: 33). */
    @Column(name = "tipo_documento_ref", nullable = false)
    private int tipoDocumentoRef;

    @Column(name = "folio_ref", nullable = false)
    private long folioRef;

    @Column(name = "fecha_ref", nullable = false)
    private LocalDate fechaRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_referencia", nullable = false, length = 20)
    private TipoReferencia tipoReferencia;

    @Column(nullable = false)
    private String razon;
}
