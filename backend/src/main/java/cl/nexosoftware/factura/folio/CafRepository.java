package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.documento.TipoDte;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CafRepository extends JpaRepository<Caf, Long> {

    List<Caf> findByEmpresaIdOrderByTipoDteAscFolioDesdeAsc(Long empresaId);

    /**
     * Bloquea (SELECT ... FOR UPDATE) el CAF vigente con menor rango que aun tenga
     * folios disponibles, garantizando asignacion atomica del siguiente folio.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Caf c
            where c.empresaId = :empresaId
              and c.tipoDte = :tipoDte
              and c.agotado = false
              and c.folioActual < c.folioHasta
            order by c.folioDesde asc
            limit 1
            """)
    Optional<Caf> bloquearCafDisponible(@Param("empresaId") Long empresaId,
                                        @Param("tipoDte") TipoDte tipoDte);
}
