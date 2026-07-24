package cl.nexosoftware.factura.tributario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolucion del RUN del firmante en {@link CertificadoFirma}. Regla: el RUN del
 * SERIALNUMBER del certificado (11111111-1 en sii/cert_prueba.p12) manda; el
 * override manual SOLO suple su ausencia y no puede contradecirlo — firmar con un
 * rutSender que no corresponde al certificado hace que el SII rechace el envio.
 */
class CertificadoFirmaTest {

    private static byte[] p12() throws Exception {
        return Files.readAllBytes(new ClassPathResource("sii/cert_prueba.p12").getFile().toPath());
    }

    @Test
    @DisplayName("sin override: usa el RUN del SERIALNUMBER del certificado")
    void usaElRutDelCertificado() throws Exception {
        CertificadoFirma cert = CertificadoFirma.desdeP12(p12(), TestCertificados.CLAVE, null);
        assertThat(cert.rutFirmante()).isEqualTo("11111111-1");
    }

    @Test
    @DisplayName("override que coincide (con o sin puntos): se acepta y prima el del certificado")
    void overrideQueCoincide() throws Exception {
        assertThat(CertificadoFirma.desdeP12(p12(), TestCertificados.CLAVE, "11111111-1").rutFirmante())
                .isEqualTo("11111111-1");
        assertThat(CertificadoFirma.desdeP12(p12(), TestCertificados.CLAVE, "11.111.111-1").rutFirmante())
                .isEqualTo("11111111-1");
    }

    @Test
    @DisplayName("override que NO coincide con el certificado: error explicito, no se firma con otro RUN")
    void overrideDistintoEsError() throws Exception {
        byte[] p12 = p12();
        assertThatThrownBy(() -> CertificadoFirma.desdeP12(p12, TestCertificados.CLAVE, "22222222-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");
    }
}
