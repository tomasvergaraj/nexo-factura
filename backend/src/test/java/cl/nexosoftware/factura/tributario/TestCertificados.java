package cl.nexosoftware.factura.tributario;

import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.util.Optional;

/**
 * Helper de tests: el certificado DUMMY de prueba (sii/cert_prueba.p12, clave
 * "test123", SERIALNUMBER=11111111-1) como {@link CertificadoFirma} y un
 * {@link CertificadoResolver} que lo devuelve para cualquier empresa (analogo
 * al modo GLOBAL). Evita repetir la carga del PKCS#12 en cada test.
 */
public final class TestCertificados {

    public static final String CLAVE = "test123";

    private TestCertificados() {}

    public static CertificadoFirma dummy() {
        try {
            byte[] p12 = Files.readAllBytes(new ClassPathResource("sii/cert_prueba.p12").getFile().toPath());
            return CertificadoFirma.desdeP12(p12, CLAVE, null);
        } catch (Exception e) {
            throw new IllegalStateException("Falta el fixture sii/cert_prueba.p12", e);
        }
    }

    /** Resolver que sirve el certificado dummy para toda empresa (modo GLOBAL de test). */
    public static CertificadoResolver resolver() {
        CertificadoFirma cert = dummy();
        return new CertificadoResolver() {
            @Override
            public CertificadoFirma paraEmpresa(Long empresaId) {
                return cert;
            }

            @Override
            public Optional<CertificadoFirma> paraEmpresaSiExiste(Long empresaId) {
                return Optional.of(cert);
            }
        };
    }
}
