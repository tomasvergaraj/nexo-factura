package cl.nexosoftware.factura.documento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<DocumentoTributario, Long> {

    /**
     * Trae el documento con sus lineas en una sola consulta (evita N+1).
     * Solo se hace fetch de "lineas": incluir ademas "referencias" en el mismo
     * EntityGraph provoca MultipleBagFetchException (Hibernate no puede hacer
     * fetch de dos colecciones tipo List/bag a la vez). Las referencias se
     * cargan de forma perezosa dentro de la transaccion del servicio.
     */
    @EntityGraph(attributePaths = {"lineas"})
    Optional<DocumentoTributario> findWithDetalleById(Long id);

    Page<DocumentoTributario> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId, Pageable pageable);

    Page<DocumentoTributario> findByEmpresaIdAndEstadoOrderByCreadoEnDesc(
            Long empresaId, EstadoDte estado, Pageable pageable);

    long countByEmpresaIdAndEstado(Long empresaId, EstadoDte estado);

    @Query("""
            select coalesce(sum(d.total), 0) from DocumentoTributario d
            where d.empresaId = :empresaId
              and d.estado in (cl.nexosoftware.factura.documento.EstadoDte.ENVIADO,
                               cl.nexosoftware.factura.documento.EstadoDte.ACEPTADO)
              and d.fechaEmision >= :desde
            """)
    long sumTotalEmitidoDesde(@Param("empresaId") Long empresaId, @Param("desde") LocalDate desde);

    long countByEmpresaIdAndFechaEmisionGreaterThanEqual(Long empresaId, LocalDate desde);
}
