package cl.nexosoftware.factura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI nexoFacturaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nexo Factura API")
                        .version("0.1.0")
                        .description("API de facturacion electronica (DTE) para el SII de Chile.")
                        .contact(new Contact().name("Nexo Software SpA").email("contacto@nexosoftware.cl"))
                        .license(new License().name("Propietaria")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
