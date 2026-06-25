package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.LineaDetalle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Calculo de totales de un DTE en pesos chilenos (enteros).
 *
 * Reglas: el neto suma las lineas afectas, el exento suma las no afectas, el IVA
 * se obtiene del neto con la tasa vigente y se redondea al peso (half-up), y el
 * total es neto + iva + exento. Es deterministico y sin estado para poder
 * cubrirlo con tests unitarios.
 */
@Component
public class CalculadoraImpuestos {

    public Totales calcular(List<LineaDetalle> lineas, double tasaIva) {
        long neto = 0;
        long exento = 0;
        for (LineaDetalle l : lineas) {
            if (l.isAfecto()) {
                neto += l.getMontoLinea();
            } else {
                exento += l.getMontoLinea();
            }
        }
        long iva = Math.round(neto * (tasaIva / 100.0));
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
