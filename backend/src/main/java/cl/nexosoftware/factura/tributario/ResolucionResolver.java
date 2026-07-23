package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Resuelve la resolucion SII (FchResol/NroResol) que va en las caratulas de los
 * sobres (EnvioDTE/EnvioBOLETA), el libro IECV y la leyenda de la
 * representacion impresa.
 *
 * Orden de resolucion: los campos PROPIOS de la empresa
 * ({@code empresa.fch_resol}/{@code nro_resol}, el modelo multi-tenant) y, si
 * no estan, el FALLBACK de entorno (APP_SII_FCH_RESOL/NRO_RESOL, con WARN) —
 * asi el ambiente de certificacion sigue operando por variables sin tocar la
 * empresa. Una configuracion a medias (solo fecha o solo numero en la fila) es
 * un error explicito: mezclar mitades produciria caratulas inconsistentes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResolucionResolver {

    private final EmpresaRepository empresaRepository;
    private final AppProperties props;

    public record Resolucion(String fchResol, int nroResol) {}

    /**
     * Resolucion para una caratula de envio: obligatoria. Sin resolucion (ni
     * propia ni de entorno) el envio seria rechazado (RCT), asi que falla aqui
     * con instrucciones en vez de quemar un intento contra el SII.
     */
    public Resolucion paraCaratula(Long empresaId) {
        return siExiste(empresaId).orElseThrow(() -> new ReglaNegocioException(
                "La empresa " + empresaId + " no tiene resolucion SII configurada (fecha y numero): "
                        + "completela en los datos de la empresa (o defina APP_SII_FCH_RESOL de fallback)"));
    }

    /** Variante opcional para usos informativos (leyenda del PDF). */
    public Optional<Resolucion> siExiste(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId).orElse(null);
        if (empresa != null) {
            LocalDate fch = empresa.getFchResol();
            Integer nro = empresa.getNroResol();
            if (fch != null && nro != null) {
                return Optional.of(new Resolucion(fch.toString(), nro));
            }
            if (fch != null || nro != null) {
                throw new ReglaNegocioException(
                        "La resolucion SII de la empresa " + empresaId + " esta incompleta: "
                                + "configure fecha Y numero (o deje ambos vacios para usar el fallback de entorno)");
            }
        }
        String fallback = props.sii().fchResol();
        if (fallback == null || fallback.isBlank()) {
            return Optional.empty();
        }
        log.warn("Empresa {} sin resolucion propia: usando el fallback de entorno "
                + "(APP_SII_FCH_RESOL={}, NroResol={})", empresaId, fallback.trim(), props.sii().nroResol());
        return Optional.of(new Resolucion(validarFchResol(fallback), props.sii().nroResol()));
    }

    /** Valida el formato AAAA-MM-DD del fallback de entorno (fail-fast reutilizable). */
    public static String validarFchResol(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "APP_SII_FCH_RESOL es obligatoria para enviar al SII: es la fecha de resolucion "
                            + "que muestra 'Datos de la Empresa' del ambiente de certificacion");
        }
        try {
            LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    "APP_SII_FCH_RESOL invalida ('" + valor + "'): use el formato AAAA-MM-DD");
        }
        return valor.trim();
    }
}
