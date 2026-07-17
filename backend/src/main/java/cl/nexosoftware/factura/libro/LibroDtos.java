package cl.nexosoftware.factura.libro;

import java.time.LocalDate;
import java.util.List;

/**
 * DTOs de los libros de compra y venta (IECV). Los montos son enteros CLP.
 *
 * Reglas del libro de VENTAS:
 * - Entran los documentos foliados del periodo, excepto los RECHAZADOS (un DTE
 *   rechazado por el SII no es una emision valida).
 * - Los ANULADOS aparecen marcados pero NO suman montos (igual que en el RCOF).
 * - Las boletas (39/41) van solo RESUMIDAS por tipo, sin detalle por documento,
 *   como en el IECV real; el resto de los tipos va detallado.
 * - Las notas de credito (61) van con sus montos positivos: el tipo de documento
 *   determina el signo con que el SII las computa.
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
            boolean sinMovimiento
    ) {}

    public record LibroResumenTipo(
            int tipoDocumento,
            long documentos,          // vigentes (sin anulados)
            long anulados,
            long neto,
            long exento,
            long iva,
            long otrosImpuestos,      // adicionales (+); solo ventas
            long ivaRetenido,         // retencion cambio de sujeto (-); solo ventas
            long total
    ) {}

    public record LibroDetalleDoc(
            int tipoDocumento,
            long folio,
            LocalDate fecha,
            String rutContraparte,    // ventas: receptor; compras: proveedor
            String razonSocial,
            long neto,
            long exento,
            long iva,
            long otrosImpuestos,
            long ivaRetenido,
            long total,
            boolean anulado           // marcado y con montos excluidos de los totales
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
}
