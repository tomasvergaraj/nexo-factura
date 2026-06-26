package cl.nexosoftware.factura.rcof;

import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.rcof.RcofDtos.*;
import cl.nexosoftware.factura.tributario.RcofXmlGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Construye el Reporte de Consumo de Folios (RCOF) de boletas (39/41) de un dia.
 *
 * El RCOF resume, por tipo de boleta, los folios utilizados (vigentes) y anulados
 * y los montos asociados (los anulados se cuentan pero no suman monto). Es un
 * reporte real y verificable; el envio firmado al SII queda fuera de alcance
 * (requiere certificado y secuencia de envio real), igual que la firma del DTE.
 */
@Service
@RequiredArgsConstructor
public class RcofService {

    /** Secuencia de envio: placeholder hasta tener historial de envios reales al SII. */
    private static final int SEC_ENVIO_PLACEHOLDER = 1;

    /** Tipos de boleta cubiertos por el RCOF, en orden de reporte (39 y luego 41). */
    private static final List<TipoDte> TIPOS_BOLETA =
            List.of(TipoDte.BOLETA_AFECTA, TipoDte.BOLETA_EXENTA);

    private final DocumentoRepository documentoRepository;
    private final EmpresaService empresaService;
    private final RcofXmlGenerator xmlGenerator;

    @Transactional(readOnly = true)
    public RcofResponse generar(Long empresaId, LocalDate fecha) {
        empresaService.buscar(empresaId);
        LocalDate dia = fecha != null ? fecha : LocalDate.now();

        List<DocumentoTributario> delDia =
                documentoRepository.findByEmpresaIdAndFechaEmisionAndFolioNotNull(empresaId, dia);

        List<RcofPorTipo> documentos = TIPOS_BOLETA.stream()
                .map(tipo -> resumirTipo(tipo, delDia))
                .toList();

        RcofTotales totales = totalizar(documentos);
        boolean sinMovimiento = documentos.stream().allMatch(d -> d.foliosEmitidos() == 0);

        return new RcofResponse(dia, SEC_ENVIO_PLACEHOLDER, documentos, totales, sinMovimiento);
    }

    @Transactional(readOnly = true)
    public String generarXml(Long empresaId, LocalDate fecha) {
        Empresa emisor = empresaService.buscar(empresaId);
        RcofResponse reporte = generar(empresaId, fecha);
        return xmlGenerator.generar(reporte, emisor);
    }

    private RcofPorTipo resumirTipo(TipoDte tipo, List<DocumentoTributario> delDia) {
        List<DocumentoTributario> deTipo = delDia.stream()
                .filter(d -> d.getTipoDte() == tipo)
                .toList();
        List<DocumentoTributario> vigentes = deTipo.stream()
                .filter(d -> d.getEstado() != EstadoDte.ANULADO)
                .toList();
        List<DocumentoTributario> anulados = deTipo.stream()
                .filter(d -> d.getEstado() == EstadoDte.ANULADO)
                .toList();

        long montoNeto = vigentes.stream().mapToLong(DocumentoTributario::getNeto).sum();
        long montoIva = vigentes.stream().mapToLong(DocumentoTributario::getIva).sum();
        long montoExento = vigentes.stream().mapToLong(DocumentoTributario::getExento).sum();
        long montoTotal = vigentes.stream().mapToLong(DocumentoTributario::getTotal).sum();

        return new RcofPorTipo(
                tipo.getCodigo(),
                vigentes.size() + anulados.size(),
                vigentes.size(),
                minFolio(vigentes),
                maxFolio(vigentes),
                anulados.size(),
                minFolio(anulados),
                maxFolio(anulados),
                montoNeto,
                montoIva,
                montoExento,
                montoTotal);
    }

    private RcofTotales totalizar(List<RcofPorTipo> documentos) {
        return new RcofTotales(
                documentos.stream().mapToLong(RcofPorTipo::foliosEmitidos).sum(),
                documentos.stream().mapToLong(RcofPorTipo::foliosUtilizados).sum(),
                documentos.stream().mapToLong(RcofPorTipo::foliosAnulados).sum(),
                documentos.stream().mapToLong(RcofPorTipo::montoNeto).sum(),
                documentos.stream().mapToLong(RcofPorTipo::montoIva).sum(),
                documentos.stream().mapToLong(RcofPorTipo::montoExento).sum(),
                documentos.stream().mapToLong(RcofPorTipo::montoTotal).sum());
    }

    private static Long minFolio(List<DocumentoTributario> docs) {
        return docs.stream().map(DocumentoTributario::getFolio).min(Comparator.naturalOrder()).orElse(null);
    }

    private static Long maxFolio(List<DocumentoTributario> docs) {
        return docs.stream().map(DocumentoTributario::getFolio).max(Comparator.naturalOrder()).orElse(null);
    }
}
