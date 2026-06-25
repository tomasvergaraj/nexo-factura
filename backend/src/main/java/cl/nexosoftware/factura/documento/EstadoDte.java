package cl.nexosoftware.factura.documento;

import java.util.Set;

/**
 * Ciclo de vida de un DTE.
 * <pre>
 *   BORRADOR -> FIRMADO -> ENVIADO -> (ACEPTADO | RECHAZADO | REPARO)
 *   ACEPTADO -> ANULADO (via nota de credito)
 * </pre>
 */
public enum EstadoDte {
    BORRADOR,
    FIRMADO,
    ENVIADO,
    ACEPTADO,
    RECHAZADO,
    REPARO,
    ANULADO;

    private static final java.util.Map<EstadoDte, Set<EstadoDte>> TRANSICIONES = java.util.Map.of(
            BORRADOR, Set.of(FIRMADO),
            FIRMADO, Set.of(ENVIADO, BORRADOR),
            ENVIADO, Set.of(ACEPTADO, RECHAZADO, REPARO),
            ACEPTADO, Set.of(ANULADO),
            RECHAZADO, Set.of(BORRADOR),
            REPARO, Set.of(ACEPTADO, RECHAZADO),
            ANULADO, Set.of()
    );

    public boolean puedeTransicionarA(EstadoDte destino) {
        return TRANSICIONES.getOrDefault(this, Set.of()).contains(destino);
    }
}
