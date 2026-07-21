package cl.nexosoftware.factura.tributario;

/**
 * RUT separado en numero y digito verificador, el formato que piden los
 * endpoints de upload/consulta del SII. (No confundir con la anotacion de
 * validacion {@code common.validation.Rut}: esto es solo el split "12345678-9".)
 */
record Rut(String numero, String dv) {

    static Rut de(String rut) {
        int guion = rut.lastIndexOf('-');
        if (guion <= 0) {
            throw new IllegalStateException("RUT sin guion: " + rut);
        }
        return new Rut(rut.substring(0, guion), rut.substring(guion + 1).toUpperCase());
    }
}
