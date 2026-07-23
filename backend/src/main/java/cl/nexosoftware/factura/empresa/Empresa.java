package cl.nexosoftware.factura.empresa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Empresa emisora (contribuyente). Concentra los datos que el SII exige en la
 * cabecera del DTE: RUT, razon social, giro, actividad economica y direccion.
 */
@Entity
@Table(name = "empresa")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RUT del emisor en formato 76543210-9. */
    @Column(nullable = false, unique = true, length = 12)
    private String rut;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(nullable = false)
    private String giro;

    /** Codigo de actividad economica del SII (ACTECO). */
    @Column(name = "actividad_economica")
    private Integer actividadEconomica;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false)
    private String comuna;

    /**
     * Direccion Regional o Unidad del SII a la que pertenece el emisor. Se rotula
     * bajo el recuadro del tipo de documento en la representacion impresa (Manual
     * de Muestras Impresas 1.1.4). Ej: "S.I.I. - SANTIAGO ORIENTE".
     */
    @Column(name = "unidad_sii")
    private String unidadSii;

    private String ciudad;

    private String telefono;

    private String email;

    /**
     * Fecha de la resolucion del SII que autoriza a la empresa como emisor
     * electronico (FchResol de la caratula de los envios). Nullable: mientras
     * este vacia rige el fallback de entorno (APP_SII_FCH_RESOL). Debe ir
     * junto a {@link #nroResol}: ambos o ninguno.
     */
    @Column(name = "fch_resol")
    private LocalDate fchResol;

    /** Numero de la resolucion del SII (NroResol; 0 en certificacion). */
    @Column(name = "nro_resol")
    private Integer nroResol;

    /** Control de concurrencia optimista (evita el lost update en edicion). */
    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
    }
}
