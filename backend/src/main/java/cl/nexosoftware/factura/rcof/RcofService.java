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
import java.util.Set;

/**
 * Construye el Reporte de Consumo de Folios (RCOF) de boletas (39/41) de un dia.
 *
 * El RCOF resume, por tipo de boleta, los folios utilizados (vigentes) y anulados
 * y los montos asociados (los anulados se cuentan pero no suman monto). Un folio
 * cuyo documento no es tributariamente valido —ANULADO o RECHAZADO por el SII—
 * va a anulados: ver {@link #FOLIO_SIN_DOCUMENTO_VALIDO}.
 *
 * <p><b>El RCOF se genera pero NO se firma ni se envia al SII.</b> Se difirio
 * cuando no habia certificado real; ese bloqueo desaparecio en el Sprint 7, que
 * trajo certificado y resolucion por empresa y el canal de boleta en produccion.
 * Lo que falta hoy es el canal de envio del ConsumoFolios y una secuencia de
 * envio de verdad — {@link #SEC_ENVIO_PLACEHOLDER} esta fijo en 1, y el SII
 * espera que incremente. Mientras tanto, un emisor de boletas queda con el
 * reporte a mano pero sin presentarlo.
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

    /**
     * Estados cuyo folio se consumio SIN producir un documento tributario valido.
     *
     * ANULADO es el caso obvio. RECHAZADO se le suma porque un DTE que el SII
     * rechazo no es una emision valida —el libro de ventas ya lo excluye— y antes
     * caia en "vigentes": el RCOF lo declaraba como folio utilizado Y sumaba sus
     * montos, o sea sobredeclaraba ventas que el propio SII habia rechazado, y
     * ademas contradecia al libro del mismo periodo.
     *
     * Va a anulados y no a "utilizado con monto cero" porque FoliosAnulados es
     * justamente la casilla que el RCOF tiene para un folio consumido sin
     * documento detras; un utilizado con monto cero se le parece bastante a un
     * error de cuadratura. Lo que no cambia es que el folio se reporta: el RCOF
     * existe para que ninguno del rango quede sin explicar.
     */
    private static final Set<EstadoDte> FOLIO_SIN_DOCUMENTO_VALIDO =
            Set.of(EstadoDte.ANULADO, EstadoDte.RECHAZADO);

    private RcofPorTipo resumirTipo(TipoDte tipo, List<DocumentoTributario> delDia) {
        List<DocumentoTributario> deTipo = delDia.stream()
                .filter(d -> d.getTipoDte() == tipo)
                .toList();
        List<DocumentoTributario> vigentes = deTipo.stream()
                .filter(d -> !FOLIO_SIN_DOCUMENTO_VALIDO.contains(d.getEstado()))
                .toList();
        List<DocumentoTributario> anulados = deTipo.stream()
                .filter(d -> FOLIO_SIN_DOCUMENTO_VALIDO.contains(d.getEstado()))
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
