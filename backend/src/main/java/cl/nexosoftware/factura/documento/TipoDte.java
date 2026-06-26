package cl.nexosoftware.factura.documento;

/**
 * Tipos de Documento Tributario Electronico segun codigos del SII.
 * El codigo numerico es el que viaja en el XML (campo TipoDTE).
 */
public enum TipoDte {
    FACTURA_AFECTA(33, "Factura electronica", true),
    FACTURA_EXENTA(34, "Factura no afecta o exenta", false),
    BOLETA_AFECTA(39, "Boleta electronica", true),
    BOLETA_EXENTA(41, "Boleta exenta electronica", false),
    NOTA_DEBITO(56, "Nota de debito electronica", true),
    NOTA_CREDITO(61, "Nota de credito electronica", true);

    private final int codigo;
    private final String descripcion;
    private final boolean afecto;

    TipoDte(int codigo, String descripcion, boolean afecto) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.afecto = afecto;
    }

    public int getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public boolean esAfecto() { return afecto; }

    /**
     * True para boletas (39/41): los precios unitarios vienen con IVA incluido
     * (brutos), por lo que el neto/IVA se desglosan del total afecto en vez de
     * sumar el IVA por encima del neto.
     */
    public boolean preciosBrutos() {
        return this == BOLETA_AFECTA || this == BOLETA_EXENTA;
    }

    public static TipoDte desdeCodigo(int codigo) {
        for (TipoDte t : values()) {
            if (t.codigo == codigo) return t;
        }
        throw new IllegalArgumentException("Codigo de DTE desconocido: " + codigo);
    }
}
