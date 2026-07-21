package cl.nexosoftware.factura.tributario;

/**
 * Comunicacion con los servicios del SII para el envio de DTE y la consulta de
 * estado por TrackID. La implementacion real ({@code SiiGatewayProd}) rutea por
 * tipo de documento: boletas 39/41 via la API REST de boleta electronica
 * (apicert/pangal en certificacion, api/rahue en produccion) y facturas/notas
 * via el flujo clasico de DTE (maullin/palena). Esta interfaz aisla esa
 * integracion para permitir un stub en desarrollo.
 */
public interface SiiGateway {

    /** Envia el documento firmado al SII y devuelve el TrackID asignado. */
    String enviar(EnvioSii envio);

    /** Consulta el estado de un envio por su TrackID. */
    EstadoEnvio consultarEstado(ConsultaSii consulta);

    /**
     * Datos de un envio. El XML es el DTE firmado tal como se almaceno; el
     * sobre (EnvioBOLETA/EnvioDTE) lo arma el gateway en cada envio.
     */
    record EnvioSii(String xmlFirmado, int tipoDte, long folio, String rutEmisor) {}

    /** El tipo decide el canal y el RUT emisor es parte de la URL de consulta. */
    record ConsultaSii(String trackId, int tipoDte, String rutEmisor) {}

    enum EstadoEnvio {
        RECIBIDO,
        ACEPTADO,
        ACEPTADO_CON_REPARO,
        RECHAZADO
    }
}
