package cl.nexosoftware.factura.tributario;

/**
 * Datos de contacto (opcionales) de la caratula de los acuses de intercambio:
 * {@code NmbContacto}/{@code FonoContacto}/{@code MailContacto} en
 * RespuestaDTE y EnvioRecibos. Cualquier campo puede ser null (se omite).
 */
public record Contacto(String nombre, String fono, String mail) {

    public static final Contacto VACIO = new Contacto(null, null, null);
}
