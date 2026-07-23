package cl.nexosoftware.factura.tributario;

/**
 * Un DTE del sobre recibido junto con la decision de recepcion que le aplico el
 * receptor (nosotros). Es la entrada comun a los tres acuses: la Respuesta de
 * Intercambio informa el {@code estadoRecepDte} de TODOS; el Resultado Comercial
 * y el Recibo de Mercaderias solo consideran los {@code aceptado == true}.
 *
 * @param documento     resumen del DTE tal como venia en el sobre
 * @param aceptado      true si el DTE es para nosotros y se acepta
 * @param estadoRecepDte codigo EstadoRecepDTE del XSD (0 = OK; 3 = RUT receptor)
 */
public record DteEvaluado(SobreRecibido.DteRecibido documento, boolean aceptado, int estadoRecepDte) {

    public long folio() {
        return documento.folio();
    }
}
