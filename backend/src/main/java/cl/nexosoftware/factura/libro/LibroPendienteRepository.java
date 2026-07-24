package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LibroPendienteRepository extends JpaRepository<LibroPendiente, Long> {

    /** Marcadores de una empresa, del periodo mas reciente al mas antiguo. */
    List<LibroPendiente> findByEmpresaIdOrderByPeriodoDescTipoOperacionAsc(Long empresaId);

    /** Marcador vigente de un libro (para el upsert de la revision). */
    Optional<LibroPendiente> findByEmpresaIdAndPeriodoAndTipoOperacion(
            Long empresaId, String periodo, TipoOperacion tipoOperacion);

    /** Borra el marcador de un libro (ya enviado o sin movimiento). */
    void deleteByEmpresaIdAndPeriodoAndTipoOperacion(
            Long empresaId, String periodo, TipoOperacion tipoOperacion);
}
