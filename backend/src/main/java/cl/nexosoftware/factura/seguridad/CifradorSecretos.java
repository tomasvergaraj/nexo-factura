package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado en reposo de secretos (PKCS#12 de clientes y sus claves) con
 * AES-256-GCM y una clave maestra de entorno (APP_MASTER_KEY, 32 bytes en
 * base64). La clave maestra jamas se persiste ni se loguea.
 *
 * Formato del blob: {@code [1 byte version de clave][12 bytes IV][ciphertext+tag]}.
 * El IV es aleatorio por cifrado (requisito de GCM: jamas reutilizar IV con la
 * misma clave) y el tag de 128 bits hace el blob a prueba de manipulacion: un
 * byte alterado hace fallar el descifrado completo. El byte de version deja
 * lista una rotacion futura de la clave maestra (re-cifrar y subir la version).
 */
@Component
public class CifradorSecretos {

    /**
     * Clave maestra de DESARROLLO conocida ("nexo-factura-dev-master-key-0123"
     * en base64). DEBE mantenerse sincronizada con el default de
     * application-dev.yml; el modo POR_EMPRESA en produccion la rechaza. Publica
     * (no es secreto: vive en application-dev.yml) para que los guardas de prod
     * se puedan verificar en tests de otros paquetes.
     */
    public static final String CLAVE_DEV = "bmV4by1mYWN0dXJhLWRldi1tYXN0ZXIta2V5LTAxMjM=";

    private static final byte VERSION_ACTUAL = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int CLAVE_BYTES = 32;

    private final SecretKeySpec clave; // null si no hay master key configurada
    private final boolean claveDeDesarrollo;
    private final SecureRandom random = new SecureRandom();

    public CifradorSecretos(AppProperties props) {
        String base64 = props.security() != null ? props.security().masterKey() : null;
        if (base64 == null || base64.isBlank()) {
            this.clave = null;
            this.claveDeDesarrollo = false;
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_MASTER_KEY no es base64 valido");
        }
        if (bytes.length != CLAVE_BYTES) {
            throw new IllegalStateException("APP_MASTER_KEY debe decodificar a exactamente "
                    + CLAVE_BYTES + " bytes (AES-256); tiene " + bytes.length);
        }
        this.clave = new SecretKeySpec(bytes, "AES");
        this.claveDeDesarrollo = CLAVE_DEV.equals(base64.trim());
    }

    /** Hay clave maestra configurada (sin ella, cifrar/descifrar no operan). */
    public boolean disponible() {
        return clave != null;
    }

    /** La clave configurada es la de desarrollo conocida (prohibida en prod POR_EMPRESA). */
    public boolean esClaveDeDesarrollo() {
        return claveDeDesarrollo;
    }

    public byte[] cifrar(byte[] claro) {
        exigirClave();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(claro);

            byte[] blob = new byte[1 + IV_BYTES + cifrado.length];
            blob[0] = VERSION_ACTUAL;
            System.arraycopy(iv, 0, blob, 1, IV_BYTES);
            System.arraycopy(cifrado, 0, blob, 1 + IV_BYTES, cifrado.length);
            return blob;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    public byte[] descifrar(byte[] blob) {
        exigirClave();
        if (blob == null || blob.length < 1 + IV_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException("Blob cifrado invalido (truncado)");
        }
        if (blob[0] != VERSION_ACTUAL) {
            throw new IllegalStateException("Version de clave desconocida en el blob cifrado: " + blob[0]
                    + " (la clave maestra configurada es la version " + VERSION_ACTUAL + ")");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave,
                    new GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES));
            return cipher.doFinal(blob, 1 + IV_BYTES, blob.length - 1 - IV_BYTES);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo descifrar el secreto: clave maestra incorrecta o blob alterado", e);
        }
    }

    /** Version de clave con la que cifra hoy (se persiste junto al blob). */
    public int versionActual() {
        return VERSION_ACTUAL;
    }

    private void exigirClave() {
        if (clave == null) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY no esta configurada: es obligatoria para cifrar/descifrar "
                            + "los certificados digitales de las empresas");
        }
    }
}
