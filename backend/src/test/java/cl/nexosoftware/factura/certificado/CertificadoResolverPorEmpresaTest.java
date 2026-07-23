package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.seguridad.CifradorSecretos;
import cl.nexosoftware.factura.tributario.CertificadoFirma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El resolver POR_EMPRESA recorre el camino completo: lee la fila cifrada,
 * descifra con la clave maestra, abre el PKCS#12 y expone el firmante. Tambien
 * cubre sus guardas: sin certificado es error de negocio, y arrancar con la
 * clave de desarrollo o sin clave maestra esta prohibido.
 */
class CertificadoResolverPorEmpresaTest {

    private static final String CLAVE_PROD =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private static CifradorSecretos cifrador(String masterKey) {
        return new CifradorSecretos(new AppProperties(null, null, null,
                new AppProperties.Security(masterKey)));
    }

    private static byte[] p12Dummy() throws Exception {
        return Files.readAllBytes(new ClassPathResource("sii/cert_prueba.p12").getFile().toPath());
    }

    private static CertificadoEmpresa filaCifrada(CifradorSecretos cif) throws Exception {
        byte[] p12 = p12Dummy();
        CertificadoFirma cert = CertificadoFirma.desdeP12(p12, "test123", null);
        return CertificadoEmpresa.builder()
                .empresaId(5L)
                .nombreArchivo("cert.p12")
                .p12Cifrado(cif.cifrar(p12))
                .passwordCifrada(cif.cifrar("test123".getBytes(StandardCharsets.UTF_8)))
                .rutFirmante(cert.rutFirmante())
                .huellaSha256(cert.huellaSha256())
                .validoDesde(OffsetDateTime.now().minusYears(1))
                .validoHasta(OffsetDateTime.now().plusYears(1))
                .keyVersion(1)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("descifra la fila, abre el PKCS#12 y expone el firmante 11111111-1")
    void resuelveElCertificadoDeLaEmpresa() throws Exception {
        CifradorSecretos cif = cifrador(CLAVE_PROD);
        CertificadoEmpresaRepository repo = mock(CertificadoEmpresaRepository.class);
        when(repo.findByEmpresaIdAndActivoTrue(5L)).thenReturn(Optional.of(filaCifrada(cif)));

        var resolver = new CertificadoResolverPorEmpresa(repo, cif);
        CertificadoFirma cert = resolver.paraEmpresa(5L);

        assertThat(cert.rutFirmante()).isEqualTo("11111111-1");
        assertThat(resolver.paraEmpresaSiExiste(5L)).isPresent();
    }

    @Test
    @DisplayName("empresa sin certificado: error de negocio explicito")
    void sinCertificadoErrorDeNegocio() {
        CertificadoEmpresaRepository repo = mock(CertificadoEmpresaRepository.class);
        when(repo.findByEmpresaIdAndActivoTrue(any())).thenReturn(Optional.empty());

        var resolver = new CertificadoResolverPorEmpresa(repo, cifrador(CLAVE_PROD));

        assertThat(resolver.paraEmpresaSiExiste(7L)).isEmpty();
        assertThatThrownBy(() -> resolver.paraEmpresa(7L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no tiene certificado digital");
    }

    @Test
    @DisplayName("arrancar sin clave maestra o con la de desarrollo esta prohibido")
    void guardasDeClaveMaestra() {
        CertificadoEmpresaRepository repo = mock(CertificadoEmpresaRepository.class);

        assertThatThrownBy(() -> new CertificadoResolverPorEmpresa(repo, cifrador(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MASTER_KEY");

        assertThatThrownBy(() -> new CertificadoResolverPorEmpresa(repo, cifrador(CifradorSecretos.CLAVE_DEV)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desarrollo");
    }
}
