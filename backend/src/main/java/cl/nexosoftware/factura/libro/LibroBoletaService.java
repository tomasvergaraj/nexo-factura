package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.DteXmlValidator;
import cl.nexosoftware.factura.tributario.FirmaElectronica;
import cl.nexosoftware.factura.tributario.LibroBoletaXmlGenerator;
import cl.nexosoftware.factura.tributario.LibroBoletaXmlGenerator.BoletaLibro;
import cl.nexosoftware.factura.tributario.ResolucionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/**
 * Libro de Boletas Electronicas del periodo: lo construye desde las boletas
 * emitidas, lo firma con el certificado de la empresa y lo valida contra
 * {@code LibroBOLETA_v10.xsd}.
 *
 * <p>Es el envio 4 del set de pruebas de certificacion de boletas. NO se sube al
 * SII —el set lo pide como adjunto del correo a SII_BE_Certificacion@sii.cl—,
 * asi que aca solo se genera el archivo. Y por eso exige
 * {@code folioNotificacion}: el esquema solo admite {@code TipoLibro=ESPECIAL},
 * el libro que responde a una notificacion del SII, con su numero de atencion.
 *
 * <p>No se confunde con {@link LibroEnvioService}, que es el IECV mensual
 * (LibroCV_v10) y si se envia: son documentos y esquemas distintos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibroBoletaService {

    private static final List<TipoDte> TIPOS_BOLETA =
            List.of(TipoDte.BOLETA_AFECTA, TipoDte.BOLETA_EXENTA);

    private final DocumentoRepository documentoRepository;
    private final LibroBoletaXmlGenerator xmlGenerator;
    private final FirmaElectronica firma;
    private final DteXmlValidator validator;
    private final EmpresaService empresaService;
    private final ResolucionResolver resolucionResolver;
    // En dev no hay certificado y el RutEnvia cae al RUT del emisor.
    private final CertificadoResolver certificadoResolver;

    /**
     * XML del libro de boletas del periodo, firmado y validado.
     *
     * @param folioNotificacion numero de la notificacion del SII (obligatorio en
     *                          el esquema: el libro de boletas solo existe como
     *                          ESPECIAL). En el set de pruebas es el numero de
     *                          atencion con que el SII lo solicita.
     */
    @Transactional(readOnly = true)
    public String xmlFirmado(Long empresaId, YearMonth periodo, long folioNotificacion) {
        Empresa emisor = empresaService.buscar(empresaId);
        if (folioNotificacion <= 0) {
            throw new ReglaNegocioException("El libro de boletas exige el folio de la notificacion "
                    + "del SII (numero de atencion): el esquema solo admite el libro ESPECIAL");
        }

        List<BoletaLibro> boletas = boletasDelPeriodo(empresaId, periodo);
        if (boletas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No hay boletas emitidas en " + periodo + ": el libro quedaria sin detalle");
        }

        ResolucionResolver.Resolucion resolucion = resolucionResolver.paraCaratula(empresaId);
        String rutEnvia = certificadoResolver.paraEmpresaSiExiste(empresaId)
                .map(CertificadoFirma::rutFirmante)
                .orElse(emisor.getRut());

        String xml = xmlGenerator.generar(periodo, boletas, emisor,
                new LibroBoletaXmlGenerator.CaratulaLibroBoleta(
                        rutEnvia, resolucion.fchResol(), resolucion.nroResol(), folioNotificacion));
        String firmado = firma.firmarEnveloped(
                xml, LibroBoletaXmlGenerator.ID_ENVIO_LIBRO_BOLETA, empresaId);
        validator.validarLibroBoleta(firmado);
        log.info("Libro de boletas {} firmado para la empresa {} ({} boletas, notificacion {})",
                periodo, empresaId, boletas.size(), folioNotificacion);
        return firmado;
    }

    /**
     * Boletas foliadas del periodo, ordenadas por tipo y folio (como se leen en
     * el libro). Un folio cuyo documento no es valido va marcado y sin montos:
     * la misma regla del RCOF, para que los dos documentos del mismo periodo no
     * se contradigan.
     */
    private List<BoletaLibro> boletasDelPeriodo(Long empresaId, YearMonth periodo) {
        return documentoRepository
                .findLibroByEmpresaIdAndFolioNotNullAndFechaEmisionBetween(
                        empresaId, periodo.atDay(1), periodo.atEndOfMonth())
                .stream()
                .filter(d -> TIPOS_BOLETA.contains(d.getTipoDte()))
                .sorted(java.util.Comparator
                        .comparingInt((DocumentoRepository.VentaLibroView d) -> d.getTipoDte().getCodigo())
                        .thenComparing(DocumentoRepository.VentaLibroView::getFolio))
                .map(d -> new BoletaLibro(
                        d.getTipoDte().getCodigo(),
                        d.getFolio(),
                        d.getFechaEmision(),
                        d.getEstado().folioSinDocumentoValido(),
                        d.getNeto(),
                        d.getIva(),
                        d.getExento(),
                        d.getTotal(),
                        d.getTasaIva()))
                .toList();
    }
}
