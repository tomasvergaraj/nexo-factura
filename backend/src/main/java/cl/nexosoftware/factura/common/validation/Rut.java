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

    /**
     * Devuelve el RUT en forma canonica {@code NNNNNNNN-D} (sin puntos, DV en
     * mayuscula). Asi se almacena siempre normalizado, de modo que la emision del
     * DTE (cuyo XSD exige el formato sin puntos) y la deduplicacion por la
     * restriccion unica no dependan del formato con el que el usuario lo escribio.
     * Para entradas demasiado cortas devuelve el valor tal cual (lo rechazara
     * {@code @RutValido} con un 400 antes de llegar aqui).
     */
    public static String normalizar(String rut) {
        if (rut == null) {
            return null;
        }
        String limpio = rut.replaceAll("[^0-9kK]", "").toUpperCase();
        if (limpio.length() < 2) {
            return rut;
        }
        return limpio.substring(0, limpio.length() - 1) + "-" + limpio.substring(limpio.length() - 1);
    }

    /**
     * Devuelve el RUT con separador de miles para la representacion impresa:
     * {@code 78397017-1} -> {@code 78.397.017-1}. El SII lo exige asi en las
     * muestras impresas (Manual 1.2); el validador de "Upload de Muestras
     * Impresas" rechaza el PDF si el RUT del CAF no aparece con este formato.
     * Entradas demasiado cortas se devuelven tal cual.
     */
    public static String formatear(String rut) {
        String canonico = normalizar(rut);
        if (canonico == null || canonico.length() < 2 || !canonico.contains("-")) {
            return rut;
        }
        int guion = canonico.lastIndexOf('-');
        String cuerpo = canonico.substring(0, guion);
        String dv = canonico.substring(guion + 1);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            sb.append(cuerpo.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                sb.append('.');
            }
        }
        return sb.reverse().append('-').append(dv).toString();
    }
}
