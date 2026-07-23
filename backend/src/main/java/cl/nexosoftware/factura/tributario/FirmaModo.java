package cl.nexosoftware.factura.tributario;

/**
 * Origen del certificado de firma ({@code app.sii.firma-modo}).
 *
 * NO es un perfil de Spring: el ambiente de certificacion corre con perfil
 * {@code prod} + {@code APP_SII_AMBIENTE=CERTIFICACION} + modo GLOBAL, y la
 * produccion multi-tenant con el mismo perfil + modo POR_EMPRESA.
 */
public enum FirmaModo {

    /** Un solo PKCS#12 por ambiente (APP_SII_CERT_PATH), fail-fast en arranque. */
    GLOBAL,

    /** Cada empresa firma con su certificado cifrado en BD (multi-tenant). */
    POR_EMPRESA;

    /** Acepta "GLOBAL"/"POR_EMPRESA" en cualquier caja, con guion o guion bajo. */
    public static FirmaModo desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return GLOBAL;
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        try {
            return FirmaModo.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.sii.firma-modo invalido: '" + valor + "' (use GLOBAL o POR_EMPRESA)");
        }
    }
}
