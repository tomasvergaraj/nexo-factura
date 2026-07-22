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

    /**
     * Envia el libro IECV firmado (LibroCompraVenta) al SII por el canal clasico
     * y devuelve el TrackID; el estado del envio se consulta despues con
     * {@link #consultarEstado} (QueryEstUp, mismo servicio que los DTE).
     */
    String enviarLibro(EnvioLibroSii envio);

    /** Consulta el estado de un envio por su TrackID. */
    EstadoEnvio consultarEstado(ConsultaSii consulta);

    /**
     * Consulta el estado de un DOCUMENTO por folio (sin TrackID): la
     * reconciliacion previa al reenvio de un documento en contingencia, que
     * evita duplicar un envio cuya respuesta se perdio tras la recepcion.
     * Contrato: cualquier fallo en DETERMINAR el estado (transporte, respuesta
     * ilegible, 4xx inesperado) lanza
     * {@link cl.nexosoftware.factura.common.exception.SiiNoDisponibleException}
     * — jamas se responde un falso NO_RECIBIDO, que habilitaria el duplicado.
     */
    EstadoDocumento consultarDocumento(ConsultaDocumento consulta);

    /**
     * Datos de un envio. El XML es el DTE firmado tal como se almaceno; el
     * sobre (EnvioBOLETA/EnvioDTE) lo arma el gateway en cada envio.
     */
    record EnvioSii(String xmlFirmado, int tipoDte, long folio, String rutEmisor) {}

    /** El tipo decide el canal y el RUT emisor es parte de la URL de consulta. */
    record ConsultaSii(String trackId, int tipoDte, String rutEmisor) {}

    /** Libro IECV firmado; periodo (AAAA-MM) y operacion solo nombran el archivo. */
    record EnvioLibroSii(String xmlFirmado, String rutEmisor, String periodo, String tipoOperacion) {}

    /**
     * Identificacion del documento para la consulta por folio. El canal clasico
     * (getEstDte) exige ademas receptor, fecha de emision y monto total: el SII
     * los cruza contra lo registrado.
     */
    record ConsultaDocumento(int tipoDte, long folio, String rutEmisor,
                             String rutReceptor, java.time.LocalDate fechaEmision, long monto) {}

    enum EstadoEnvio {
        RECIBIDO,
        ACEPTADO,
        ACEPTADO_CON_REPARO,
        RECHAZADO
    }

    /** Resultado de la consulta por folio. Solo NO_RECIBIDO habilita un reenvio. */
    enum EstadoDocumento {
        /** El SII declara explicitamente que no recibio el documento. */
        NO_RECIBIDO,
        ACEPTADO,
        ACEPTADO_CON_REPARO,
        /** Registrado pero rechazado/no autorizado: corresponde emitir uno nuevo. */
        RECHAZADO,
        /** El SII conoce el folio pero aun lo procesa: no reenviar (duplicaria). */
        EN_PROCESO,
        /** El SII conoce el folio pero el estado no es concluyente: revisar en el portal. */
        DESCONOCIDO
    }
}
