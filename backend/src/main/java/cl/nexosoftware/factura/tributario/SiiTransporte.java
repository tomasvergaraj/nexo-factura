package cl.nexosoftware.factura.tributario;

/**
 * Canal de comunicacion con el SII para una familia de documentos. El SII opera
 * dos canales con hosts, autenticacion y sobres DISTINTOS e independientes:
 * la API REST de boleta (39/41) y el flujo clasico de DTE (33/34/56/61).
 * {@link SiiGatewayProd} rutea cada operacion al transporte que soporta el tipo.
 */
public interface SiiTransporte {

    boolean soporta(int tipoDte);

    /** Arma el sobre, lo envia y devuelve el TrackID del SII. */
    String enviar(SiiGateway.EnvioSii envio);

    /** Envia el libro IECV firmado. Solo el canal clasico lo soporta. */
    default String enviarLibro(SiiGateway.EnvioLibroSii envio) {
        throw new UnsupportedOperationException(
                "Este canal del SII no soporta el envio de libros IECV");
    }

    SiiGateway.EstadoEnvio consultarEstado(SiiGateway.ConsultaSii consulta);

    /** Estado del documento por folio (reconciliacion; ver {@link SiiGateway#consultarDocumento}). */
    SiiGateway.EstadoDocumento consultarDocumento(SiiGateway.ConsultaDocumento consulta);
}
