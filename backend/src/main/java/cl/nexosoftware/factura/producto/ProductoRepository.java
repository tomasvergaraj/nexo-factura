package cl.nexosoftware.factura.producto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Page<Producto> findByEmpresaId(Long empresaId, Pageable pageable);
    Page<Producto> findByEmpresaIdAndNombreContainingIgnoreCase(Long empresaId, String q, Pageable pageable);
}
