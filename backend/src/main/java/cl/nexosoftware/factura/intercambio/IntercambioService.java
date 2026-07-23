package cl.nexosoftware.factura.intercambio;

import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.intercambio.IntercambioDtos.DecisionDte;
import cl.nexosoftware.factura.intercambio.IntercambioDtos.RespuestaIntercambioResponse;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.Contacto;
import cl.nexosoftware.factura.tributario.DteEvaluado;
import cl.nexosoftware.factura.tributario.DteXmlValidator;
import cl.nexosoftware.factura.tributario.EnvioRecibosGenerator;
import cl.nexosoftware.factura.tributario.EnvioRecibosGenerator.ReciboItem;
import cl.nexosoftware.factura.tributario.LectorSobreDte;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator.AcuseEnvio;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator.Cabecera;
import cl.nexosoftware.factura.tributario.SobreRecibido;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta la respuesta a un sobre {@code EnvioDTE} recibido por intercambio
 * (Ley 19.983 / etapa de intercambio de la certificacion): valida el sobre,
 * decide aceptacion/rechazo por RUT receptor y genera los tres artefactos
 * firmados que el portal de postulantes pide subir.
 *
 * <p><b>Estado de recepcion del sobre</b> ({@code EstadoRecepEnv}): 0 si la
 * caratula viene dirigida a nuestro RUT, 3 (RUT Receptor No Corresponde) si no.
 * <b>Estado por DTE</b> ({@code EstadoRecepDTE}): 0 si el {@code RUTRecep} del
 * documento es el nuestro, 3 si no — esta es la trampa del set de intercambio
 * (un DTE dirigido a otro RUT viene mezclado en el sobre). Solo los DTE
 * aceptados generan Recibo de Mercaderias y Resultado Comercial.
 *
 * <p>Alcance actual: se valida el sobre contra {@code EnvioDTE_v10.xsd} y su
 * estructura; la verificacion criptografica de la firma del emisor y del CAF
 * queda pendiente (el set de certificacion es genuino, asi que la firma se da
 * por conforme). Por eso {@code EstadoRecepEnv} nunca vale 2 (Error de Firma).
 */
@Service
@Slf4j
public class IntercambioService {

    private static final String RECINTO_POR_DEFECTO = "Casa Matriz";

    private final LectorSobreDte lector;
    private final DteXmlValidator validator;
    private final RespuestaDteGenerator respuestaGen;
    private final EnvioRecibosGenerator recibosGen;
    private final EmpresaRepository empresaRepo;
    private final CertificadoResolver certificadoResolver;
    private final Clock clock;

    // @Autowired explicito: hay un segundo constructor (con Clock, para tests).
    @Autowired
    public IntercambioService(LectorSobreDte lector, DteXmlValidator validator,
                              RespuestaDteGenerator respuestaGen, EnvioRecibosGenerator recibosGen,
                              EmpresaRepository empresaRepo, CertificadoResolver certificadoResolver) {
        this(lector, validator, respuestaGen, recibosGen, empresaRepo, certificadoResolver,
                Clock.system(ZoneId.of("America/Santiago")));
    }

    IntercambioService(LectorSobreDte lector, DteXmlValidator validator,
                       RespuestaDteGenerator respuestaGen, EnvioRecibosGenerator recibosGen,
                       EmpresaRepository empresaRepo, CertificadoResolver certificadoResolver, Clock clock) {
        this.lector = lector;
        this.validator = validator;
        this.respuestaGen = respuestaGen;
        this.recibosGen = recibosGen;
        this.empresaRepo = empresaRepo;
        this.certificadoResolver = certificadoResolver;
        this.clock = clock;
    }

