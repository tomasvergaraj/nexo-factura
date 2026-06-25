package cl.nexosoftware.factura.tributario;

/**
 * Comunicacion con los servicios del SII para el envio de DTE.
 *
 * El flujo real implica: obtener semilla, firmar la semilla y solicitar token,
 * armar el sobre EnvioDTE, hacer el upload al endpoint del ambiente
 * (certificacion/produccion) y luego consultar el estado por TrackID. Esta
 * interfaz aisla esa integracion para permitir un stub en desarrollo.
 */
public interface SiiGateway {

    /** Envia el sobre con el DTE y devuelve el TrackID asignado por el SII. */
    String enviar(String xmlDteFirmado);

    /** Consulta el estado de un envio por su TrackID. */
    EstadoEnvio consultarEstado(String trackId);

    enum EstadoEnvio {
        RECIBIDO,
        ACEPTADO,
        ACEPTADO_CON_REPARO,
        RECHAZADO
    }
}
