package cl.nexosoftware.factura.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propiedades de aplicacion (prefijo "app" en application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Sii sii) {

    public record Jwt(String secret, long expirationMinutes, long refreshExpirationDays) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * Integracion con el SII.
     *
     * @param ambiente            CERTIFICACION (apicert/pangal, maullin) o PRODUCCION (api/rahue, palena)
     * @param certificadoPath     ruta del PKCS#12 del firmante
     * @param certificadoPassword clave del PKCS#12
     * @param rutFirmante         override/fallback del RUN del firmante (por defecto se extrae
     *                            del SERIALNUMBER del subject del certificado)
     * @param fchResol            fecha de resolucion de la caratula (AAAA-MM-DD); en certificacion
     *                            es la que muestra "Datos de la Empresa" del ambiente cert
     * @param nroResol            numero de resolucion (0 fijo en certificacion)
     * @param userAgent           User-Agent de las llamadas al SII (su WAF bloquea agentes de libreria)
     */
    public record Sii(String ambiente, String certificadoPath, String certificadoPassword,
                      String rutFirmante, String fchResol, int nroResol, String userAgent) {}
}
