package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.config.AppProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobre EnvioBOLETA: caratula fiel (RutReceptor SII, NroResol 0, FchResol del
 * fallback de entorno, RutEnvia del certificado), DTE embebido verbatim, firma
 * del SetDTE y — la garantia central — el sobre completo VALIDA contra
 * EnvioBOLETA_v11.xsd.
 */
class EnvioBoletaGeneratorTest {

    private static final Clock RELOJ_FIJO =
            Clock.fixed(Instant.parse("2026-06-26T18:03:21Z"), ZoneId.of("America/Santiago"));

    private static EnvioBoletaGenerator generador;
    private static CertificadoResolver certificadoResolver;
    private static AppProperties props;

    @BeforeAll
    static void inicializar() {
        props = new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", "GLOBAL", "sii/cert_prueba.p12", "test123", null,
                "2026-05-14", 0, "Mozilla/4.0 (compatible; PROG 1.0)"), null);
        certificadoResolver = TestCertificados.resolver();
        generador = new EnvioBoletaGenerator(new FirmaElectronicaProd(certificadoResolver),
                new DteXmlValidator(true), certificadoResolver, TestResoluciones.deEntorno(props),
                props, RELOJ_FIJO);
    }

    private SiiGateway.EnvioSii envio() {
        String dte = DteFixtures.xmlFirmado(DteFixtures.boletaAfecta(11900L));
        return new SiiGateway.EnvioSii(1L, dte, 39, 1L, DteFixtures.RUT_EMISOR);
    }

    @Test
    @DisplayName("el sobre firmado valida contra EnvioBOLETA_v11.xsd")
    void sobreValidaContraElEsquema() {
        assertThatCode(() -> generador.generar(envio())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("caratula fiel: RutReceptor del SII, resolucion de config y RutEnvia del certificado")
    void caratulaFiel() {
        String sobre = generador.generar(envio());
        assertThat(sobre)
                .contains("<RutEmisor>76543210-9</RutEmisor>")
                .contains("<RutEnvia>11111111-1</RutEnvia>")
                .contains("<RutReceptor>60803000-K</RutReceptor>")
                .contains("<FchResol>2026-05-14</FchResol>")
                .contains("<NroResol>0</NroResol>")
                .contains("<TmstFirmaEnv>2026-06-26T14:03:21</TmstFirmaEnv>")
                .contains("<SubTotDTE><TpoDTE>39</TpoDTE><NroDTE>1</NroDTE></SubTotDTE>");
    }

    @Test
    @DisplayName("el contenido del DTE va byte-identico dentro del sobre, con 2 firmas")
    void dteVerbatimYDosFirmas() {
        SiiGateway.EnvioSii envio = envio();
        String sobre = generador.generar(envio);

        // Al re-serializar, el xmlns redundante del <DTE> interno puede omitirse
        // (el sobre ya declara el mismo default); eso preserva el infoset y la
        // firma. Lo que SI debe ser byte-identico es el contenido del Documento
        // (incluido el TED) y su firma interna.
        String xml = envio.xmlFirmado();
        String contenido = xml.substring(xml.indexOf("<Documento"), xml.indexOf("</DTE>"));
        assertThat(sobre).contains(contenido);
        // Firma del DTE (stub, dentro del DTE embebido) + firma real del SetDTE.
        // "<Signature " con espacio: no contar SignatureValue/SignatureMethod.
        assertThat(sobre.split("<Signature ", -1).length - 1).isEqualTo(2);
        assertThat(sobre).contains("<SetDTE ID=\"SetDoc\">");
    }

    @Test
    @DisplayName("modo GLOBAL sin FchResol de entorno: fail-fast en la construccion")
    void sinFchResolFallaAlConstruir() {
        AppProperties sinResol = new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", "GLOBAL", props.sii().certificadoPath(), "test123", null, "", 0, "UA"), null);
        assertThatThrownBy(() -> new EnvioBoletaGenerator(new FirmaElectronicaProd(certificadoResolver),
                new DteXmlValidator(true), certificadoResolver, TestResoluciones.deEntorno(sinResol),
                sinResol, RELOJ_FIJO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SII_FCH_RESOL");
    }

    @Test
    @DisplayName("modo POR_EMPRESA sin FchResol de entorno: NO falla al construir (resolucion por llamada)")
    void porEmpresaNoFallaAlConstruirSinFchResol() {
        AppProperties porEmpresa = new AppProperties(null, null, new AppProperties.Sii(
                "CERTIFICACION", "POR_EMPRESA", props.sii().certificadoPath(), "test123", null, "", 0, "UA"), null);
        assertThatCode(() -> new EnvioBoletaGenerator(new FirmaElectronicaProd(certificadoResolver),
                new DteXmlValidator(true), certificadoResolver, TestResoluciones.deEntorno(porEmpresa),
                porEmpresa, RELOJ_FIJO))
                .doesNotThrowAnyException();
    }
}
