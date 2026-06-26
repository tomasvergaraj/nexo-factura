package cl.nexosoftware.factura.tributario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test unitario del sello de integridad (SHA-256 hex del XML firmado). */
class SelloDteTest {

    @Test
    @DisplayName("el sello es un SHA-256 en hexadecimal (64 chars minusculas)")
    void formatoHex64() {
        String sello = SelloDte.calcular("<DTE>contenido</DTE>");
        assertThat(sello).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("el sello es deterministico para la misma entrada")
    void deterministico() {
        String a = SelloDte.calcular("<DTE/>");
        String b = SelloDte.calcular("<DTE/>");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("entradas distintas producen sellos distintos")
    void sensibleAlContenido() {
        String a = SelloDte.calcular("<DTE>a</DTE>");
        String b = SelloDte.calcular("<DTE>b</DTE>");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("vector conocido de SHA-256 (cadena vacia)")
    void vectorConocido() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(SelloDte.calcular(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
