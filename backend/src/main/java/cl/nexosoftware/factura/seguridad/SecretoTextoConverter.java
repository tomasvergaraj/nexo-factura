package cl.nexosoftware.factura.seguridad;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cifra en reposo un campo de texto de una entidad, dejando el ciphertext en la
 * MISMA columna {@code text} (formato {@code enc:v1:<base64 del blob GCM>}).
 * Se usa para el {@code xml_caf}: el CAF trae la clave privada RSA (RSASK) con
 * la que se timbra el TED, o sea que un volcado de la BD bastaba para emitir
 * documentos a nombre del contribuyente.
 *
 * <p>El prefijo hace el formato auto-descriptivo y permite convivir con filas
 * LEGACY en claro: al leer, lo que no lo lleva se devuelve tal cual (un CAF real
 * empieza en {@code <?xml} o {@code <AUTORIZACION>}, jamas en {@code enc:}).
 * {@code CafCifradoBackfill} las migra al arrancar. Al escribir siempre se
 * cifra: sin clave maestra la escritura falla en vez de degradar a texto plano.
 *
 * <p>Hibernate resuelve el converter como bean de Spring (SpringBeanContainer),
 * por eso el {@link CifradorSecretos} llega por constructor.
 */
@Component
@Converter
public class SecretoTextoConverter implements AttributeConverter<String, String> {

    /** Marca de contenido cifrado; la version es la del blob (byte 0). */
    public static final String PREFIJO = "enc:v1:";

    private final CifradorSecretos cifrador;

    public SecretoTextoConverter(CifradorSecretos cifrador) {
        this.cifrador = cifrador;
    }

    @Override
    public String convertToDatabaseColumn(String claro) {
        if (claro == null) {
            return null;
        }
        if (claro.startsWith(PREFIJO)) {
            return claro; // ya cifrado (p.ej. re-guardado de un valor no descifrado)
        }
        if (!cifrador.disponible()) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY no esta configurada: no se puede guardar el CAF cifrado en reposo");
        }
        byte[] blob = cifrador.cifrar(claro.getBytes(StandardCharsets.UTF_8));
        return PREFIJO + Base64.getEncoder().encodeToString(blob);
    }

    @Override
    public String convertToEntityAttribute(String almacenado) {
        if (almacenado == null || !almacenado.startsWith(PREFIJO)) {
            return almacenado; // fila legacy en claro (o null)
        }
        byte[] blob = Base64.getDecoder().decode(almacenado.substring(PREFIJO.length()));
        return new String(cifrador.descifrar(blob), StandardCharsets.UTF_8);
    }

    /** El valor almacenado esta cifrado (no es una fila legacy en claro). */
    public static boolean estaCifrado(String almacenado) {
        return almacenado != null && almacenado.startsWith(PREFIJO);
    }
}
