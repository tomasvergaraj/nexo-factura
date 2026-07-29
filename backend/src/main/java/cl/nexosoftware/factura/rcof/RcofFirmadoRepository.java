package cl.nexosoftware.factura.rcof;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RcofFirmadoRepository extends JpaRepository<RcofFirmado, Long> {

    /** Ultimo archivo firmado de ese dia; vacio si nunca se genero. */
    Optional<RcofFirmado> findFirstByEmpresaIdAndFechaOrderBySecEnvioDesc(Long empresaId, LocalDate fecha);
}
