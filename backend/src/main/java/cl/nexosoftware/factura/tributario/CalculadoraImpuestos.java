package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.LineaDetalle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

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
 * exactamente sin un segundo redondeo.
 *
 * Otros impuestos (P1-6, solo precios netos): cada linea afecta puede referenciar
 * un codigo del catalogo {@link TipoImpuesto}. La base de cada impuesto se AGREGA
 * por codigo (suma del monto neto de las lineas marcadas) y se redondea UNA sola
 * vez (half-up) para casar 1:1 con el bloque ImptoReten del XML. Los adicionales
 * suman al total; la retencion de IVA lo resta. Es deterministico y sin estado
 * para poder cubrirlo con tests unitarios.
 */
@Component
public class CalculadoraImpuestos {

    /** Calculo para precios netos (facturas, notas). Equivale a {@code calcular(lineas, tasaIva, false)}. */
    public Totales calcular(List<LineaDetalle> lineas, double tasaIva) {
        return calcular(lineas, tasaIva, false);
    }

    public Totales calcular(List<LineaDetalle> lineas, double tasaIva, boolean preciosBrutos) {
        return calcular(lineas, tasaIva, preciosBrutos, null);
    }

    /**
     * Calculo con descuento global porcentual sobre las lineas AFECTAS
     * (DscRcgGlobal TpoMov=D TpoValor=%): el descuento se redondea UNA vez
     * sobre el afecto agregado, el neto queda rebajado y el IVA se calcula
     * sobre ese neto rebajado (regla del SII: MntNeto = detalle afecto -
     * descuentos globales). Solo aplica a documentos de precios netos.
     */
    public Totales calcular(List<LineaDetalle> lineas, double tasaIva, boolean preciosBrutos,
                            Double descuentoGlobalPct) {
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
        long descuentoGlobal = 0;
        if (preciosBrutos) {
            // El total afecto ya incluye IVA: se desglosa el neto y el IVA es el resto.
            neto = Math.round(afecto / (1.0 + tasaIva / 100.0));
            iva = afecto - neto;
        } else {
            if (descuentoGlobalPct != null) {
                descuentoGlobal = Math.round(afecto * (descuentoGlobalPct / 100.0));
            }
            neto = afecto - descuentoGlobal;
            iva = Math.round(neto * (tasaIva / 100.0));
        }

        // Otros impuestos: solo en documentos de precios netos (facturas/notas). En
        // boletas el desglose es vacio (la regla de negocio ya rechaza codigos alli).
        List<ImpuestoCalculado> impuestos = preciosBrutos ? List.of() : desglosarImpuestos(lineas);
        long adicionales = 0;
        long retenido = 0;
        for (ImpuestoCalculado imp : impuestos) {
            if (imp.esRetencion()) {
                retenido += imp.monto();
            } else {
                adicionales += imp.monto();
            }
        }

        long total = neto + iva + exento + adicionales - retenido;
        return new Totales(neto, exento, tasaIva, iva, adicionales, retenido, total, descuentoGlobal, impuestos);
    }

    /**
     * Desglosa los otros impuestos de un conjunto de lineas: agrega la base (monto
     * neto) por codigo de impuesto sobre las lineas AFECTAS marcadas y redondea el
     * monto una sola vez por codigo (half-up). Devuelve la lista ordenada por codigo
     * (determinista). Es {@code static} y puro para que el generador de XML y el
     * mapper de respuesta deriven exactamente el mismo desglose sin recalcular logica.
     */
    public static List<ImpuestoCalculado> desglosarImpuestos(List<LineaDetalle> lineas) {
        // TreeMap: orden por codigo ascendente -> XML/tests deterministas.
        TreeMap<Integer, Long> basePorCodigo = new TreeMap<>();
        for (LineaDetalle l : lineas) {
            Integer cod = l.getCodImpAdic();
            if (cod == null || !l.isAfecto()) {
                continue;
            }
            basePorCodigo.merge(cod, l.getMontoLinea(), Long::sum);
        }
        List<ImpuestoCalculado> out = new ArrayList<>();
        for (var e : basePorCodigo.entrySet()) {
            TipoImpuesto t = TipoImpuesto.desdeCodigo(e.getKey());
            long base = e.getValue();
            long monto = Math.round(base * (t.getTasa() / 100.0));
            out.add(new ImpuestoCalculado(t.getCodigo(), t.getNombre(), t.getTasa(), t.esRetencion(), base, monto));
        }
        return out;
    }

    /** Monto de una linea = cantidad * precioUnitario - descuento (>= 0). */
    public long montoLinea(double cantidad, long precioUnitario, long descuento) {
        long bruto = Math.round(cantidad * precioUnitario);
        return Math.max(0, bruto - descuento);
    }

    /**
     * Descuento en pesos derivado de un porcentaje por linea (DescuentoPct):
     * round(bruto * pct / 100), redondeado UNA vez sobre el bruto de la linea.
     */
    public long descuentoPorcentual(double cantidad, long precioUnitario, double pct) {
        long bruto = Math.round(cantidad * precioUnitario);
        return Math.round(bruto * (pct / 100.0));
    }

    public record Totales(long neto, long exento, double tasaIva, long iva,
                          long impuestosAdicionales, long ivaRetenido, long total,
                          long descuentoGlobal, List<ImpuestoCalculado> impuestos) {}

    /** Desglose de un otro-impuesto por codigo: base agregada y monto redondeado. */
    public record ImpuestoCalculado(int codigo, String nombre, double tasa, boolean esRetencion,
                                    long base, long monto) {}
}
