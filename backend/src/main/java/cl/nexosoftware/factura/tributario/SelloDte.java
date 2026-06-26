package cl.nexosoftware.factura.tributario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Sello de integridad de un DTE: SHA-256 (hex, 64 chars) del XML firmado.
 *
 * Se fija al emitir y permite detectar manipulacion posterior del XML almacenado
 * recomputando el sello y comparandolo con el guardado. Es deterministico y sin
 * estado.
 */
public final class SelloDte {

    private SelloDte() {}

    public static String calcular(String xml) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(xml.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
