package cl.nexosoftware.factura.documento;

import java.util.Set;

/**
 * Ciclo de vida de un DTE.
 * <pre>
 *   BORRADOR -> FIRMADO -> ENVIADO -> (ACEPTADO | RECHAZADO | REPARO)
 *   FIRMADO -> EN_CONTINGENCIA        (el SII no estaba disponible al enviar)
 *   EN_CONTINGENCIA -> ENVIADO        (reintento de envio exitoso)
 *   EN_CONTINGENCIA -> RECHAZADO      (el SII rechazo el envio en el reintento)
 *   RECHAZADO -> ENVIADO              (reenvio del mismo XML firmado)
 *   ACEPTADO -> ANULADO               (via nota de credito)
 * </pre>
 *
 * Un RECHAZADO no vuelve a BORRADOR: el contenido tributario del DTE es
 * inmutable y su folio ya fue consumido, asi que la unica correccion posible
 * es reenviar el mismo XML (errores transitorios del envio) o emitir un
 * documento nuevo que lo reemplace. Tampoco pasa a EN_CONTINGENCIA: un rechazo
 * del SII es una decision de fondo, no una caida transitoria, y el documento
 * no debe entrar a la cola de reintento automatico ni volver a los libros; si
 * su reenvio falla, permanece RECHAZADO con el error en la traza de envio.
 * El camino inverso EN_CONTINGENCIA -> RECHAZADO si existe: si durante el
 * reintento el SII responde con un rechazo de negocio (no una caida), el
 * documento sale de la cola con el motivo — dejarlo en contingencia lo haria
 * golpear al SII indefinidamente con un envio que sera rechazado igual.
 */
public enum EstadoDte {
    BORRADOR,
    FIRMADO,
    EN_CONTINGENCIA,
    ENVIADO,
    ACEPTADO,
    RECHAZADO,
    REPARO,
    ANULADO;

    private static final java.util.Map<EstadoDte, Set<EstadoDte>> TRANSICIONES = java.util.Map.of(
            BORRADOR, Set.of(FIRMADO),
            FIRMADO, Set.of(ENVIADO, BORRADOR, EN_CONTINGENCIA),
            EN_CONTINGENCIA, Set.of(ENVIADO, RECHAZADO),
            ENVIADO, Set.of(ACEPTADO, RECHAZADO, REPARO),
            ACEPTADO, Set.of(ANULADO),
            RECHAZADO, Set.of(ENVIADO),
            REPARO, Set.of(ACEPTADO, RECHAZADO),
            ANULADO, Set.of()
    );

    public boolean puedeTransicionarA(EstadoDte destino) {
        return TRANSICIONES.getOrDefault(this, Set.of()).contains(destino);
    }
}
