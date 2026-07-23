package cl.nexosoftware.factura.certificado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificadoEmpresaRepository extends JpaRepository<CertificadoEmpresa, Long> {

    /** El certificado vigente de la empresa (a lo mas uno, por indice parcial). */
    Optional<CertificadoEmpresa> findByEmpresaIdAndActivoTrue(Long empresaId);
}
