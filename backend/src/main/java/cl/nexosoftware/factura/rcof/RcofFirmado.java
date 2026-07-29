package cl.nexosoftware.factura.rcof;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Registro de un RCOF (ConsumoFolios) firmado: queda cada vez que se genera el
 * archivo firmado de un dia.
 *
 * NO es un registro de envios al SII. El envio del consumo de folios dejo de ser
 * obligatorio con la Res. Ex. SII N°53 de 2022; el archivo se adjunta a mano al
 * correo de certificacion de boletas, y el sistema no tiene forma de saber si
 * eso ocurrio. Por eso aqui solo se afirma lo verificable: se genero el archivo.
 *
 * Su razon de ser es {@code secEnvio}: el esquema del SII lo define como 1 la
 * primera vez y +1 en cada correccion del mismo periodo, asi que hace falta
 * recordar cuantas veces se rehizo el dia. Puede repetirse dentro de un mismo
 * dia: regenerar el mismo numero es legitimo (el archivo anterior nunca se
 * presento), y cada fila registra una generacion real, no un numero reservado.
 */
@Entity
@Table(name = "rcof_firmado")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RcofFirmado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Dia reportado (FchInicio = FchFinal en la caratula). */
    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    /** Secuencia declarada en la caratula; 1..999 (totalDigits=3 en el XSD). */
    @Column(name = "sec_envio", nullable = false, updatable = false)
    private int secEnvio;

    @Column(name = "tmst_firma", nullable = false, updatable = false)
    private OffsetDateTime tmstFirma;

    @PrePersist
    void onCreate() {
        this.tmstFirma = OffsetDateTime.now();
    }
}
