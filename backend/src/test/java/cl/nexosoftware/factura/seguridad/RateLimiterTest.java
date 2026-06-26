package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.auth.RateLimiter;
import cl.nexosoftware.factura.common.exception.DemasiadasPeticionesException;
import cl.nexosoftware.factura.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test unitario del rate limiter (sin Spring), con reloj mutable inyectado. */
class RateLimiterTest {

    private static final String IP = "1.2.3.4";

    private RelojMutable reloj;

    @BeforeEach
    void preparar() {
        reloj = new RelojMutable(Instant.parse("2026-06-26T12:00:00Z"));
    }

    /** maxEmail intentos email, maxIp intentos IP, ventana 60s, bloqueo 60s. */
    private RateLimiter limiter(int maxEmail, int maxIp) {
        return new RateLimiter(new RateLimitProperties(true, maxEmail, maxIp, 60, 60, 1000), reloj);
    }

    @Test
    @DisplayName("permite hasta el maximo y bloquea al alcanzarlo")
    void bloqueaAlAlcanzarElMaximo() {
        RateLimiter rl = limiter(3, 100);
        rl.registrarFallo("a@x.cl", IP);
        rl.registrarFallo("a@x.cl", IP);
        assertThatCode(() -> rl.verificar("a@x.cl", IP)).doesNotThrowAnyException(); // 2 < 3
        rl.registrarFallo("a@x.cl", IP); // 3 -> bloqueado
        assertThatThrownBy(() -> rl.verificar("a@x.cl", IP))
                .isInstanceOf(DemasiadasPeticionesException.class);
    }

    @Test
    @DisplayName("el bloqueo es independiente por clave (email)")
    void independientePorEmail() {
        RateLimiter rl = limiter(3, 100);
        for (int i = 0; i < 3; i++) rl.registrarFallo("a@x.cl", IP);
        assertThatThrownBy(() -> rl.verificar("a@x.cl", IP))
                .isInstanceOf(DemasiadasPeticionesException.class);
        assertThatCode(() -> rl.verificar("b@x.cl", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("se reinicia al avanzar el reloj mas alla del bloqueo")
    void seReiniciaTrasLaVentana() {
        RateLimiter rl = limiter(3, 100);
        for (int i = 0; i < 3; i++) rl.registrarFallo("a@x.cl", IP);
        assertThatThrownBy(() -> rl.verificar("a@x.cl", IP))
                .isInstanceOf(DemasiadasPeticionesException.class);
        reloj.avanzar(Duration.ofSeconds(61));
        assertThatCode(() -> rl.verificar("a@x.cl", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un login exitoso reinicia el contador de ese email")
    void exitoReiniciaEmail() {
        RateLimiter rl = limiter(3, 100);
        rl.registrarFallo("a@x.cl", IP);
        rl.registrarFallo("a@x.cl", IP);
        rl.registrarExito("a@x.cl"); // reinicia el cubo de email
        rl.registrarFallo("a@x.cl", IP);
        rl.registrarFallo("a@x.cl", IP);
        assertThatCode(() -> rl.verificar("a@x.cl", IP)).doesNotThrowAnyException(); // 2 < 3 tras reset
        rl.registrarFallo("a@x.cl", IP);
        assertThatThrownBy(() -> rl.verificar("a@x.cl", IP))
                .isInstanceOf(DemasiadasPeticionesException.class);
    }

    @Test
    @DisplayName("solo cuenta fallos: verificar repetido no consume presupuesto")
    void verificarNoConsume() {
        RateLimiter rl = limiter(3, 100);
        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> rl.verificar("a@x.cl", IP)).doesNotThrowAnyException();
        }
        rl.registrarFallo("a@x.cl", IP);
        rl.registrarFallo("a@x.cl", IP);
        assertThatCode(() -> rl.verificar("a@x.cl", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el cubo por IP bloquea independientemente del email")
    void bloqueaPorIp() {
        RateLimiter rl = limiter(100, 3);
        for (int i = 0; i < 3; i++) rl.registrarFalloIp(IP);
        assertThatThrownBy(() -> rl.verificarIp(IP))
                .isInstanceOf(DemasiadasPeticionesException.class);
    }

    @Test
    @DisplayName("retryAfter refleja el bloqueo configurado")
    void retryAfterValido() {
        RateLimiter rl = limiter(3, 100);
        for (int i = 0; i < 3; i++) rl.registrarFallo("a@x.cl", IP);
        assertThatThrownBy(() -> rl.verificar("a@x.cl", IP))
                .isInstanceOfSatisfying(DemasiadasPeticionesException.class,
                        ex -> assertThat(ex.getRetryAfterSegundos()).isBetween(1L, 60L));
    }

    @Test
    @DisplayName("bajo concurrencia no se pierden conteos y termina bloqueando")
    void concurrenciaBloquea() throws InterruptedException {
        RateLimiter rl = limiter(3, 1000);
        int hilos = 13;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch partida = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);
        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                try {
                    partida.await();
                    rl.registrarFallo("a@x.cl", IP);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            });
        }
        partida.countDown();
        assertThat(fin.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThatThrownBy(() -> rl.verificar("a@x.cl", IP))
                .isInstanceOf(DemasiadasPeticionesException.class);
    }

    @Test
    @DisplayName("deshabilitado: nunca bloquea")
    void deshabilitadoNoBloquea() {
        RateLimiter rl = new RateLimiter(new RateLimitProperties(false, 1, 1, 60, 60, 1000), reloj);
        for (int i = 0; i < 10; i++) rl.registrarFallo("a@x.cl", IP);
        assertThatCode(() -> rl.verificar("a@x.cl", IP)).doesNotThrowAnyException();
    }

    /** Reloj mutable para avanzar el tiempo en los tests. */
    private static final class RelojMutable extends Clock {
        private Instant instante;

        RelojMutable(Instant inicio) { this.instante = inicio; }

        void avanzar(Duration d) { this.instante = instante.plus(d); }

        @Override public Instant instant() { return instante; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
