package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.LibroPendienteResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.libro.LibroPendiente.Estado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/**
 * Revision automatica de los libros IECV: por cada libro (VENTA y COMPRA) de un
 * periodo, si tiene movimiento y no fue aun gestionado, lo PREPARA —lo firma y
 * valida contra el esquema oficial SIN postearlo al SII— y deja un marcador
 * {@link LibroPendiente} (PREPARADO o ERROR con el motivo) para que la UI avise
 * y el usuario apriete "Enviar". No manda nada al SII: el envio real sigue
 * siendo manual.
 *
 * "Gestionado" = ya existe un envio del libro que no esta RECHAZADO (aceptado o
 * en vuelo); en ese caso el marcador sobra y se borra. La orquestacion temporal
 * (cuando corre, sobre que empresas) vive en {@link RevisionLibroJob}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevisionLibroService {

    private final LibroService libroService;
    private final LibroEnvioService envioService;
    private final EnvioLibroRepository envioLibroRepository;
    private final LibroPendienteRepository pendienteRepository;
    private final LibroPendienteStore store;

    /**
     * Revisa ambos libros (VENTA y COMPRA) del periodo para una empresa.
     *
     * A proposito NO es {@code @Transactional}: preparar el libro llama a
     * {@code xmlFirmado()} (que si lo es) y, si esa preparacion falla, no debe
     * arrastrar la escritura del marcador a un rollback. Cada paso corre en su
     * propia transaccion: la preparacion (que puede fallar) queda aislada del
     * upsert del marcador, delegado en {@link LibroPendienteStore}.
     */
    public void revisar(Long empresaId, YearMonth periodo) {
        for (TipoOperacion operacion : TipoOperacion.values()) {
            revisarOperacion(empresaId, periodo, operacion);
        }
    }

    /** Libros pendientes de una empresa; oculta los que ya quedaron gestionados. */
    @Transactional(readOnly = true)
    public List<LibroPendienteResponse> listarPendientes(Long empresaId) {
        return pendienteRepository.findByEmpresaIdOrderByPeriodoDescTipoOperacionAsc(empresaId).stream()
                .filter(p -> !gestionado(empresaId, p.getPeriodo(), p.getTipoOperacion()))
                .map(RevisionLibroService::aResponse)
                .toList();
    }

    private void revisarOperacion(Long empresaId, YearMonth periodo, TipoOperacion operacion) {
        LibroResponse libro = libroService.construir(empresaId, operacion, periodo, null);
        // Sin nada que enviar (sin movimiento) o ya gestionado: no hay pendiente.
        if (libro.sinMovimiento() || gestionado(empresaId, periodo.toString(), operacion)) {
            store.borrar(empresaId, periodo.toString(), operacion);
            return;
        }
        // Falta el factor de proporcionalidad: se corta aca en vez de dejar que
        // reviente el generador, porque su mensaje ("informe el factor (fctProp)")
        // le sirve a quien llama la API, no a quien mira la UI y necesita saber
        // DONDE se configura. Era el caso que dejaba estos periodos en ERROR
        // permanente sin decir como salir de el.
        if (libro.tieneIvaUsoComun() && libro.fctProp() == null) {
            store.guardar(empresaId, periodo.toString(), operacion, Estado.ERROR,
                    "El libro tiene IVA de uso comun y la empresa no tiene configurado el factor "
                            + "de proporcionalidad. Configuralo en Configuracion para poder enviarlo.");
            return;
        }
        Estado estado;
        String detalle;
        try {
            // Firma + validacion XSD del libro MENSUAL, sin postearlo al SII.
            // fctProp = null: no es "sin factor", es "sin override" — LibroService
            // resuelve el de la empresa, que es justo el que se verifico arriba.
            envioService.xmlFirmado(empresaId, operacion, periodo, null, "MENSUAL", null);
            estado = Estado.PREPARADO;
            detalle = null;
        } catch (Exception e) {
            estado = Estado.ERROR;
            detalle = e.getMessage();
            log.warn("Libro {} {} de empresa {} no se pudo preparar: {}",
                    operacion, periodo, empresaId, e.toString());
        }
        store.guardar(empresaId, periodo.toString(), operacion, estado, detalle);
    }

    /** Un libro esta gestionado si tiene un envio que no esta RECHAZADO. */
    private boolean gestionado(Long empresaId, String periodo, TipoOperacion operacion) {
        return envioLibroRepository
                .findByEmpresaIdAndPeriodoAndTipoOperacionOrderByTmstEnvioDesc(empresaId, periodo, operacion)
                .stream()
                .anyMatch(e -> !"RECHAZADO".equals(e.getEstado()));
    }

    private static LibroPendienteResponse aResponse(LibroPendiente p) {
        return new LibroPendienteResponse(p.getId(), p.getPeriodo(), p.getTipoOperacion(),
                p.getEstado().name(), p.getDetalle(), p.getTmstRevision());
    }
}
