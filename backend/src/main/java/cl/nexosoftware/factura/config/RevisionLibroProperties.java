package cl.nexosoftware.factura.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de la revision automatica de libros IECV (prefijo
 * {@code app.libro.revision-auto}).
 *
 * El job PREPARA el libro del mes anterior (firma + validacion, sin postear al
 * SII) y deja un marcador para que el usuario lo envie. Corre desde {@code dia}
 * del mes en adelante: unos dias de margen para que se terminen de registrar las
 * compras del periodo antes de avisar. La expresion cron va en
 * {@code app.libro.revision-auto.cron} (se referencia directo en {@code @Scheduled}).
 */
@ConfigurationProperties(prefix = "app.libro.revision-auto")
public record RevisionLibroProperties(boolean enabled, int dia) {

    public RevisionLibroProperties {
        // Dia del mes valido para todos los meses (1..28); fuera de rango, dia 5.
        if (dia < 1 || dia > 28) dia = 5;
    }
}
