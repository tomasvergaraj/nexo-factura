package cl.nexosoftware.factura.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link Rut#esValido(String)} reproduce el algoritmo modulo 11
 * de {@code frontend/src/lib/format.ts} (fuente de verdad del RUT).
 */
class RutTest {

    @ParameterizedTest
    @DisplayName("RUTs con digito verificador correcto son validos")
    @ValueSource(strings = {
            "76543210-3",   // DV numerico, con guion
            "765432103",    // mismo RUT sin guion ni puntos
            "76.543.210-3", // mismo RUT con puntos y guion
            "78222333-K",   // DV = K (resto 10)
            "78222333-k"    // DV = K en minuscula (se normaliza)
    })
    void rutsValidos(String rut) {
        assertThat(Rut.esValido(rut)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("RUTs con digito verificador incorrecto son invalidos")
    @ValueSource(strings = {
            "76543210-0",   // DV equivocado (deberia ser 3)
            "76543210-9",   // DV equivocado (deberia ser 3)
            "78222333-4"    // deberia ser K
    })
    void rutsConDvIncorrecto(String rut) {
        assertThat(Rut.esValido(rut)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("RUTs sin cuerpo numerico o sin contenido son invalidos")
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "k", "-9", "   "})
    void rutsNoNumericos(String rut) {
        assertThat(Rut.esValido(rut)).isFalse();
    }

    @Test
    @DisplayName("resto 11 produce DV '0'")
    void restoOnceEsCero() {
        // cuerpo 10000004 -> suma multiplo de 11 -> resto 11 -> DV '0'
        assertThat(Rut.esValido("10000004-0")).isTrue();
    }

    @ParameterizedTest
    @DisplayName("normalizar deja el RUT en forma canonica NNNNNNNN-D (sin puntos)")
    @CsvSource({
            "76.543.210-3, 76543210-3",
            "76543210-3,   76543210-3",
            "765432103,    76543210-3",
            "78222333-k,   78222333-K",
            "66666666-6,   66666666-6"
    })
    void normalizaACanonico(String entrada, String esperado) {
        assertThat(Rut.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("normalizar de null devuelve null")
    void normalizaNull() {
        assertThat(Rut.normalizar(null)).isNull();
    }

    @ParameterizedTest
    @DisplayName("formatear agrega separador de miles para la muestra impresa (Manual 1.2)")
    @CsvSource({
            "78397017-1,   78.397.017-1",  // el RUT del emisor de certificacion
            "12345678-5,   12.345.678-5",
            "765432103,    76.543.210-3",  // acepta la entrada sin puntos ni guion
            "76.543.210-3, 76.543.210-3",  // idempotente
            "78222333-k,   78.222.333-K",  // DV K normalizado a mayuscula
            "1-9,          1-9"            // cuerpo corto: sin puntos
    })
    void formateaConPuntos(String entrada, String esperado) {
        assertThat(Rut.formatear(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("formatear de null devuelve null")
    void formateaNull() {
        assertThat(Rut.formatear(null)).isNull();
    }
}
