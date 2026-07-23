package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el riesgo de arquitectura del perfil prod: con {@code @Profile("prod")}
 * el contexto LEVANTA y se cablean los beans reales esqueleto
 * ({@link FirmaElectronicaProd} / {@link SiiGatewayProd}) en lugar de los stubs,
 * aunque sus operaciones reales todavia lancen {@code UnsupportedOperationException}.
 *
 * <p>El {@code JwtSecretValidator} (solo perfil prod) aborta el arranque si
 * APP_JWT_SECRET no es seguro; por eso se provee aqui un secret valido
 * (&gt;=32 bytes UTF-8 y distinto del secret de desarrollo conocido) via
 * {@link DynamicPropertySource}.
 */
@ActiveProfiles("prod")
class PerfilProdContextoIT extends AbstractIntegrationTest {

    /** Secret valido para prod: distinto del default de dev y con >=32 bytes UTF-8. */
    private static final String SECRET_PROD_TEST =
            "test-prod-secret-distinto-del-de-dev-9876543210-abcdef";

    @DynamicPropertySource
    static void propiedadesProd(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> SECRET_PROD_TEST);
        // El resolver GLOBAL y EnvioBoletaGenerator son fail-fast en prod: el
        // contexto solo levanta con un certificado legible y una FchResol valida.
        registry.add("app.sii.certificado-path", PerfilProdContextoIT::rutaCertPrueba);
        registry.add("app.sii.certificado-password", () -> "test123");
        registry.add("app.sii.fch-resol", () -> "2026-05-14");
    }

    private static String rutaCertPrueba() {
        try {
            return new org.springframework.core.io.ClassPathResource("sii/cert_prueba.p12")
                    .getFile().getAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Falta el fixture sii/cert_prueba.p12", e);
        }
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("perfil prod: el contexto levanta y se cablea FirmaElectronicaProd")
    void firmaElectronicaEsLaImplementacionProd() {
        FirmaElectronica firma = context.getBean(FirmaElectronica.class);
        assertThat(firma).isInstanceOf(FirmaElectronicaProd.class);
    }

    @Test
    @DisplayName("perfil prod: el contexto levanta y se cablea SiiGatewayProd")
    void siiGatewayEsLaImplementacionProd() {
        SiiGateway gateway = context.getBean(SiiGateway.class);
        assertThat(gateway).isInstanceOf(SiiGatewayProd.class);
    }
}
