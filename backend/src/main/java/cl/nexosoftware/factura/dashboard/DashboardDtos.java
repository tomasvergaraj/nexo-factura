package cl.nexosoftware.factura.dashboard;

import cl.nexosoftware.factura.documento.DocumentoDtos.DocumentoResumen;

import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record ResumenDashboard(
            long documentosMes,
            long montoEmitidoMes,
            long pendientesSii,
            long aceptados,
            long borradores,
            List<DocumentoResumen> recientes
    ) {}
}
