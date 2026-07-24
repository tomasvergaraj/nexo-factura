package cl.nexosoftware.factura.dashboard;

import cl.nexosoftware.factura.documento.DocumentoDtos.DocumentoResumen;

import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record ResumenDashboard(
            long documentosMes,
            long montoEmitidoMes,
            long pendientesSii,
            long aceptados,
            long borradores,
            /** Documentos cuyo envio al SII fallo y esperan reintento. */
            long enContingencia,
            List<DocumentoResumen> recientes,
            /** Monto emitido por dia en los ultimos 7 dias (para el grafico). */
            List<PuntoSerie> serieEmision
    ) {}

    /** Un dia de la serie de emision: fecha y monto emitido ese dia. */
    public record PuntoSerie(LocalDate fecha, long monto) {}
}
