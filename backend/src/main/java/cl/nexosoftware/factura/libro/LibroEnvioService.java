package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.libro.LibroDtos.EnvioLibroResponse;
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
import java.util.List;

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
    private final EnvioLibroRepository envioLibroRepository;

    @Transactional(readOnly = true)
    public String xmlFirmado(Long empresaId, TipoOperacion operacion, YearMonth periodo,
                             Double fctProp, String tipoLibro, Long folioNotificacion) {
        return firmar(empresaId, operacion, periodo, fctProp, tipoLibro, folioNotificacion).xml();
    }

    @Transactional
    public LibroEnvioResponse enviar(Long empresaId, TipoOperacion operacion, YearMonth periodo,
                                     Double fctProp, String tipoLibro, Long folioNotificacion) {
        Empresa emisor = empresaService.buscar(empresaId);
        LibroFirmado firmado = firmar(empresaId, operacion, periodo, fctProp, tipoLibro, folioNotificacion);
        String trackId = siiGateway.enviarLibro(new SiiGateway.EnvioLibroSii(
                empresaId, firmado.xml(), emisor.getRut(), periodo.toString(), operacion.name()));
        log.info("Libro IECV {} {} enviado al SII: TrackID={}", operacion, periodo, trackId);
        // El estado real llega despues por QueryEstUp; aqui solo queda el TrackID.
        envioLibroRepository.save(EnvioLibro.builder()
                .empresaId(empresaId)
                .periodo(periodo.toString())
                .tipoOperacion(operacion)
                .trackId(trackId)
                .tipoLibro(tipoLibro)
                .folioNotificacion(folioNotificacion)
                // Se guarda el factor que quedo EN EL XML, no el que hoy tiene la
                // empresa: ese es editable y el envio admite override, asi que sin
                // esto no habria como saber despues que se declaro en este envio.
                .fctProp(firmado.fctPropDeclarado())
                .build());
        return new LibroEnvioResponse(periodo.toString(), operacion, trackId);
    }

    /** Envios ya registrados de un libro (periodo + operacion), del mas nuevo al mas viejo. */
    @Transactional(readOnly = true)
    public List<EnvioLibroResponse> listar(Long empresaId, TipoOperacion operacion, YearMonth periodo) {
        return envioLibroRepository
                .findByEmpresaIdAndPeriodoAndTipoOperacionOrderByTmstEnvioDesc(
                        empresaId, periodo.toString(), operacion)
                .stream()
                .map(LibroEnvioService::aResponse)
                .toList();
    }

    /**
     * Envios que todavia esperan resolucion del SII (nunca consultados o en
     * proceso). Es la lista de trabajo de la consulta automatica de estados: el
     * job la recorre y llama a {@link #estadoEnvio} por cada uno, de modo que
     * cada consulta corra en su propia transaccion y un TrackID que falle no
     * arrastre a los demas.
     */
    @Transactional(readOnly = true)
    public List<EnvioLibroResponse> pendientesDeResolucion(Long empresaId) {
        return envioLibroRepository
                .findPendientesDeResolucion(empresaId, SiiGateway.EstadoEnvio.RECIBIDO.name())
                .stream()
                .map(LibroEnvioService::aResponse)
                .toList();
    }

    /**
     * Estado del envio del libro por TrackID (QueryEstUp del canal clasico).
     * Persiste el estado consultado en el registro del envio para que la UI lo
     * muestre sin volver a llamar al SII.
     */
    @Transactional
    public SiiGateway.EstadoEnvio estadoEnvio(Long empresaId, String trackId) {
        Empresa emisor = empresaService.buscar(empresaId);
        // Tipo 33: el libro viaja por el canal clasico y QueryEstUp es por TrackID.
        SiiGateway.EstadoEnvio estado = siiGateway.consultarEstado(
                new SiiGateway.ConsultaSii(empresaId, trackId, 33, emisor.getRut()));
        envioLibroRepository.findFirstByEmpresaIdAndTrackId(empresaId, trackId)
                .ifPresent(envio -> envio.setEstado(estado.name()));
        return estado;
    }

    private static EnvioLibroResponse aResponse(EnvioLibro e) {
        return new EnvioLibroResponse(e.getId(), e.getPeriodo(), e.getTipoOperacion(),
                e.getTrackId(), e.getEstado(), e.getTipoLibro(), e.getFolioNotificacion(),
                e.getFctProp(), e.getTmstEnvio());
    }

    /** XML firmado y el factor que quedo DECLARADO en el (null si no hay uso comun). */
    private record LibroFirmado(String xml, Double fctPropDeclarado) {}

    private LibroFirmado firmar(Long empresaId, TipoOperacion operacion, YearMonth periodo,
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
        return new LibroFirmado(firmado, libro.tieneIvaUsoComun() ? libro.fctProp() : null);
    }
}
