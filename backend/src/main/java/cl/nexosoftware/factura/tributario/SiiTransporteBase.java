package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

/**
 * Mecanica comun de los transportes al SII (boleta REST y DTE clasico): manejo
 * del token con renovacion + 1 reintento, y armado del upload multipart que
 * ambos canales comparten (rutSender del firmante, rutCompany del emisor y el
 * sobre como archivo ISO-8859-1).
 */
@Slf4j
abstract class SiiTransporteBase implements SiiTransporte {

    private final SiiTokenAuth auth;
    private final String nombreSobre;

    protected SiiTransporteBase(SiiTokenAuth auth, String nombreSobre) {
        this.auth = auth;
        this.nombreSobre = nombreSobre;
    }

    /**
     * Ejecuta la llamada con un token vigente DE LA EMPRESA; ante token
     * invalido, renueva y reintenta UNA vez. Se invalida exactamente el token
     * que fallo (no el que otro hilo pudo renovar entretanto), y un segundo
     * rechazo consecutivo con token recien emitido se traduce a un error con
     * diagnostico: eso ya no es una expiracion, es el certificado sin habilitar
     * o sesiones invalidadas en el SII.
     */
    protected final <T> T conReintentoDeToken(Long empresaId, Function<String, T> llamada) {
        String token = auth.token(empresaId);
        try {
            return llamada.apply(token);
        } catch (TokenInvalidoSii e) {
            log.info("Token del SII invalido/expirado: renovando y reintentando una vez");
            auth.invalidar(empresaId, token);
            try {
                return llamada.apply(auth.token(empresaId));
            } catch (TokenInvalidoSii e2) {
                throw new SiiNoDisponibleException(
                        "El SII rechazo la autenticacion dos veces seguidas, incluso con un token "
                                + "recien emitido: revise la habilitacion del certificado en este ambiente");
            }
        }
    }

    /**
     * Upload multipart comun a ambos canales, armado A MANO como byte[]: el SII
     * rechaza con 400 "Header viene sin 'Content-Length'" los POST chunked, y el
     * RestClient con multipart en streaming no emite Content-Length. Con el
     * cuerpo ya materializado, el largo es conocido y el header sale fijo.
     */
    protected final MultipartUpload multipartUpload(SiiGateway.EnvioSii envio, String sobre) {
        return multipartUpload(envio.rutEmisor(),
                nombreSobre + "_T" + envio.tipoDte() + "F" + envio.folio() + ".xml", sobre);
    }

    protected final MultipartUpload multipartUpload(String rutEmisor, String nombre, String sobre) {
        Rut emisor = Rut.de(rutEmisor);
        // RutEnvia del multipart: el firmante autorizado (mismo de la caratula).
        Rut firmante = Rut.de(rutEnviaDeCaratula(sobre));
        String boundary = "----NexoFactura" + UUID.randomUUID().toString().replace("-", "");

        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream(sobre.length() + 1024);
        campo(cuerpo, boundary, "rutSender", firmante.numero());
        campo(cuerpo, boundary, "dvSender", firmante.dv());
        campo(cuerpo, boundary, "rutCompany", emisor.numero());
        campo(cuerpo, boundary, "dvCompany", emisor.dv());
        escribir(cuerpo, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"archivo\"; filename=\"" + nombre + "\"\r\n"
                + "Content-Type: text/xml\r\n\r\n");
        cuerpo.writeBytes(sobre.getBytes(StandardCharsets.ISO_8859_1));
        escribir(cuerpo, "\r\n--" + boundary + "--\r\n");
        return new MultipartUpload(boundary, cuerpo.toByteArray());
    }

    private static void campo(ByteArrayOutputStream cuerpo, String boundary, String nombre, String valor) {
        escribir(cuerpo, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + nombre + "\"\r\n\r\n"
                + valor + "\r\n");
    }

    private static void escribir(ByteArrayOutputStream cuerpo, String texto) {
        cuerpo.writeBytes(texto.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Cuerpo multipart materializado + su boundary (para el Content-Type). */
    protected record MultipartUpload(String boundary, byte[] cuerpo) {

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }
    }

    /** Extrae el RutEnvia de la caratula ya generada (fuente unica del firmante). */
    private static String rutEnviaDeCaratula(String sobre) {
        int i = sobre.indexOf("<RutEnvia>");
        int j = sobre.indexOf("</RutEnvia>");
        if (i < 0 || j < 0) {
            throw new IllegalStateException("El sobre no contiene RutEnvia");
        }
        return sobre.substring(i + "<RutEnvia>".length(), j);
    }
}
