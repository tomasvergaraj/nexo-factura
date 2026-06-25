package cl.nexosoftware.factura.documento;

/** Codigo de referencia para notas de credito/debito (campo CodRef del SII). */
public enum TipoReferencia {
    ANULA_DOCUMENTO(1, "Anula documento de referencia"),
    CORRIGE_TEXTO(2, "Corrige texto del documento de referencia"),
    CORRIGE_MONTO(3, "Corrige montos");

    private final int codigo;
    private final String descripcion;

    TipoReferencia(int codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public int getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
}
