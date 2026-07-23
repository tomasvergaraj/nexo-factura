package cl.nexosoftware.factura.tributario;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos extraidos de un sobre {@code EnvioDTE} AJENO recibido por intercambio
 * (Ley 19.983 / etapa de intercambio de la certificacion). Es la vista minima
 * que necesitan los acuses de respuesta: la caratula del envio, el atributo ID
 * del SetDTE y su DigestValue (para el {@code RecepcionEnvio}), y el resumen de
 * cada DTE embebido (para decidir aceptacion/rechazo por RUT receptor).
 *
 * @param rutEmisor    RutEmisor de la Caratula del sobre (emisor de los DTE)
 * @param rutReceptor  RutReceptor de la Caratula del sobre (a quien va dirigido)
 * @param envioDteId   valor del atributo ID del tag SetDTE ({@code EnvioDTEID})
 * @param digest       DigestValue base64 de la firma del SetDTE (o null si no se hallo)
 * @param documentos   resumen de cada DTE del sobre, en orden de aparicion
 */
public record SobreRecibido(String rutEmisor, String rutReceptor, String envioDteId,
                            String digest, List<DteRecibido> documentos) {

    /**
     * Resumen de un DTE embebido en el sobre, leido del Encabezado (no del TED).
     */
    public record DteRecibido(int tipoDte, long folio, LocalDate fchEmis,
                              String rutEmisor, String rutReceptor, long mntTotal) {
    }
}
