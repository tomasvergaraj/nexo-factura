package cl.nexosoftware.factura.tributario;

/**
 * Ambientes del SII y sus hosts. Confirmados contra el spec OpenAPI oficial
 * (www4c.sii.cl/bolcoreinternetui/api): la API de boleta usa apicert/api para
 * semilla, token y consultas, y pangal/rahue SOLO para el POST del envio
 * (pangal = certificacion, rahue = produccion). El flujo clasico de DTE
 * (facturas/notas) usa maullin/palena.
 */
public enum SiiAmbiente {

    CERTIFICACION(
            "https://apicert.sii.cl/recursos/v1",
            "https://pangal.sii.cl/recursos/v1",
            "https://maullin.sii.cl"),
    PRODUCCION(
            "https://api.sii.cl/recursos/v1",
            "https://rahue.sii.cl/recursos/v1",
            "https://palena.sii.cl");

    private final String apiBoleta;
    private final String envioBoleta;
    private final String hostDte;

    SiiAmbiente(String apiBoleta, String envioBoleta, String hostDte) {
        this.apiBoleta = apiBoleta;
        this.envioBoleta = envioBoleta;
        this.hostDte = hostDte;
    }

    /** Base de semilla/token/consultas de la API REST de boleta. */
    public String apiBoleta() { return apiBoleta; }

    /** Base EXCLUSIVA del POST de envio de boletas. */
    public String envioBoleta() { return envioBoleta; }

    /** Host del flujo clasico de DTE (semilla/token SOAP, DTEUpload, QueryEstUp). */
    public String hostDte() { return hostDte; }

    public static SiiAmbiente desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return CERTIFICACION;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.sii.ambiente invalido: '" + valor + "' (use CERTIFICACION o PRODUCCION)");
        }
    }
}
