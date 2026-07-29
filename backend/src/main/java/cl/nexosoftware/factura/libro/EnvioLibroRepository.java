package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnvioLibroRepository extends JpaRepository<EnvioLibro, Long> {

    /** Envios de un libro (periodo + operacion), del mas reciente al mas antiguo. */
    List<EnvioLibro> findByEmpresaIdAndPeriodoAndTipoOperacionOrderByTmstEnvioDesc(
            Long empresaId, String periodo, TipoOperacion tipoOperacion);

    /** Envio por TrackID para refrescar su estado; acotado a la empresa (tenant). */
    Optional<EnvioLibro> findFirstByEmpresaIdAndTrackId(Long empresaId, String trackId);

    /**
     * Envios sin resolucion definitiva del SII: nunca consultados ({@code estado}
     * nulo, porque el POST solo devuelve el TrackID) o todavia en proceso
     * (RECIBIDO). Los otros tres estados —ACEPTADO, ACEPTADO_CON_REPARO y
     * RECHAZADO— son terminales y no vale la pena volver a preguntarlos.
     *
     * Del mas antiguo al mas nuevo: si hay muchos, conviene resolver primero los
     * que llevan mas tiempo esperando.
     */
    @Query("""
            select e from EnvioLibro e
            where e.empresaId = :empresaId
              and (e.estado is null or e.estado = :enProceso)
            order by e.tmstEnvio asc
            """)
    List<EnvioLibro> findPendientesDeResolucion(@Param("empresaId") Long empresaId,
                                                @Param("enProceso") String enProceso);
}
