package cl.nexosoftware.factura.libro;

import java.time.LocalDate;
import java.util.List;

/**
 * DTOs de los libros de compra y venta (IECV). Los montos son enteros CLP.
 *
 * Reglas del libro de VENTAS:
 * - Entran los documentos foliados del periodo, excepto los RECHAZADOS (un DTE
 *   rechazado por el SII no es una emision valida).
 * - Un documento ANULADO por nota de credito va CON sus montos: la anulacion
 *   tributaria la materializa la propia NC (el SII computa el signo por tipo),
 *   no una marca en el libro. La marca "A" del IECV es para folios inutilizados,
 *   que este sistema no produce.
 * - Las boletas (39/41) van solo RESUMIDAS por tipo, sin detalle por documento,
 *   como en el IECV real; el resto de los tipos va detallado.
 * - Las notas de credito (60/61) van con sus montos positivos: el tipo de
 *   documento determina el signo con que el SII las computa.
 *
 * Reglas del libro de COMPRAS:
 * - {@code iva} es el IVA con derecho a credito. El IVA de uso comun va aparte
 *   (credito proporcional segun {@code fctProp}) y el no recuperable va con su
 *   codigo del catalogo del SII (4 = entrega gratuita).
 */
public final class LibroDtos {

    private LibroDtos() {}

    public enum TipoOperacion { VENTA, COMPRA }

    public record LibroResponse(
            String periodo,                    // YYYY-MM
            TipoOperacion tipoOperacion,
            List<LibroResumenTipo> resumen,    // un registro por tipo con movimiento
            List<LibroDetalleDoc> detalle,     // por documento (ventas: sin boletas)
            LibroTotales totales,
            boolean sinMovimiento,
            // Factor de proporcionalidad del IVA uso comun del periodo (compras);
            // null si no se informo.
            Double fctProp
    ) {}

    public record LibroResumenTipo(
            int tipoDocumento,
            long documentos,          // vigentes (sin anulados)
            long anulados,
            long neto,
            long exento,
            long iva,                 // ventas: IVA debito; compras: IVA recuperable
            long otrosImpuestos,      // adicionales (+); solo ventas
            long ivaRetenido,         // retencion cambio de sujeto; ventas y compras (46)
            long total,
            long ivaUsoComun,               // compras: IVA de uso comun del tipo
            long operacionesIvaUsoComun,    // compras: numero de operaciones uso comun
            long creditoIvaUsoComun,        // compras: round(ivaUsoComun * fctProp); 0 sin factor
            List<IvaNoRecResumen> ivaNoRec  // compras: IVA no recuperable por codigo
    ) {}

    /** IVA no recuperable agregado por codigo del SII (compras). */
    public record IvaNoRecResumen(int codigo, long operaciones, long monto) {}

    public record LibroDetalleDoc(
            int tipoDocumento,
            long folio,
            LocalDate fecha,
            String rutContraparte,    // ventas: receptor; compras: proveedor
            String razonSocial,
            long neto,
            long exento,
            long iva,                 // ventas: IVA debito; compras: IVA recuperable
            long otrosImpuestos,
            long ivaRetenido,
            long total,
            boolean anulado,          // reservado para folios inutilizados (hoy siempre false)
            long ivaUsoComun,         // compras: IVA de uso comun del documento
            long ivaNoRec,            // compras: IVA no recuperable del documento
            Integer codIvaNoRec       // compras: codigo del IVA no recuperable
    ) {}

    public record LibroTotales(
            long documentos,
            long anulados,
            long neto,
            long exento,
            long iva,
            long otrosImpuestos,
            long ivaRetenido,
            long total
    ) {}

    /** Resultado del envio del libro firmado al SII. */
    public record LibroEnvioResponse(
            String periodo,
            TipoOperacion tipoOperacion,
            String trackId
    ) {}
}
