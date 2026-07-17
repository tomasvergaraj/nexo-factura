package cl.nexosoftware.factura.dashboard;

import cl.nexosoftware.factura.dashboard.DashboardDtos.ResumenDashboard;
import cl.nexosoftware.factura.documento.DocumentoMapper;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.EstadoDte;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DocumentoRepository documentoRepository;

    @Transactional(readOnly = true)
    public ResumenDashboard resumen(Long empresaId) {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);

        long documentosMes = documentoRepository
                .countByEmpresaIdAndFechaEmisionGreaterThanEqual(empresaId, inicioMes);
        long montoMes = documentoRepository.sumTotalEmitidoDesde(empresaId, inicioMes);
        long pendientes = documentoRepository.countByEmpresaIdAndEstado(empresaId, EstadoDte.ENVIADO);
        long aceptados = documentoRepository.countByEmpresaIdAndEstado(empresaId, EstadoDte.ACEPTADO);
        long borradores = documentoRepository.countByEmpresaIdAndEstado(empresaId, EstadoDte.BORRADOR);
        long enContingencia = documentoRepository.countByEmpresaIdAndEstado(empresaId, EstadoDte.EN_CONTINGENCIA);

        var recientes = documentoRepository
                .findByEmpresaIdOrderByCreadoEnDesc(empresaId, PageRequest.of(0, 8))
                .map(DocumentoMapper::toResumen)
                .getContent();

        return new ResumenDashboard(documentosMes, montoMes, pendientes, aceptados, borradores,
                enContingencia, List.copyOf(recientes));
    }
}
