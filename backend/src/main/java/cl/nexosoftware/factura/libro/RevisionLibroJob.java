package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.config.RevisionLibroProperties;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Dispara la revision automatica de libros IECV: a diario, desde el dia
 * configurado del mes en adelante, prepara el libro del MES ANTERIOR de cada
 * empresa que puede firmar. La preparacion (firma + validacion) y el marcador
 * viven en {@link RevisionLibroService}; aca solo esta el "cuando" y "sobre
 * quienes". No postea nada al SII: el envio real sigue siendo manual.
 *
 * Idempotente: cada corrida re-evalua el marcador (upsert), asi un ERROR que se
 * corrige pasa a PREPARADO al dia siguiente, y un libro ya enviado deja de
 * figurar como pendiente.
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
     * En modo GLOBAL todas firman con el certificado del ambiente; en POR_EMPRESA
     * solo las que tienen su certificado cargado.
     */
    private boolean puedeFirmar(Long empresaId) {
        return "GLOBAL".equalsIgnoreCase(app.sii().firmaModo())
                || certificadoResolver.paraEmpresaSiExiste(empresaId).isPresent();
    }
}
