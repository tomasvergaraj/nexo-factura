package cl.nexosoftware.factura.tributario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cache de token del SII particiona por clave (huella del certificado): un
 * token por identidad, renovacion serializada, e invalidacion que solo descarta
 * el token que fallo.
 */
class TokenCacheTest {

    @Test
    @DisplayName("misma clave: el token se cachea y no se vuelve a renovar")
    void mismaClaveCacheaToken() {
        TokenCache cache = new TokenCache();
        AtomicInteger renovaciones = new AtomicInteger();

        String t1 = cache.obtener("huellaA", () -> "TOK-" + renovaciones.incrementAndGet());
        String t2 = cache.obtener("huellaA", () -> "TOK-" + renovaciones.incrementAndGet());

        assertThat(t1).isEqualTo("TOK-1");
        assertThat(t2).isEqualTo("TOK-1");
        assertThat(renovaciones.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("claves distintas: cada certificado tiene su propia sesion")
    void clavesDistintasTokensIndependientes() {
        TokenCache cache = new TokenCache();

        String a = cache.obtener("huellaA", () -> "TOKEN-A");
        String b = cache.obtener("huellaB", () -> "TOKEN-B");

        assertThat(a).isEqualTo("TOKEN-A");
        assertThat(b).isEqualTo("TOKEN-B");
    }

    @Test
    @DisplayName("invalidar descarta solo la clave dada y fuerza su renovacion")
    void invalidarForzaRenovacion() {
        TokenCache cache = new TokenCache();
        AtomicInteger n = new AtomicInteger();

        String primero = cache.obtener("huellaA", () -> "TOK-" + n.incrementAndGet());
        cache.invalidar("huellaA", primero);
        String segundo = cache.obtener("huellaA", () -> "TOK-" + n.incrementAndGet());

        assertThat(primero).isEqualTo("TOK-1");
        assertThat(segundo).isEqualTo("TOK-2");
    }

    @Test
    @DisplayName("invalidar con un token que ya no es el vigente no descarta el nuevo")
    void invalidarNoPisaTokenRenovado() {
        TokenCache cache = new TokenCache();
        AtomicInteger n = new AtomicInteger();

        String viejo = cache.obtener("huellaA", () -> "TOK-" + n.incrementAndGet());
        // Otro hilo ya renovo: invalidamos el token viejo, que ya no es el vigente.
        cache.invalidar("huellaA", "TOK-viejo-distinto");
        String vigente = cache.obtener("huellaA", () -> "TOK-" + n.incrementAndGet());

        assertThat(viejo).isEqualTo("TOK-1");
        assertThat(vigente).isEqualTo("TOK-1"); // no se renovo: el cache seguia vigente
    }
}
