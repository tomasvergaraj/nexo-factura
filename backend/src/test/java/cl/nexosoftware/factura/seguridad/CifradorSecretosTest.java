package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cifrado en reposo AES-256-GCM: roundtrip, deteccion de manipulacion (el tag
 * GCM), rechazo de claves mal dimensionadas y de la clave de desarrollo.
 */
class CifradorSecretosTest {

    // Clave de PRODUCCION de prueba: 32 bytes, distinta de la de desarrollo.
    private static final String CLAVE_32 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private static CifradorSecretos con(String masterKeyBase64) {
        return new CifradorSecretos(new AppProperties(null, null, null,
                new AppProperties.Security(masterKeyBase64)));
    }

    @Test
    @DisplayName("cifra y descifra: el claro vuelve identico")
    void roundtrip() {
        CifradorSecretos cif = con(CLAVE_32);
        byte[] claro = "clave-secreta-del-p12-áéí".getBytes(StandardCharsets.UTF_8);

        byte[] blob = cif.cifrar(claro);
        assertThat(blob).isNotEqualTo(claro);
        assertThat(cif.descifrar(blob)).isEqualTo(claro);
    }

    @Test
    @DisplayName("dos cifrados del mismo claro difieren (IV aleatorio por cifrado)")
    void ivAleatorio() {
        CifradorSecretos cif = con(CLAVE_32);
        byte[] claro = "mismo-contenido".getBytes(StandardCharsets.UTF_8);
        assertThat(cif.cifrar(claro)).isNotEqualTo(cif.cifrar(claro));
    }

    @Test
    @DisplayName("un byte alterado del blob hace fallar el descifrado (tag GCM)")
    void tamperDetectado() {
        CifradorSecretos cif = con(CLAVE_32);
        byte[] blob = cif.cifrar("intacto".getBytes(StandardCharsets.UTF_8));
        blob[blob.length - 1] ^= 0x01; // adultera el ultimo byte (parte del tag)

        assertThatThrownBy(() -> cif.descifrar(blob))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo descifrar");
    }

    @Test
    @DisplayName("otra clave maestra no puede descifrar lo cifrado con la primera")
    void claveDistintaNoDescifra() {
        byte[] blob = con(CLAVE_32).cifrar("secreto".getBytes(StandardCharsets.UTF_8));
        String otra = Base64.getEncoder()
                .encodeToString("ZZZZZZZZZZZZZZZZ0123456789abcdef".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> con(otra).descifrar(blob))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("clave de tamano invalido: falla al construir")
    void claveTamanoInvalido() {
        String corta = Base64.getEncoder().encodeToString("clave-corta".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> con(corta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("sin clave configurada: no disponible y cifrar lanza")
    void sinClave() {
        CifradorSecretos cif = con(null);
        assertThat(cif.disponible()).isFalse();
        assertThatThrownBy(() -> cif.cifrar(new byte[]{1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MASTER_KEY");
    }

    @Test
    @DisplayName("reconoce la clave de desarrollo conocida")
    void detectaClaveDev() {
        assertThat(con(CifradorSecretos.CLAVE_DEV).esClaveDeDesarrollo()).isTrue();
        assertThat(con(CLAVE_32).esClaveDeDesarrollo()).isFalse();
    }
}
