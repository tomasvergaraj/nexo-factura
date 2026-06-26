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

    private LineaDetalle linea(long monto, boolean afecto, Integer codImpAdic) {
        return LineaDetalle.builder().montoLinea(monto).afecto(afecto).codImpAdic(codImpAdic).build();
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

    // ---- Otros impuestos (P1-6): adicionales y retencion de IVA ----

    private static final int COD_ILA_ANALCOHOLICAS = 27; // adicional 10%
    private static final int COD_IVA_RETENIDO = 15;      // retencion 19%

    @Test
    @DisplayName("Un impuesto adicional (ILA 10%) suma su sobretasa al total")
    void impuestoAdicionalSumaAlTotal() {
        var t = calculadora.calcular(List.of(linea(100000, true, COD_ILA_ANALCOHOLICAS)), 19.0);
        assertThat(t.neto()).isEqualTo(100000);
        assertThat(t.iva()).isEqualTo(19000);
        assertThat(t.impuestosAdicionales()).isEqualTo(10000); // 100000 * 10%
        assertThat(t.ivaRetenido()).isZero();
        assertThat(t.total()).isEqualTo(129000); // 100000 + 19000 + 10000
        assertThat(t.impuestos()).singleElement().satisfies(i -> {
            assertThat(i.codigo()).isEqualTo(27);
            assertThat(i.tasa()).isEqualTo(10.0);
            assertThat(i.esRetencion()).isFalse();
            assertThat(i.base()).isEqualTo(100000);
            assertThat(i.monto()).isEqualTo(10000);
        });
    }

    @Test
    @DisplayName("La base de un mismo codigo se agrega ANTES de redondear (una sola vez)")
    void agregaBasePorCodigoAntesDeRedondear() {
        // Dos lineas con el mismo codigo: base = 3333 + 3334 = 6667; 10% = 666,7 -> 667.
        // Redondear por linea daria round(333,3)+round(333,4)=333+333=666 (mal).
        var t = calculadora.calcular(
                List.of(linea(3333, true, COD_ILA_ANALCOHOLICAS), linea(3334, true, COD_ILA_ANALCOHOLICAS)), 19.0);
        assertThat(t.impuestos()).singleElement().satisfies(i -> {
            assertThat(i.base()).isEqualTo(6667);
            assertThat(i.monto()).isEqualTo(667);
        });
        assertThat(t.impuestosAdicionales()).isEqualTo(667);
    }

    @Test
    @DisplayName("El monto del adicional se redondea half-up al peso")
    void redondeaAdicionalHalfUp() {
        // 1995 * 10% = 199,5 -> 200 (half-up)
        assertThat(calculadora.calcular(List.of(linea(1995, true, COD_ILA_ANALCOHOLICAS)), 19.0)
                .impuestosAdicionales()).isEqualTo(200);
        // 1994 * 10% = 199,4 -> 199
        assertThat(calculadora.calcular(List.of(linea(1994, true, COD_ILA_ANALCOHOLICAS)), 19.0)
                .impuestosAdicionales()).isEqualTo(199);
    }

    @Test
    @DisplayName("La retencion total de IVA (cambio de sujeto) resta el IVA del total")
    void retencionDeIvaRestaDelTotal() {
        var t = calculadora.calcular(List.of(linea(50000, true, COD_IVA_RETENIDO)), 19.0);
        assertThat(t.neto()).isEqualTo(50000);
        assertThat(t.iva()).isEqualTo(9500);
        assertThat(t.ivaRetenido()).isEqualTo(9500); // = IVA de la linea marcada (50000 * 19%)
        assertThat(t.impuestosAdicionales()).isZero();
        assertThat(t.total()).isEqualTo(50000); // 50000 + 9500 - 9500: el emisor no recibe el IVA
    }

    @Test
    @DisplayName("Adicional y retencion en el mismo documento: uno suma y el otro resta")
    void adicionalYRetencionCombinados() {
        var t = calculadora.calcular(
                List.of(linea(100000, true, COD_ILA_ANALCOHOLICAS), linea(50000, true, COD_IVA_RETENIDO)), 19.0);
        assertThat(t.neto()).isEqualTo(150000);
        assertThat(t.iva()).isEqualTo(28500); // 150000 * 19%
        assertThat(t.impuestosAdicionales()).isEqualTo(10000); // 100000 * 10%
        assertThat(t.ivaRetenido()).isEqualTo(9500);           // 50000 * 19%
        assertThat(t.total()).isEqualTo(179000); // 150000 + 28500 + 10000 - 9500
        assertThat(t.impuestos()).hasSize(2);
    }

    @Test
    @DisplayName("Solo las lineas marcadas (y afectas) entran a la base del impuesto")
    void soloLineasMarcadasYAfectas() {
        // Una afecta con codigo, una afecta sin codigo, una exenta: la base es solo la primera.
        var t = calculadora.calcular(
                List.of(linea(100000, true, COD_ILA_ANALCOHOLICAS), linea(40000, true), linea(50000, false)), 19.0);
        assertThat(t.neto()).isEqualTo(140000);
        assertThat(t.exento()).isEqualTo(50000);
        assertThat(t.impuestos()).singleElement().satisfies(i -> assertThat(i.base()).isEqualTo(100000));
        assertThat(t.impuestosAdicionales()).isEqualTo(10000);
        assertThat(t.total()).isEqualTo(140000 + 26600 + 50000 + 10000); // iva = 140000*19% = 26600
    }

    @Test
    @DisplayName("Sin codigos de impuesto: agregados en cero y total igual al actual (regresion)")
    void sinCodigosNoCambiaNada() {
        var t = calculadora.calcular(List.of(linea(10000, true)), 19.0);
        assertThat(t.impuestosAdicionales()).isZero();
        assertThat(t.ivaRetenido()).isZero();
        assertThat(t.impuestos()).isEmpty();
        assertThat(t.total()).isEqualTo(11900);
    }

    @Test
    @DisplayName("En precios brutos (boletas) los codigos se ignoran (defensa en profundidad)")
    void preciosBrutosIgnoraImpuestos() {
        // El servicio ya rechaza codigos en boletas; aqui se verifica que la calculadora
        // no aplica adicionales en la rama bruta aunque la linea traiga un codigo.
        var t = calculadora.calcular(List.of(linea(11900, true, COD_ILA_ANALCOHOLICAS)), 19.0, true);
        assertThat(t.neto()).isEqualTo(10000);
        assertThat(t.iva()).isEqualTo(1900);
        assertThat(t.impuestosAdicionales()).isZero();
        assertThat(t.impuestos()).isEmpty();
        assertThat(t.total()).isEqualTo(11900);
    }

    @Test
    @DisplayName("desglosarImpuestos es determinista y ordena por codigo ascendente")
    void desgloseOrdenadoPorCodigo() {
        var desglose = CalculadoraImpuestos.desglosarImpuestos(List.of(
                linea(50000, true, COD_IVA_RETENIDO),            // 15
                linea(100000, true, COD_ILA_ANALCOHOLICAS)));    // 27
        assertThat(desglose).extracting(CalculadoraImpuestos.ImpuestoCalculado::codigo)
                .containsExactly(15, 27); // orden ascendente por codigo
    }
}
