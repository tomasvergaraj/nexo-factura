package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.libro.LibroDtos.LibroEnvioResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.DteXmlValidator;
import cl.nexosoftware.factura.tributario.FirmaElectronica;
import cl.nexosoftware.factura.tributario.LibroXmlGenerator;
import cl.nexosoftware.factura.tributario.ResolucionResolver;
import cl.nexosoftware.factura.tributario.SiiGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * Firma y envio del libro IECV al SII: construye el libro agregado, genera el
 * XML oficial (LibroCV_v10), lo firma XMLDSig enveloped (Reference al ID del
 * EnvioLibro) con el certificado DE LA EMPRESA, lo valida contra el esquema y
 * lo sube por el canal clasico. La resolucion de la caratula sale de la
 * empresa (o del fallback de entorno) via {@link ResolucionResolver}.
 *
 * Para el SET DE PRUEBAS de certificacion el SII exige el libro como ESPECIAL
 * con el numero de atencion como FolioNotificacion; el envio mensual normal va
 * como MENSUAL sin folio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibroEnvioService {

    private final LibroService libroService;
    private final LibroXmlGenerator xmlGenerator;
    private final FirmaElectronica firma;
    private final DteXmlValidator validator;
    private final SiiGateway siiGateway;
    private final EmpresaService empresaService;
    private final ResolucionResolver resolucionResolver;
    // En dev no hay certificado y el RutEnvia cae al RUT del emisor.
    private final CertificadoResolver certificadoResolver;

    @Transactional(readOnly = true)
    public String xmlFirmado(Long empresaId, TipoOperacion operacion, YearMonth periodo,
                             Double fctProp, String tipoLibro, Long folioNotificacion) {
        return firmar(empresaId, operacion, periodo, fctProp, tipoLibro, folioNotificacion);
    }

    @Transactional(readOnly = true)
    public LibroEnvioResponse enviar(Long empresaId, TipoOperacion operacion, YearMonth periodo,
                                     Double fctProp, String tipoLibro, Long folioNotificacion) {
        Empresa emisor = empresaService.buscar(empresaId);
        String xml = firmar(empresaId, operacion, periodo, fctProp, tipoLibro, folioNotificacion);
        String trackId = siiGateway.enviarLibro(new SiiGateway.EnvioLibroSii(
                empresaId, xml, emisor.getRut(), periodo.toString(), operacion.name()));
        log.info("Libro IECV {} {} enviado al SII: TrackID={}", operacion, periodo, trackId);
        return new LibroEnvioResponse(periodo.toString(), operacion, trackId);
    }

    /** Estado del envio del libro por TrackID (QueryEstUp del canal clasico). */
    @Transactional(readOnly = true)
    public SiiGateway.EstadoEnvio estadoEnvio(Long empresaId, String trackId) {
        Empresa emisor = empresaService.buscar(empresaId);
        // Tipo 33: el libro viaja por el canal clasico y QueryEstUp es por TrackID.
        return siiGateway.consultarEstado(new SiiGateway.ConsultaSii(empresaId, trackId, 33, emisor.getRut()));
    }

    private String firmar(Long empresaId, TipoOperacion operacion, YearMonth periodo,
                          Double fctProp, String tipoLibro, Long folioNotificacion) {
        Empresa emisor = empresaService.buscar(empresaId);
        LibroResponse libro = libroService.construir(empresaId, operacion, periodo, fctProp);
        if (libro.sinMovimiento()) {
            throw new ReglaNegocioException(
                    "El libro " + operacion + " de " + periodo + " no tiene movimiento: nada que enviar");
        }
        ResolucionResolver.Resolucion resolucion = resolucionResolver.paraCaratula(empresaId);
        String rutEnvia = certificadoResolver.paraEmpresaSiExiste(empresaId)
                .map(CertificadoFirma::rutFirmante)
                .orElse(emisor.getRut());

        String xml = xmlGenerator.generar(libro, emisor, new LibroXmlGenerator.CaratulaLibro(
                rutEnvia, resolucion.fchResol(), resolucion.nroResol(),
                tipoLibro, folioNotificacion));
        String firmado = firma.firmarEnveloped(xml, LibroXmlGenerator.ID_ENVIO_LIBRO, empresaId);
        validator.validarLibro(firmado);
        return firmado;
    }
}
