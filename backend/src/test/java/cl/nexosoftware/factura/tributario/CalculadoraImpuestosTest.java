package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.LineaDetalle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitarios puros del calculo de impuestos (sin contexto de Spring). */
class CalculadoraImpuestosTest {

    private final CalculadoraImpuestos calculadora = new CalculadoraImpuestos();

    private LineaDetalle linea(long monto, boolean afecto) {
        return LineaDetalle.builder().montoLinea(monto).afecto(afecto).build();
    }

    @Test
    @DisplayName("IVA 19% se calcula sobre el neto y se redondea al peso")
    void calculaIvaSobreNeto() {
        var totales = calculadora.calcular(List.of(linea(10000, true)), 19.0);
        assertThat(totales.neto()).isEqualTo(10000);
        assertThat(totales.iva()).isEqualTo(1900);
        assertThat(totales.total()).isEqualTo(11900);
        assertThat(totales.exento()).isZero();
    }

    @Test
    @DisplayName("El redondeo del IVA usa half-up al peso")
    void redondeaIva() {
        // 19% de 1990 = 378,1 -> 378
        var t1 = calculadora.calcular(List.of(linea(1990, true)), 19.0);
        assertThat(t1.iva()).isEqualTo(378);
        // 19% de 2110 = 400,9 -> 401
        var t2 = calculadora.calcular(List.of(linea(2110, true)), 19.0);
        assertThat(t2.iva()).isEqualTo(401);
    }

    @Test
    @DisplayName("Separa montos afectos y exentos")
    void separaAfectoYExento() {
        var totales = calculadora.calcular(
                List.of(linea(50000, true), linea(30000, false)), 19.0);
        assertThat(totales.neto()).isEqualTo(50000);
        assertThat(totales.exento()).isEqualTo(30000);
        assertThat(totales.iva()).isEqualTo(9500);
        assertThat(totales.total()).isEqualTo(89500); // 50000 + 9500 + 30000
    }

    @Test
    @DisplayName("El monto de una linea descuenta y nunca es negativo")
    void calculaMontoLinea() {
        assertThat(calculadora.montoLinea(3, 25000, 5000)).isEqualTo(70000);
        assertThat(calculadora.montoLinea(1, 1000, 5000)).isZero();
    }
}
