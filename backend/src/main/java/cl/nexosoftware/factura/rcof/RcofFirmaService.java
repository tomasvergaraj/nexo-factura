package cl.nexosoftware.factura.rcof;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaService;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofResponse;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.DteXmlValidator;
import cl.nexosoftware.factura.tributario.FirmaElectronica;
import cl.nexosoftware.factura.tributario.RcofXmlGenerator;
import cl.nexosoftware.factura.tributario.ResolucionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Firma el ConsumoFolios (RCOF) del dia: construye el reporte, genera el XML
 * oficial, lo firma XMLDSig enveloped (Reference al ID del
 * DocumentoConsumoFolios) con el certificado DE LA EMPRESA y lo valida contra
 * {@code ConsumoFolio_v10.xsd}, que exige el nodo Signature.
 *
 * <p><b>No lo envia al SII, y eso es deliberado.</b> La Res. Ex. SII N°53 de
 * 2022 elimino la obligacion de remitir el consumo de folios —hoy Resumen de
 * Ventas Diarias— desde el 01/08/2022: el registro de ventas del contribuyente
 * se alimenta del XML de las boletas. Lo que sigue pidiendo el archivo es la
 * CERTIFICACION de boletas, que se tramita por correo a
 * {@code SII_BE_Certificacion@sii.cl} adjuntando los XML firmados. Por eso el
 * resultado es un archivo para descargar y no un TrackID. Ver ROADMAP §15.
 *
 * <p>Espeja a {@code LibroEnvioService} en todo lo demas (resolucion de
 * caratula, RutEnvia del firmante, validacion post-firma).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RcofFirmaService {

    private final RcofService rcofService;
    private final RcofXmlGenerator xmlGenerator;
    private final FirmaElectronica firma;
    private final DteXmlValidator validator;
    private final EmpresaService empresaService;
    private final ResolucionResolver resolucionResolver;
    // En dev no hay certificado y el RutEnvia cae al RUT del emisor.
    private final CertificadoResolver certificadoResolver;
    private final RcofFirmadoRepository firmadoRepository;

    /**
     * XML del RCOF firmado y validado, listo para adjuntar.
     *
     * @param fecha    dia reportado; hoy si viene null
     * @param secEnvio secuencia a declarar; si viene null se usa la siguiente del
     *                 dia (1 la primera vez). El override existe para rehacer un
     *                 archivo que se genero pero nunca se presento, sin que la
     *                 secuencia salte y el SII lo lea como una correccion.
     */
    @Transactional
    public String xmlFirmado(Long empresaId, LocalDate fecha, Integer secEnvio) {
        Empresa emisor = empresaService.buscar(empresaId);
        RcofResponse reporte = rcofService.generar(empresaId, fecha);
        if (reporte.sinMovimiento()) {
            throw new ReglaNegocioException("El " + reporte.fecha() + " no se emitieron boletas: "
                    + "no hay consumo de folios que declarar");
        }

        int secuencia = secEnvio != null ? secEnvio : reporte.secEnvio();
        if (secuencia < 1 || secuencia > RcofService.SEC_ENVIO_MAX) {
            throw new ReglaNegocioException("SecEnvio fuera de rango (1-"
                    + RcofService.SEC_ENVIO_MAX + "): " + secuencia);
        }

        ResolucionResolver.Resolucion resolucion = resolucionResolver.paraCaratula(empresaId);
        String rutEnvia = certificadoResolver.paraEmpresaSiExiste(empresaId)
                .map(CertificadoFirma::rutFirmante)
                .orElse(emisor.getRut());

        String xml = xmlGenerator.generar(reporte, emisor, new RcofXmlGenerator.CaratulaRcof(
                rutEnvia, resolucion.fchResol(), resolucion.nroResol(), secuencia));
        String firmado = firma.firmarEnveloped(xml, RcofXmlGenerator.ID_CONSUMO_FOLIOS, empresaId);
        validator.validarConsumoFolios(firmado);

        // Se registra DESPUES de validar: un XML que no cumple el esquema no
        // existe como archivo, y no debe consumir una secuencia.
        firmadoRepository.save(RcofFirmado.builder()
                .empresaId(empresaId)
                .fecha(reporte.fecha())
                .secEnvio(secuencia)
                .build());
        log.info("RCOF de {} firmado para la empresa {} (SecEnvio={})",
                reporte.fecha(), empresaId, secuencia);
        return firmado;
    }
}
