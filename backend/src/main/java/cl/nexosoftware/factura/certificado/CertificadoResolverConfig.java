package cl.nexosoftware.factura.certificado;

import cl.nexosoftware.factura.config.AppProperties;
import cl.nexosoftware.factura.seguridad.CifradorSecretos;
import cl.nexosoftware.factura.tributario.CertificadoResolver;
import cl.nexosoftware.factura.tributario.FirmaModo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Cablea el {@link CertificadoResolver} del perfil prod segun
 * {@code app.sii.firma-modo}. Es una PROPERTY y no un perfil: el ambiente de
 * certificacion corre con perfil prod + modo GLOBAL (docker-compose.cert.yml
 * intacto) y la produccion multi-tenant con el mismo perfil + POR_EMPRESA.
 */
@Configuration
@Profile("prod")
@Slf4j
public class CertificadoResolverConfig {

    @Bean
    public CertificadoResolver certificadoResolver(AppProperties props,
                                                   CertificadoEmpresaRepository repository,
                                                   CifradorSecretos cifrador) {
        FirmaModo modo = FirmaModo.desde(props.sii().firmaModo());
        log.info("Modo de firma SII: {}", modo);
        return switch (modo) {
            case GLOBAL -> CertificadoResolverGlobal.cargar(props);
            case POR_EMPRESA -> new CertificadoResolverPorEmpresa(repository, cifrador);
        };
    }
}
