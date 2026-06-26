package cl.nexosoftware.factura.rcof;

import java.time.LocalDate;
import java.util.List;

/**
 * DTOs del RCOF (Reporte de Consumo de Folios) de boletas. Los montos son enteros
 * en pesos chilenos (CLP). Los folios anulados se cuentan pero no suman montos.
 */
public final class RcofDtos {

    private RcofDtos() {}

    public record RcofResponse(
            LocalDate fecha,
            int secEnvio,                  // secuencia de envio (placeholder = 1 sin SII real)
            List<RcofPorTipo> documentos,  // siempre incluye 39 y 41 (con ceros si no hubo)
            RcofTotales totales,
            boolean sinMovimiento
    ) {}

    public record RcofPorTipo(
            int tipoDocumento,             // 39 | 41
            long foliosEmitidos,           // utilizados + anulados
            long foliosUtilizados,         // estado != ANULADO
            Long folioInicial,             // null si foliosUtilizados == 0
            Long folioFinal,               // null si foliosUtilizados == 0
            long foliosAnulados,
            Long folioAnuladoInicial,      // null si foliosAnulados == 0
            Long folioAnuladoFinal,        // null si foliosAnulados == 0
            long montoNeto,
            long montoIva,
            long montoExento,
            long montoTotal
    ) {}

    public record RcofTotales(
            long foliosEmitidos,
            long foliosUtilizados,
            long foliosAnulados,
            long montoNeto,
            long montoIva,
            long montoExento,
            long montoTotal
    ) {}
}
