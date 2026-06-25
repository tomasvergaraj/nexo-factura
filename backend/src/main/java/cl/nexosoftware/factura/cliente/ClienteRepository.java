package cl.nexosoftware.factura.cliente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Page<Cliente> findByEmpresaIdAndRazonSocialContainingIgnoreCase(Long empresaId, String q, Pageable pageable);
    Page<Cliente> findByEmpresaId(Long empresaId, Pageable pageable);
}
