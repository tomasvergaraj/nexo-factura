package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cifrado transparente de un campo de texto: roundtrip, formato marcado,
 * convivencia con filas legacy en claro y negativa a degradar sin clave.
 */
class SecretoTextoConverterTest {

    private static final String XML_CAF = "<AUTORIZACION><CAF><RSASK>clave-privada</RSASK></CAF></AUTORIZACION>";

    private static SecretoTextoConverter con(String masterKeyBase64) {
        return new SecretoTextoConverter(
                new CifradorSecretos(new AppProperties(null, null, null,
                        new AppProperties.Security(masterKeyBase64))));
    }

    @Test
    @DisplayName("lo almacenado va marcado y no contiene el claro; al leer vuelve identico")
    void roundtrip() {
        SecretoTextoConverter conv = con(CifradorSecretos.CLAVE_DEV);

        String almacenado = conv.convertToDatabaseColumn(XML_CAF);

        assertThat(almacenado).startsWith(SecretoTextoConverter.PREFIJO);
        assertThat(almacenado).doesNotContain("RSASK").doesNotContain("clave-privada");
        assertThat(conv.convertToEntityAttribute(almacenado)).isEqualTo(XML_CAF);
    }

    @Test
    @DisplayName("fila legacy en texto plano: se lee tal cual")
    void legacyEnClaroSeLee() {
        assertThat(con(CifradorSecretos.CLAVE_DEV).convertToEntityAttribute(XML_CAF)).isEqualTo(XML_CAF);
    }

    @Test
    @DisplayName("null pasa en ambos sentidos (CAF legacy sin XML)")
    void nullPasa() {
        SecretoTextoConverter conv = con(CifradorSecretos.CLAVE_DEV);
        assertThat(conv.convertToDatabaseColumn(null)).isNull();
        assertThat(conv.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("no re-cifra un valor ya cifrado")
    void noCifraDosVeces() {
        SecretoTextoConverter conv = con(CifradorSecretos.CLAVE_DEV);
        String almacenado = conv.convertToDatabaseColumn(XML_CAF);

        assertThat(conv.convertToDatabaseColumn(almacenado)).isEqualTo(almacenado);
    }

    @Test
    @DisplayName("sin clave maestra la escritura falla en vez de guardar en claro")
    void sinClaveNoDegrada() {
        assertThatThrownBy(() -> con(null).convertToDatabaseColumn(XML_CAF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MASTER_KEY");
    }

    @Test
    @DisplayName("otra clave maestra no puede leer el CAF cifrado")
    void claveDistintaNoLee() {
        String almacenado = con(CifradorSecretos.CLAVE_DEV).convertToDatabaseColumn(XML_CAF);
        String otra = java.util.Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> con(otra).convertToEntityAttribute(almacenado))
                .isInstanceOf(IllegalStateException.class);
    }
}
