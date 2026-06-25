package cl.nexosoftware.factura.producto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Page<Producto> findByEmpresaId(Long empresaId, Pageable pageable);
    Page<Producto> findByEmpresaIdAndNombreContainingIgnoreCase(Long empresaId, String q, Pageable pageable);
    Optional<Producto> findByIdAndEmpresaId(Long id, Long empresaId);
}
