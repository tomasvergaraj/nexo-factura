package cl.nexosoftware.factura.folio;

/**
 * Resultado de la asignacion de un folio: el numero y el CAF del que salio.
 * El CAF es necesario al emitir porque el timbre (TED) debe llevar embebido el
 * bloque {@code <CAF>} exacto que autoriza ese folio y firmarse con su clave.
 */
public record FolioAsignado(long folio, Caf caf) {}
