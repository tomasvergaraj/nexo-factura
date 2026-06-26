package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.math.BigDecimal;

/**
 * Adapta un {@code double} a su forma decimal "plana" (sin notacion cientifica)
 * para que el XML cumpla {@code xs:decimal}.
 *
 * Por defecto JAXB marshalla un double via {@code Double.toString}, que para
 * magnitudes &gt;= 1e7 o &lt; 1e-3 emite notacion cientifica (ej "1.0E7"),
 * lexicalmente invalida como {@code xs:decimal}: la validacion XSD pre-firma
 * rechazaria un DTE legitimo con una cantidad grande. {@code BigDecimal.valueOf(d)
 * .toPlainString()} produce siempre la forma plana ("10000000", "0.00010", "1.0").
 * Nota: el printer xs:decimal de JAXB usa {@code BigDecimal.toString()} (NO
 * {@code toPlainString()}), por lo que cambiar el campo a BigDecimal no bastaria;
 * el adaptador es la via correcta.
 */
public class PlainDecimalAdapter extends XmlAdapter<String, Double> {

    @Override
    public Double unmarshal(String v) {
        return v == null ? null : Double.valueOf(v);
    }

    @Override
    public String marshal(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).toPlainString();
    }
}
