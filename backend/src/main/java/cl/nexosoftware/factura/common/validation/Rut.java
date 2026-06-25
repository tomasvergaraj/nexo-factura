package cl.nexosoftware.factura.common.validation;

/**
 * Validacion del digito verificador de un RUT chileno (modulo 11).
 *
 * <p>Replica bit a bit el algoritmo de {@code validarRut} de
 * {@code frontend/src/lib/format.ts}: normaliza (quita todo lo que no sea
 * digito o K), recorre el cuerpo de derecha a izquierda con multiplos 2..7
 * ciclicos, y deriva el DV del resto de 11 (resto 11 -> '0', resto 10 -> 'K').
 */
public final class Rut {

    private Rut() {}

    /** {@code true} si el RUT tiene un digito verificador correcto. */
    public static boolean esValido(String rut) {
        if (rut == null) {
            return false;
        }
        String limpio = rut.replaceAll("[^0-9kK]", "").toUpperCase();
        if (limpio.length() < 2) {
            return false;
        }
        String cuerpo = limpio.substring(0, limpio.length() - 1);
        String dv = limpio.substring(limpio.length() - 1);
        if (!cuerpo.matches("\\d+")) {
            return false;
        }

        int suma = 0;
        int multiplo = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * multiplo;
            multiplo = multiplo == 7 ? 2 : multiplo + 1;
        }
        int resto = 11 - (suma % 11);
        String dvEsperado = resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
        return dv.equals(dvEsperado);
    }
}
