package cl.nexosoftware.factura.common.exception;

/**
 * Refresh token invalido, expirado, revocado o reutilizado. Hacia el cliente se
 * traduce siempre a 401 con un mensaje generico (anti-enumeracion); el motivo
 * especifico queda en el servidor.
 */
public class TokenInvalidoException extends RuntimeException {
    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }
}
