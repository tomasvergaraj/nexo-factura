package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.config.RevisionLibroProperties;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.SiiGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Tareas programadas de los libros IECV. Aca vive solo el "cuando" y "sobre
 * quienes"; el trabajo en si esta en {@link RevisionLibroService} y
 * {@link LibroEnvioService}.
 *
 * <p><b>1. Revision de libros pendientes.</b> A diario, desde el dia configurado
 * del mes en adelante, prepara el libro del MES ANTERIOR de cada empresa que
 * puede firmar. No postea nada al SII: el envio real sigue siendo manual.
 * Idempotente: cada corrida re-evalua el marcador (upsert), asi un ERROR que se
 * corrige pasa a PREPARADO al dia siguiente, y un libro ya enviado deja de
 * figurar como pendiente.
 *
 * <p><b>2. Consulta de estados de envio.</b> El POST del libro solo devuelve un
 * TrackID; la resolucion (ACEPTADO / ACEPTADO_CON_REPARO / RECHAZADO) llega
 * despues por QueryEstUp. Hasta ahora habia que pedirla a mano por cada TrackID,
 * asi que un libro RECHAZADO podia pasar inadvertido justo cuando mas urge
 * reaccionar. Este job la consulta a diario para los envios que aun no tienen
 * resolucion, y la deja persistida para que la UI la muestre sin volver al SII.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RevisionLibroJob {

    private final RevisionLibroProperties props;
    private final AppProperties app;
    private final EmpresaRepository empresaRepository;
    private final CertificadoResolver certificadoResolver;
    private final RevisionLibroService revisionService;
    private final LibroEnvioService envioService;
    private final Clock clock;

    @Scheduled(cron = "${app.libro.revision-auto.cron}")
    public void revisarLibrosDelMesAnterior() {
        if (!props.enabled()) {
            return;
        }
        LocalDate hoy = LocalDate.now(clock);
        if (hoy.getDayOfMonth() < props.dia()) {
            return; // margen para registrar las compras del mes antes de avisar
        }
        YearMonth periodo = YearMonth.from(hoy).minusMonths(1);
        log.info("Revision automatica de libros IECV pendientes: periodo {}", periodo);
        int revisadas = 0;
        for (Empresa empresa : empresaRepository.findAll()) {
            if (!puedeFirmar(empresa.getId())) {
                continue; // sin certificado en modo por-empresa: no puede preparar el libro
            }
            try {
                revisionService.revisar(empresa.getId(), periodo);
                revisadas++;
            } catch (Exception e) {
                // Una empresa que falla no aborta el resto del lote.
                log.warn("Revision de libros fallo para empresa {} periodo {}: {}",
                        empresa.getId(), periodo, e.toString());
            }
        }
        log.info("Revision automatica de libros IECV terminada: {} empresa(s) revisada(s)", revisadas);
    }

    /**
     * Consulta al SII el estado de los envios de libro que aun no lo tienen
     * resuelto, y lo persiste.
     *
     * No mira el dia del mes ni un periodo concreto, a diferencia de la revision:
     * un envio espera resolucion desde que se hace, sea del periodo que sea.
     * Cada TrackID se consulta en su propia transaccion (via el metodo publico
     * del servicio), asi que uno que falle —red, token, TrackID que el SII ya no
     * reconoce— no aborta el resto ni pierde lo ya resuelto.
     */
    @Scheduled(cron = "${app.libro.revision-auto.cron-estado}")
    public void consultarEstadosDeEnvio() {
        if (!props.enabled()) {
            return;
        }
        int consultados = 0;
        int resueltos = 0;
        for (Empresa empresa : empresaRepository.findAll()) {
            if (!puedeFirmar(empresa.getId())) {
                continue; // sin certificado no hay token para QueryEstUp
            }
            for (var envio : envioService.pendientesDeResolucion(empresa.getId())) {
                try {
                    var estado = envioService.estadoEnvio(empresa.getId(), envio.trackId());
                    consultados++;
                    if (estado != SiiGateway.EstadoEnvio.RECIBIDO) {
                        resueltos++;
                        log.info("Libro {} {} de empresa {} (TrackID {}): {}",
                                envio.tipoOperacion(), envio.periodo(), empresa.getId(),
                                envio.trackId(), estado);
                    }
                } catch (Exception e) {
                    log.warn("No se pudo consultar el estado del envio {} (empresa {}): {}",
                            envio.trackId(), empresa.getId(), e.toString());
                }
            }
        }
        if (consultados > 0) {
            log.info("Consulta automatica de estados de libro: {} consultado(s), {} resuelto(s)",
                    consultados, resueltos);
        }
    }

    /**
     * En modo GLOBAL todas firman con el certificado del ambiente; en POR_EMPRESA
     * solo las que tienen su certificado cargado.
     */
    private boolean puedeFirmar(Long empresaId) {
        return "GLOBAL".equalsIgnoreCase(app.sii().firmaModo())
                || certificadoResolver.paraEmpresaSiExiste(empresaId).isPresent();
    }
}
