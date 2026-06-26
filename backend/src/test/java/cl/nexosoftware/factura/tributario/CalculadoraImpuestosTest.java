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

    // ---- Boletas: precios brutos (IVA incluido) — se desglosa el neto del total afecto ----

    @Test
    @DisplayName("Boleta afecta: desglosa neto e IVA de un total bruto exacto")
    void boletaAfectaExacta() {
        var t = calculadora.calcular(List.of(linea(11900, true)), 19.0, true);
        assertThat(t.neto()).isEqualTo(10000);
        assertThat(t.iva()).isEqualTo(1900);
        assertThat(t.exento()).isZero();
        assertThat(t.total()).isEqualTo(11900);
    }

    @Test
    @DisplayName("Boleta afecta: redondea el neto y el total bruto se conserva")
    void boletaAfectaRedondeo() {
        // 100 / 1.19 = 84,03 -> neto 84; iva = 100 - 84 = 16
        var t = calculadora.calcular(List.of(linea(100, true)), 19.0, true);
        assertThat(t.neto()).isEqualTo(84);
        assertThat(t.iva()).isEqualTo(16);
        assertThat(t.total()).isEqualTo(100);
    }

    @Test
    @DisplayName("Boleta afecta: iva = bruto - neto evita el descalce del re-redondeo")
    void boletaAfectaSinDescalce() {
        // 999 / 1.19 = 839,49 -> neto 839; iva = 999 - 839 = 160 (round(839*0,19)=159 estaria mal)
        var t1 = calculadora.calcular(List.of(linea(999, true)), 19.0, true);
        assertThat(t1.neto()).isEqualTo(839);
        assertThat(t1.iva()).isEqualTo(160);
        assertThat(t1.total()).isEqualTo(999);
        // 9999 / 1.19 = 8402,52 -> neto 8403; iva = 9999 - 8403 = 1596
        var t2 = calculadora.calcular(List.of(linea(9999, true)), 19.0, true);
        assertThat(t2.neto()).isEqualTo(8403);
        assertThat(t2.iva()).isEqualTo(1596);
        assertThat(t2.total()).isEqualTo(9999);
    }

    @Test
    @DisplayName("Boleta exenta: sin lineas afectas el desglose es cero y todo es exento")
    void boletaExentaNoOp() {
        var t = calculadora.calcular(List.of(linea(30000, false), linea(5000, false)), 19.0, true);
        assertThat(t.neto()).isZero();
        assertThat(t.iva()).isZero();
        assertThat(t.exento()).isEqualTo(35000);
        assertThat(t.total()).isEqualTo(35000);
    }

    @Test
    @DisplayName("Boleta mixta: desglosa solo el subtotal afecto y suma el exento aparte")
    void boletaMixta() {
        var t = calculadora.calcular(List.of(linea(1190, true), linea(5000, false)), 19.0, true);
        assertThat(t.neto()).isEqualTo(1000);
        assertThat(t.iva()).isEqualTo(190);
        assertThat(t.exento()).isEqualTo(5000);
        assertThat(t.total()).isEqualTo(6190);
    }

    @Test
    @DisplayName("La sobrecarga sin flag equivale a precios netos (facturas)")
    void sobrecargaEquivaleANeto() {
        var dosArg = calculadora.calcular(List.of(linea(10000, true)), 19.0);
        var tresArg = calculadora.calcular(List.of(linea(10000, true)), 19.0, false);
        assertThat(dosArg).isEqualTo(tresArg);
        assertThat(tresArg.neto()).isEqualTo(10000);
        assertThat(tresArg.iva()).isEqualTo(1900);
        assertThat(tresArg.total()).isEqualTo(11900);
    }
}
