package cl.nexosoftware.factura.tributario;

/**
 * Catalogo REPRESENTATIVO de otros impuestos del DTE: impuestos adicionales
 * (sobretasas que suben el total) y la retencion de IVA por cambio de sujeto
 * (que reduce lo que recibe el emisor). El codigo numerico es el que viaja en el
 * XML como {@code TipoImp} (bloque ImptoReten) y como {@code CodImpAdic} en la
 * linea.
 *
 * NO es la tabla tributaria oficial ni exhaustiva del SII: es un subconjunto
 * curado, verificable sin certificado/CAF reales, espejo del enum del frontend
 * ({@code CATALOGO_IMPUESTOS}). Las tasas vigentes pueden cambiar por ley; aqui se
 * fijan como dato representativo. Notas de fidelidad:
 *  - La retencion de IVA total (cambio de sujeto, codigo 15) en el SII real opera
 *    sobre la Factura de Compra (codigo 45, fuera del enum {@code TipoDte} actual);
 *    aqui se modela de forma representativa sobre facturas/notas de venta afectas.
 *  - El DTE NO tiene un elemento {@code IVARetTotal}: tanto adicionales como
 *    retenciones viajan como bloques {@code ImptoReten} (TipoImp/TasaImp/MontoImp),
 *    igual que en el esquema oficial. El signo (suma/resta) lo determina
 *    {@link #esRetencion()}.
 */
public enum TipoImpuesto {
    SUNTUARIO(23, "Impuesto adicional articulos suntuarios (oro, joyas, pieles)", 15.0, false),
    ILA_DESTILADOS(24, "ILA licores, piscos, whisky, aguardientes y destilados", 31.5, false),
    ILA_VINOS(25, "ILA vinos, espumosos, champana, chichas y sidras", 20.5, false),
    ILA_CERVEZAS(26, "ILA cervezas y otras bebidas alcoholicas", 20.5, false),
    ILA_ANALCOHOLICAS(27, "ILA bebidas analcoholicas y minerales", 10.0, false),
    ILA_AZUCARADAS(271, "ILA bebidas analcoholicas con alto contenido de azucar", 18.0, false),
    IVA_RETENIDO_TOTAL(15, "IVA retenido total (cambio de sujeto)", 19.0, true);

    private final int codigo;
    private final String nombre;
    private final double tasa;
    private final boolean retencion;

    TipoImpuesto(int codigo, String nombre, double tasa, boolean retencion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.tasa = tasa;
        this.retencion = retencion;
    }

    public int getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public double getTasa() { return tasa; }

    /** True si el impuesto se RESTA del total (retencion); false si lo suma (adicional). */
    public boolean esRetencion() { return retencion; }

    public static boolean existe(int codigo) {
        for (TipoImpuesto t : values()) {
            if (t.codigo == codigo) return true;
        }
        return false;
    }

    public static TipoImpuesto desdeCodigo(int codigo) {
        for (TipoImpuesto t : values()) {
            if (t.codigo == codigo) return t;
        }
        throw new IllegalArgumentException("Codigo de impuesto desconocido: " + codigo);
    }
}
