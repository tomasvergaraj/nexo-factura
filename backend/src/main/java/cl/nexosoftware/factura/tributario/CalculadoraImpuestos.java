package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.LineaDetalle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Calculo de totales de un DTE en pesos chilenos (enteros).
 *
 * Para facturas (precios netos): el neto suma las lineas afectas, el exento suma
 * las no afectas, el IVA se obtiene del neto con la tasa vigente y se redondea al
 * peso (half-up), y el total es neto + iva + exento.
 *
 * Para boletas (precios brutos, IVA incluido): las lineas afectas ya traen el IVA
 * dentro, asi que el neto se desglosa del total afecto (neto = total/(1+tasa)) y el
 * IVA es la diferencia (iva = totalAfecto - neto), garantizando neto+iva == bruto
 * exactamente sin un segundo redondeo. Es deterministico y sin estado para poder
 * cubrirlo con tests unitarios.
 */
@Component
public class CalculadoraImpuestos {

    /** Calculo para precios netos (facturas, notas). Equivale a {@code calcular(lineas, tasaIva, false)}. */
    public Totales calcular(List<LineaDetalle> lineas, double tasaIva) {
        return calcular(lineas, tasaIva, false);
    }

    public Totales calcular(List<LineaDetalle> lineas, double tasaIva, boolean preciosBrutos) {
        long afecto = 0;
        long exento = 0;
        for (LineaDetalle l : lineas) {
            if (l.isAfecto()) {
                afecto += l.getMontoLinea();
            } else {
                exento += l.getMontoLinea();
            }
        }
        long neto;
        long iva;
        if (preciosBrutos) {
            // El total afecto ya incluye IVA: se desglosa el neto y el IVA es el resto.
            neto = Math.round(afecto / (1.0 + tasaIva / 100.0));
            iva = afecto - neto;
        } else {
            neto = afecto;
            iva = Math.round(neto * (tasaIva / 100.0));
        }
        long total = neto + iva + exento;
        return new Totales(neto, exento, tasaIva, iva, total);
    }

    /** Monto de una linea = cantidad * precioUnitario - descuento (>= 0). */
    public long montoLinea(double cantidad, long precioUnitario, long descuento) {
        long bruto = Math.round(cantidad * precioUnitario);
        return Math.max(0, bruto - descuento);
    }

    public record Totales(long neto, long exento, double tasaIva, long iva, long total) {}
}
