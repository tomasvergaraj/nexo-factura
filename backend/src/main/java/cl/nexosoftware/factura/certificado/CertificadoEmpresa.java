package cl.nexosoftware.factura.certificado;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Certificado digital de firma electronica de una empresa (modo POR_EMPRESA).
 *
 * El PKCS#12 y su clave se persisten CIFRADOS (AES-256-GCM via
 * {@code CifradorSecretos}); esta entidad jamas expone el material en claro y
 * los DTO de la API solo devuelven metadata. Historial 1-N con un unico activo
 * por empresa (indice parcial {@code ux_certificado_empresa_activo}): subir un
 * certificado nuevo desactiva el anterior sin borrarlo.
 */
@Entity
@Table(name = "certificado_empresa")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CertificadoEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    /** PKCS#12 cifrado: [version de clave][IV][ciphertext+tag GCM]. */
    @Column(name = "p12_cifrado", nullable = false)
    private byte[] p12Cifrado;

    /** Clave del PKCS#12 cifrada con el mismo formato. */
    @Column(name = "password_cifrada", nullable = false)
    private byte[] passwordCifrada;

    /** RUN del firmante autorizado (RutEnvia/rutSender de los envios al SII). */
    @Column(name = "rut_firmante", nullable = false, length = 12)
    private String rutFirmante;

    /** Subject completo del certificado (informativo, para la UI). */
    @Column(length = 500)
    private String subject;

    @Column(name = "valido_desde", nullable = false)
    private OffsetDateTime validoDesde;

    @Column(name = "valido_hasta", nullable = false)
    private OffsetDateTime validoHasta;

    /** SHA-256 (hex) del certificado DER: identidad estable para caches. */
    @Column(name = "huella_sha256", nullable = false, length = 64)
    private String huellaSha256;

    /** Version de la clave maestra con que se cifro (rotacion futura). */
    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(nullable = false)
    private boolean activo;

    /** Email del usuario que lo subio (auditoria). */
    @Column(name = "creado_por", length = 180)
    private String creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
