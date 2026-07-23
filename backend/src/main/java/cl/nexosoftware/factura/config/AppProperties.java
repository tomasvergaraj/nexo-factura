package cl.nexosoftware.factura.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propiedades de aplicacion (prefijo "app" en application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Sii sii, Security security) {

    public record Jwt(String secret, long expirationMinutes, long refreshExpirationDays) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * Integracion con el SII.
     *
     * @param ambiente            CERTIFICACION (apicert/pangal, maullin) o PRODUCCION (api/rahue, palena)
     * @param firmaModo           GLOBAL (un solo certificado por ambiente, via cert-path; el modo
     *                            del E2E de certificacion) o POR_EMPRESA (cada empresa firma con su
     *                            certificado cifrado en BD; el modo multi-tenant de produccion)
     * @param certificadoPath     ruta del PKCS#12 del firmante (solo modo GLOBAL)
     * @param certificadoPassword clave del PKCS#12 (solo modo GLOBAL)
     * @param rutFirmante         override/fallback del RUN del firmante (por defecto se extrae
     *                            del SERIALNUMBER del subject del certificado)
     * @param fchResol            FALLBACK de la fecha de resolucion de la caratula (AAAA-MM-DD)
     *                            cuando la empresa no tiene la suya propia; en certificacion es la
     *                            que muestra "Datos de la Empresa" del ambiente cert
     * @param nroResol            FALLBACK del numero de resolucion (0 fijo en certificacion)
     * @param userAgent           User-Agent de las llamadas al SII (su WAF bloquea agentes de libreria)
     */
    public record Sii(String ambiente, String firmaModo, String certificadoPath, String certificadoPassword,
                      String rutFirmante, String fchResol, int nroResol, String userAgent) {}

    /**
     * Seguridad de secretos en reposo.
     *
     * @param masterKey clave maestra AES-256 (32 bytes en base64) con la que se cifran los
     *                  secretos antes de persistirlos: los PKCS#12 de las empresas y sus
     *                  claves, y el XML del CAF (que lleva la clave privada del timbre).
     *                  Obligatoria para cargar CAF y en modo POR_EMPRESA; el default de
     *                  desarrollo vive en application-dev.yml.
     */
    public record Security(String masterKey) {}
}
