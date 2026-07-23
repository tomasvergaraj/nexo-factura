package cl.nexosoftware.factura.tributario;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identidad de firma cargada desde un PKCS#12: clave privada, certificado X.509
 * y RUN del firmante. Es un VALUE OBJECT inmutable (no un bean): el origen del
 * PKCS#12 lo decide {@link CertificadoResolver} segun el modo de firma (archivo
 * global del ambiente o BD cifrada por empresa).
 *
 * El RUN del firmante (RutEnvia/rutSender de los envios al SII) se extrae del
 * atributo SERIALNUMBER (OID 2.5.4.5) del subject, donde las CA chilenas lo
 * colocan; {@code rutOverride} actua de override/fallback.
 *
 * Los errores de carga/validacion se reportan como {@link IllegalStateException}
 * con mensaje accionable; quien resuelve por empresa los traduce a error de
 * negocio, y en la carga global de arranque abortan el contexto (fail-fast).
 */
public final class CertificadoFirma {

    private static final Pattern SERIALNUMBER = Pattern.compile("SERIALNUMBER=([0-9]+-[0-9Kk])");

    private final PrivateKey clavePrivada;
    private final X509Certificate certificado;
    private final String rutFirmante;
    private final String huellaSha256;

    private CertificadoFirma(PrivateKey clavePrivada, X509Certificate certificado, String rutFirmante) {
        this.clavePrivada = clavePrivada;
        this.certificado = certificado;
        this.rutFirmante = rutFirmante;
        this.huellaSha256 = calcularHuella(certificado);
    }

    /**
     * Abre el PKCS#12, valida la vigencia del certificado y resuelve el RUN del
     * firmante. Lanza {@link IllegalStateException} si la clave es incorrecta,
     * no contiene clave privada, esta vencido o no se puede extraer el RUN.
     */
    public static CertificadoFirma desdeP12(byte[] p12, String password, String rutOverride) {
        PrivateKey clave;
        X509Certificate cert;
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(p12), password.toCharArray());
            String alias = aliasConClave(ks);
            clave = (PrivateKey) ks.getKey(alias, password.toCharArray());
            cert = (X509Certificate) ks.getCertificate(alias);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo abrir el certificado PKCS#12: clave incorrecta o archivo corrupto", e);
        }

        try {
            cert.checkValidity();
        } catch (Exception e) {
            throw new IllegalStateException("El certificado digital esta vencido o aun no es valido: "
                    + cert.getNotBefore() + " → " + cert.getNotAfter());
        }

        String rut = resolverRutFirmante(cert, rutOverride);
        return new CertificadoFirma(clave, cert, rut);
    }

    /** Variante desde archivo (modo GLOBAL: el PKCS#12 unico del ambiente). */
    public static CertificadoFirma desdeArchivo(String path, String password, String rutOverride) {
        if (!Files.isReadable(Path.of(path))) {
            throw new IllegalStateException("El certificado PKCS#12 no existe o no es legible: " + path);
        }
        byte[] p12;
        try {
            p12 = Files.readAllBytes(Path.of(path));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el certificado PKCS#12: " + path, e);
        }
        try {
            return desdeP12(p12, password, rutOverride);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(e.getMessage() + " (" + path + ")", e.getCause());
        }
    }

    private static String aliasConClave(KeyStore ks) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("El PKCS#12 no contiene ninguna clave privada");
    }

    private static String resolverRutFirmante(X509Certificate cert, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toUpperCase();
        }
        // RFC2253 con el OID mapeado imprime SERIALNUMBER=<valor> como string.
        String dn = cert.getSubjectX500Principal()
                .getName(X500Principal.RFC2253, Map.of("2.5.4.5", "SERIALNUMBER"));
        Matcher m = SERIALNUMBER.matcher(dn.toUpperCase());
        if (m.find()) {
            return m.group(1);
        }
        throw new IllegalStateException(
                "No se pudo extraer el RUT del firmante del certificado (SERIALNUMBER ausente del subject: "
                        + dn + "). Indique el RUT del firmante manualmente.");
    }

    private static String calcularHuella(X509Certificate cert) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la huella del certificado", e);
        }
    }

    public PrivateKey clavePrivada() {
        return clavePrivada;
    }

    public X509Certificate certificado() {
        return certificado;
    }

    /** RUN de la persona autorizada que firma (RutEnvia/rutSender en el SII). */
    public String rutFirmante() {
        return rutFirmante;
    }

    /** SHA-256 (hex) del certificado DER: clave estable para caches (token, parseo). */
    public String huellaSha256() {
        return huellaSha256;
    }

    /** Subject legible (RFC1779), para logs y metadata. */
    public String subject() {
        return certificado.getSubjectX500Principal().getName(X500Principal.RFC1779);
    }
}
