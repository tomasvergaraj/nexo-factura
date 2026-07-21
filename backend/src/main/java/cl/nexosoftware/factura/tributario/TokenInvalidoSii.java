package cl.nexosoftware.factura.tributario;

/**
 * Senal interna de los transportes: el SII no reconocio el token (401 o "NO
 * ESTA AUTENTICADO" en boleta; STATUS 5 del upload o estados 001-003 de la
 * consulta en el canal clasico). Gatilla la renovacion del token + 1 reintento.
 */
final class TokenInvalidoSii extends RuntimeException {}
