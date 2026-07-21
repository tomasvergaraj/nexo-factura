package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Autenticacion de la API REST de boleta electronica: semilla → getToken
 * firmado (XMLDSig enveloped con Reference URI="", el perfil del spec oficial)
 * → token.
 *
 * El token se cachea via {@link TokenCache} y se reutiliza para envios y
 * consultas (recomendacion expresa del spec). Ante un 401 o el texto "NO ESTA
 * AUTENTICADO", el llamador invalida el token que fallo y reintenta una vez.
 * Este token es INDEPENDIENTE del token SOAP del flujo clasico de facturas.
 * La semilla expira en 2 minutos: se pide y usa al vuelo.
 */
@Component
@Profile("prod")
@Slf4j
public class SiiAuthClient implements SiiTokenAuth {

    private final RestClient http;
    private final FirmaElectronica firma;
    private final SiiAmbiente ambiente;
    private final TokenCache cache = new TokenCache();

    public SiiAuthClient(SiiHttp siiHttp, FirmaElectronica firma, AppProperties props) {
        this.http = siiHttp.cliente();
        this.firma = firma;
        this.ambiente = SiiAmbiente.desde(props.sii().ambiente());
    }

    @Override
    public String token() {
        return cache.obtener(this::renovar);
    }

    @Override
    public void invalidar(String tokenFallido) {
        cache.invalidar(tokenFallido);
    }

    private String renovar() {
        String token = obtenerToken(obtenerSemilla());
        log.info("Token SII (boleta, {}) renovado", ambiente);
        return token;
    }

    private String obtenerSemilla() {
        String cuerpo;
        try {
            cuerpo = http.get()
                    .uri(ambiente.apiBoleta() + "/boleta.electronica.semilla")
                    .accept(MediaType.APPLICATION_XML)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new SiiNoDisponibleException("No se pudo obtener la semilla del SII: " + e.getMessage());
        }
        String semilla = SiiXml.textoElemento(cuerpo, "SEMILLA");
        if (semilla == null || semilla.isBlank()) {
            throw new SiiNoDisponibleException(
                    "El SII no entrego semilla (ESTADO=" + SiiXml.textoElemento(cuerpo, "ESTADO") + ")");
        }
        return semilla.trim();
    }

    private String obtenerToken(String semilla) {
        // Estructura exacta del spec oficial; la firma va enveloped con URI="".
        String getToken = "<getToken><item><Semilla>" + semilla + "</Semilla></item></getToken>";
        String firmado = firma.firmarEnveloped(getToken, null);

        String cuerpo;
        try {
            cuerpo = http.post()
                    .uri(ambiente.apiBoleta() + "/boleta.electronica.token")
                    .contentType(MediaType.APPLICATION_XML)
                    .accept(MediaType.APPLICATION_XML)
                    .body(firmado)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new SiiNoDisponibleException("No se pudo obtener el token del SII: " + e.getMessage());
        }
        String token = SiiXml.textoElemento(cuerpo, "TOKEN");
        if (token == null || token.isBlank()) {
            throw new SiiNoDisponibleException("El SII rechazo la solicitud de token (ESTADO="
                    + SiiXml.textoElemento(cuerpo, "ESTADO")
                    + ", GLOSA=" + SiiXml.textoElemento(cuerpo, "GLOSA") + ")");
        }
        return token.trim();
    }
}
