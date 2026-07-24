package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvioLibroRepository extends JpaRepository<EnvioLibro, Long> {

    /** Envios de un libro (periodo + operacion), del mas reciente al mas antiguo. */
    List<EnvioLibro> findByEmpresaIdAndPeriodoAndTipoOperacionOrderByTmstEnvioDesc(
            Long empresaId, String periodo, TipoOperacion tipoOperacion);

    /** Envio por TrackID para refrescar su estado; acotado a la empresa (tenant). */
    Optional<EnvioLibro> findFirstByEmpresaIdAndTrackId(Long empresaId, String trackId);
}
