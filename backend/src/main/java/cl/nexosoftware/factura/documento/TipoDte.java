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
     * Nombre del tipo tal como debe rotularse en el recuadro de la representacion
     * impresa: en mayusculas y con la glosa EXACTA que exige el SII (Manual de
     * Muestras Impresas 1.1.4). Distinto de {@link #getDescripcion()} (glosa
     * interna sin la palabra "ELECTRONICA" en la exenta y sin acentos).
     */
    public String nombreImpreso() {
        return switch (this) {
            case FACTURA_AFECTA -> "FACTURA ELECTRÓNICA";
            case FACTURA_EXENTA -> "FACTURA NO AFECTA O EXENTA ELECTRÓNICA";
            case BOLETA_AFECTA  -> "BOLETA ELECTRÓNICA";
            case BOLETA_EXENTA  -> "BOLETA EXENTA ELECTRÓNICA";
            case NOTA_DEBITO    -> "NOTA DE DÉBITO ELECTRÓNICA";
            case NOTA_CREDITO   -> "NOTA DE CRÉDITO ELECTRÓNICA";
        };
    }

    /** Nombre impreso del tipo referenciado por codigo; fallback legible si no esta en el catalogo. */
    public static String nombreImpreso(int codigo) {
        try {
            return desdeCodigo(codigo).nombreImpreso();
        } catch (IllegalArgumentException e) {
            return "DOCUMENTO TIPO " + codigo;
        }
    }

    /**
     * True para boletas (39/41): los precios unitarios vienen con IVA incluido
     * (brutos), por lo que el neto/IVA se desglosan del total afecto en vez de
     * sumar el IVA por encima del neto.
     */
    public boolean preciosBrutos() {
        return this == BOLETA_AFECTA || this == BOLETA_EXENTA;
    }

    /**
     * True para los tipos cuyo CAF caduca (Res. Ex. 58/2017: documentos que dan
     * derecho a credito fiscal IVA — 33/56/61 de este catalogo — vencen a los 6
     * meses de la fecha de autorizacion). Los CAF de boletas y exentas no vencen.
     */
    public boolean cafVence() {
        return this == FACTURA_AFECTA || this == NOTA_DEBITO || this == NOTA_CREDITO;
    }

    public static TipoDte desdeCodigo(int codigo) {
        for (TipoDte t : values()) {
            if (t.codigo == codigo) return t;
        }
        throw new IllegalArgumentException("Codigo de DTE desconocido: " + codigo);
    }
}
