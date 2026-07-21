package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.SiiNoDisponibleException;
import cl.nexosoftware.factura.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Autenticacion del canal CLASICO de DTE (facturas/notas): semilla por
 * {@code CrSeed.jws} y token por {@code GetTokenFromSeed.jws}, ambos SOAP
 * rpc/encoded en maullin (cert) / palena (prod).
 *
 * Este token es INDEPENDIENTE del de la API REST de boleta (lo dice el
 * instructivo del SII): por eso este cliente existe aparte de SiiAuthClient.
 * La semilla dura 2 minutos (se pide y firma al vuelo); la vigencia del token
 * no esta documentada oficialmente (consenso ~60 min), asi que se cachea via
 * {@link TokenCache} y se renueva ante STATUS 5 del upload o estados 001-003
 * de la consulta (se invalida el token que fallo y se reintenta una vez).
 */
@Component
@Profile("prod")
@Slf4j
public class SiiDteAuthClient implements SiiTokenAuth {

    private final SiiSoap soap;
    private final FirmaElectronica firma;
    private final SiiAmbiente ambiente;
    private final TokenCache cache = new TokenCache();

    public SiiDteAuthClient(SiiSoap soap, FirmaElectronica firma, AppProperties props) {
        this.soap = soap;
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
        log.info("Token SII (DTE clasico, {}) renovado", ambiente);
        return token;
    }

    private String obtenerSemilla() {
        String respuesta = soap.invocar(ambiente.hostDte() + "/DTEWS/CrSeed.jws", "getSeed");
        String semilla = soap.textoElemento(respuesta, "SEMILLA");
        if (semilla == null || semilla.isBlank()) {
            throw new SiiNoDisponibleException("El SII no entrego semilla clasica (ESTADO="
                    + soap.textoElemento(respuesta, "ESTADO") + ")");
        }
        return semilla.trim();
    }

    private String obtenerToken(String semilla) {
        String getToken = "<getToken><item><Semilla>" + semilla + "</Semilla></item></getToken>";
        String firmado = firma.firmarEnveloped(getToken, null);

        String respuesta = soap.invocar(ambiente.hostDte() + "/DTEWS/GetTokenFromSeed.jws",
                "getToken", new String[]{"pszXml", firmado});
        String token = soap.textoElemento(respuesta, "TOKEN");
        if (token == null || token.isBlank()) {
            throw new SiiNoDisponibleException("El SII rechazo el token clasico (ESTADO="
                    + soap.textoElemento(respuesta, "ESTADO")
                    + ", GLOSA=" + soap.textoElemento(respuesta, "GLOSA") + ")");
        }
        return token.trim();
    }
}
