package cl.nexosoftware.factura.intercambio;

import java.util.List;

/**
 * DTOs del endpoint de intercambio de informacion. La respuesta entrega los tres
 * artefactos que el portal de postulantes pide subir (Respuesta de Intercambio,
 * Recibo de Mercaderias y Resultado Aprobacion Comercial), ya firmados, mas un
 * resumen legible de la decision tomada por cada DTE.
 */
public final class IntercambioDtos {

    private IntercambioDtos() {}

    /**
     * Los tres XML firmados. {@code reciboMercaderias} y {@code resultadoComercial}
     * son null si el sobre no traia ningun DTE aceptado (no hay nada que acusar
     * ni aprobar comercialmente).
     */
    public record RespuestaIntercambioResponse(
            String respuestaIntercambio,
            String reciboMercaderias,
            String resultadoComercial,
            List<DecisionDte> decisiones) {}

    /** Decision de recepcion por cada DTE del sobre. */
    public record DecisionDte(int tipoDte, long folio, String rutReceptor,
                              boolean aceptado, int estadoRecepDte, String glosa) {}
}
