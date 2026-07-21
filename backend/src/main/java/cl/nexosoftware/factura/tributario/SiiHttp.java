package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP compartido de las llamadas al SII: timeouts explicitos y el
 * User-Agent estilo navegador que exige su plataforma (el WAF del SII bloquea
 * los agentes por defecto de las librerias; el formato es el recomendado por su
 * propio spec). Sin reintentos automaticos: la contingencia de envio ya maneja
 * la indisponibilidad a nivel de documento.
 */
@Component
@Profile("prod")
public class SiiHttp {

    private final RestClient cliente;

    public SiiHttp(AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.cliente = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, props.sii().userAgent())
                .build();
    }

    public RestClient cliente() {
        return cliente;
    }
}
