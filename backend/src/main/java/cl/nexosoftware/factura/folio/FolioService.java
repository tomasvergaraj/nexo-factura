package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.documento.TipoDte;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignacion de folios. El metodo {@link #siguienteFolio} debe ejecutarse dentro
 * de la transaccion del documento para que el folio se reserve solo si el DTE
 * persiste. El bloqueo pesimista del CAF serializa a los emisores concurrentes y
 * elimina la condicion de carrera por folios duplicados.
 */
@Service
@RequiredArgsConstructor
public class FolioService {

    private final CafRepository cafRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public FolioAsignado siguienteFolio(Long empresaId, TipoDte tipoDte) {
        Caf caf = cafRepository.bloquearCafDisponible(empresaId, tipoDte)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No hay folios disponibles ni vigentes para " + tipoDte.getDescripcion()
                                + ". Cargue un nuevo CAF desde el SII."));

        long folio = caf.getFolioActual() + 1;
        caf.setFolioActual(folio);
        if (folio >= caf.getFolioHasta()) {
            caf.setAgotado(true);
        }
        cafRepository.save(caf);
        return new FolioAsignado(folio, caf);
    }
}
