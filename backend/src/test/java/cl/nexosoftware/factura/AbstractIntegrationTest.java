package cl.nexosoftware.factura;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base para tests de integracion. Levanta un PostgreSQL real con Testcontainers
 * y deja que Flyway construya el esquema, de modo que el comportamiento de
 * bloqueos y transacciones sea el mismo que en produccion.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /**
     * Contenedor SINGLETON: arranca una vez por JVM y no se para entre clases.
     *
     * Antes esto era un {@code @Container} de {@code @Testcontainers}, que para el
     * contenedor en el {@code afterAll} de CADA clase. Pero el contexto de Spring
     * se cachea y se reutiliza en la clase siguiente, asi que su datasource
     * quedaba apuntando a un PostgreSQL ya muerto: la segunda clase en adelante
     * moria entera con "Could not open JPA EntityManager for transaction" tras
     * agotar el timeout de Hikari. El patron singleton (el que documenta
     * Testcontainers para suites con contexto compartido) empareja el ciclo de
     * vida del contenedor con el de la JVM, que es justo el del contexto
     * cacheado. Al terminar la JVM lo recoge Ryuk.
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final AtomicInteger SECUENCIA_RUT = new AtomicInteger();

    /**
     * RUT unico por invocacion, para sembrar empresas sin chocar con
     * {@code empresa_rut_key}.
     *
     * Los ITs usaban {@code "91000000-" + random(0,9)}: NUEVE valores posibles para
     * decenas de siembras sobre una base compartida, asi que las colisiones eran
     * practicamente seguras. Va sin digito verificador valido a proposito: se
     * inserta por repositorio, donde no corre la validacion de {@code @RutValido}
     * (esa vive en los DTO de entrada).
     */
    protected static String rutUnicoDeTest() {
        return "9%07d-0".formatted(SECUENCIA_RUT.incrementAndGet());
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
