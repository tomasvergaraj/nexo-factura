package cl.nexosoftware.factura.tributario;

/**
 * Contrato comun de los clientes de autenticacion del SII (REST de boleta y
 * SOAP clasico): entregar un token vigente PARA LA EMPRESA (la semilla se firma
 * con SU certificado y el token resultante es una sesion de ese certificado) y
 * descartarlo cuando el SII deja de aceptarlo, para que el transporte renueve
 * y reintente una vez.
 */
interface SiiTokenAuth {

    /** Token vigente de la empresa (cacheado por certificado o recien obtenido). */
    String token(Long empresaId);

    /**
     * Descarta el token cacheado tras un rechazo de autenticacion del SII —
     * pero SOLO si sigue siendo el que fallo: si otro hilo ya renovo, el token
     * nuevo no se pisa (evita una tercera ronda semilla+token innecesaria).
     */
    void invalidar(Long empresaId, String tokenFallido);
}
