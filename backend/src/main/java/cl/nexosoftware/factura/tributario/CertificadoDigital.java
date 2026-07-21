package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Certificado digital del firmante (PKCS#12) para el perfil prod.
 *
 * Se carga y valida FAIL-FAST en el arranque (patron JwtSecretValidator): si el
 * archivo no existe, la clave es incorrecta o el certificado esta vencido, el
 * contexto no levanta — mejor que descubrirlo al emitir el primer documento.
 *
 * El RUN del firmante (RutEnvia/rutSender de los envios al SII) se extrae del
 * atributo SERIALNUMBER (OID 2.5.4.5) del subject, donde las CA chilenas lo
 * colocan; {@code app.sii.rut-firmante} actua de override/fallback.
 */
@Component
@Profile("prod")
@Slf4j
public class CertificadoDigital {

    private static final Pattern SERIALNUMBER = Pattern.compile("SERIALNUMBER=([0-9]+-[0-9Kk])");

    private final PrivateKey clavePrivada;
    private final X509Certificate certificado;
    private final String rutFirmante;

    public CertificadoDigital(AppProperties props) {
        String path = props.sii().certificadoPath();
        String password = props.sii().certificadoPassword();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "Perfil prod requiere el certificado digital: configure APP_SII_CERT_PATH");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Perfil prod requiere la clave del certificado: configure APP_SII_CERT_PASSWORD");
        }
        if (!Files.isReadable(Path.of(path))) {
            throw new IllegalStateException("El certificado PKCS#12 no existe o no es legible: " + path);
        }
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            String alias = aliasConClave(ks, password);
            this.clavePrivada = (PrivateKey) ks.getKey(alias, password.toCharArray());
            this.certificado = (X509Certificate) ks.getCertificate(alias);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo abrir el certificado PKCS#12 (" + path + "): clave incorrecta o archivo corrupto", e);
        }

        try {
            certificado.checkValidity();
        } catch (Exception e) {
            throw new IllegalStateException("El certificado digital esta vencido o aun no es valido: "
                    + certificado.getNotBefore() + " → " + certificado.getNotAfter());
        }

        this.rutFirmante = resolverRutFirmante(props.sii().rutFirmante());
        log.info("Certificado digital cargado: subject='{}', vigente hasta {}, RUT firmante {}",
                certificado.getSubjectX500Principal().getName(X500Principal.RFC1779),
                certificado.getNotAfter(), rutFirmante);
    }

    private String aliasConClave(KeyStore ks, String password) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("El PKCS#12 no contiene ninguna clave privada");
    }

    private String resolverRutFirmante(String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toUpperCase();
        }
        // RFC2253 con el OID mapeado imprime SERIALNUMBER=<valor> como string.
        String dn = certificado.getSubjectX500Principal()
                .getName(X500Principal.RFC2253, Map.of("2.5.4.5", "SERIALNUMBER"));
        Matcher m = SERIALNUMBER.matcher(dn.toUpperCase());
        if (m.find()) {
            return m.group(1);
        }
        throw new IllegalStateException(
                "No se pudo extraer el RUT del firmante del certificado (SERIALNUMBER ausente del subject: "
                        + dn + "). Configure APP_SII_RUT_FIRMANTE.");
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
}