    /**
     * Genera los tres acuses para {@code xmlEnvioDte} (sobre recibido) en nombre
     * de la empresa {@code empresaId}.
     *
     * @param nombreArchivo nombre del archivo recibido, para el {@code NmbEnvio}
     *                      del RecepcionEnvio (default si viene vacio)
     */
    public RespuestaIntercambioResponse responder(Long empresaId, String xmlEnvioDte, String nombreArchivo) {
        Empresa empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Empresa", empresaId));
        String rutEmpresa = normalizarRut(empresa.getRut());

        // 1) El sobre debe cumplir el esquema oficial EnvioDTE_v10 antes de acusarlo.
        validator.validarEnvioDte(xmlEnvioDte);
        SobreRecibido sobre = lector.leer(xmlEnvioDte);

        // 2) Estado del sobre: la caratula debe venir dirigida a nuestro RUT.
        boolean sobreParaNosotros = rutEmpresa.equals(normalizarRut(sobre.rutReceptor()));
        int estadoRecepEnv = sobreParaNosotros ? 0 : 3;

        // 3) Decision por DTE: aceptar solo los que son para nuestro RUT.
        List<DteEvaluado> evaluados = new ArrayList<>();
        for (SobreRecibido.DteRecibido d : sobre.documentos()) {
            boolean paraNosotros = rutEmpresa.equals(normalizarRut(d.rutReceptor()));
            evaluados.add(new DteEvaluado(d, paraNosotros, paraNosotros ? 0 : 3));
        }
        List<DteEvaluado> aceptados = evaluados.stream().filter(DteEvaluado::aceptado).toList();

        Contacto contacto = new Contacto(empresa.getRazonSocial(), empresa.getTelefono(), empresa.getEmail());
        long codigo = codigoUnico();
        Cabecera cab = new Cabecera(rutEmpresa, normalizarRut(sobre.rutEmisor()), contacto, codigo);
        String nmbEnvio = (nombreArchivo == null || nombreArchivo.isBlank())
                ? "EnvioDTE.xml" : nombreArchivo;

        // 4) Artefacto 1: Respuesta de Intercambio (siempre).
        String respuestaIntercambio = respuestaGen.generarRecepcionEnvio(cab, new AcuseEnvio(
                nmbEnvio, LocalDateTime.now(clock), codigo, sobre.envioDteId(), sobre.digest(),
                normalizarRut(sobre.rutEmisor()), normalizarRut(sobre.rutReceptor()),
                estadoRecepEnv, evaluados), empresaId);

        // 5) Artefactos 2 y 3: solo si hay DTE aceptados.
        String reciboMercaderias = null;
        String resultadoComercial = null;
        if (!aceptados.isEmpty()) {
            String rutFirma = resolverRutFirma(empresaId, rutEmpresa);
            String recinto = recinto(empresa);
            List<ReciboItem> recibos = aceptados.stream()
                    .map(ev -> aReciboItem(ev, recinto, rutFirma))
                    .toList();
            reciboMercaderias = recibosGen.generar(
                    rutEmpresa, normalizarRut(sobre.rutEmisor()), contacto, recibos, empresaId);
            resultadoComercial = respuestaGen.generarResultadoComercial(cab, codigo, aceptados, empresaId);
        } else {
            log.info("Sobre {} sin DTE para nuestro RUT: solo se genera la Respuesta de Intercambio",
                    sobre.envioDteId());
        }

        return new RespuestaIntercambioResponse(respuestaIntercambio, reciboMercaderias, resultadoComercial,
                decisiones(evaluados));
    }

    private static ReciboItem aReciboItem(DteEvaluado ev, String recinto, String rutFirma) {
        SobreRecibido.DteRecibido d = ev.documento();
        return new ReciboItem(d.tipoDte(), d.folio(), d.fchEmis(),
                d.rutEmisor(), d.rutReceptor(), d.mntTotal(), recinto, rutFirma);
    }

    private List<DecisionDte> decisiones(List<DteEvaluado> evaluados) {
        return evaluados.stream()
                .map(ev -> new DecisionDte(
                        ev.documento().tipoDte(), ev.folio(), ev.documento().rutReceptor(),
                        ev.aceptado(), ev.estadoRecepDte(),
                        ev.aceptado() ? "DTE Recibido OK" : "DTE No Recibido - Error en RUT Receptor"))
                .toList();
    }

    /** RUN del firmante del certificado; en dev/test (sin cert) cae al RUT empresa. */
    private String resolverRutFirma(Long empresaId, String rutEmpresa) {
        return certificadoResolver.paraEmpresaSiExiste(empresaId)
                .map(CertificadoFirma::rutFirmante)
                .map(IntercambioService::normalizarRut)
                .orElse(rutEmpresa);
    }

    private static String recinto(Empresa empresa) {
        String dir = empresa.getDireccion();
        if (dir == null || dir.isBlank()) {
            return RECINTO_POR_DEFECTO;
        }
        return dir.length() > 80 ? dir.substring(0, 80) : dir;
    }

    /** Numero unico (<=10 digitos) para IdRespuesta/CodEnvio: segundos epoch. */
    private long codigoUnico() {
        return Instant.now(clock).getEpochSecond();
    }

    private static String normalizarRut(String rut) {
        return rut == null ? null : rut.replace(".", "").trim().toUpperCase();
    }
}
