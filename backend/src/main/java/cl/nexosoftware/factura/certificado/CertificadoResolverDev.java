package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.tributario.CertificadoFirma;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Perfiles de desarrollo: no hay certificado real (la firma es
 * {@code FirmaElectronicaStub}). Los flujos con fallback (libro, intercambio,
 * PDF) usan {@link #paraEmpresaSiExiste} y caen al RUT del emisor.
 */
@Component
@Profile("!prod")
public class CertificadoResolverDev implements CertificadoResolver {

    @Override
    public CertificadoFirma paraEmpresa(Long empresaId) {
        throw new IllegalStateException(
                "No hay certificado digital en perfiles de desarrollo (la firma es stub)");
    }

    @Override
    public Optional<CertificadoFirma> paraEmpresaSiExiste(Long empresaId) {
        return Optional.empty();
    }
}
