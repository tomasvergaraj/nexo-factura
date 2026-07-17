package cl.nexosoftware.factura.compra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<DocumentoCompra, Long> {

    List<DocumentoCompra> findByEmpresaIdAndFechaEmisionBetweenOrderByTipoDteAscFolioAsc(
            Long empresaId, LocalDate desde, LocalDate hasta);

    /**
     * Compras de un periodo tributario, ordenadas como el libro (tipo y folio).
     * Punto unico de la regla "que pertenece al periodo": lo comparten el listado
     * de compras y el libro de compras para que no diverjan.
     */
    default List<DocumentoCompra> delPeriodo(Long empresaId, YearMonth periodo) {
        return findByEmpresaIdAndFechaEmisionBetweenOrderByTipoDteAscFolioAsc(
                empresaId, periodo.atDay(1), periodo.atEndOfMonth());
    }

    Optional<DocumentoCompra> findByIdAndEmpresaId(Long id, Long empresaId);
}
