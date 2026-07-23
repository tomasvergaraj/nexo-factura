package cl.nexosoftware.factura.intercambio;

import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.intercambio.IntercambioDtos.DecisionDte;
import cl.nexosoftware.factura.intercambio.IntercambioDtos.RespuestaIntercambioResponse;
import cl.nexosoftware.factura.tributario.CertificadoDigital;
import cl.nexosoftware.factura.tributario.DteXmlValidator;
import cl.nexosoftware.factura.tributario.EnvioRecibosGenerator;
import cl.nexosoftware.factura.tributario.FirmaElectronicaStub;
import cl.nexosoftware.factura.tributario.LectorSobreDte;
import cl.nexosoftware.factura.tributario.RespuestaDteGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El servicio de intercambio aplica la regla del set: acepta el DTE dirigido a
 * nuestro RUT (52235) y rechaza el dirigido a otro (52236, EstadoRecepDTE=3),
 * genera los tres artefactos y solo produce Recibo/Resultado para los aceptados.
 *
 * Usa la firma stub y un validador deshabilitado para el sobre ENTRANTE (asi el
 * sobre minimo pasa sin firma real); los generadores validan sus SALIDAS contra
 * el XSD oficial (validador habilitado), de modo que un artefacto mal formado
 * haria fallar el test.
 */
class IntercambioServiceTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-23T14:30:00Z"), ZoneId.of("America/Santiago"));

    private static final String SOBRE = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <EnvioDTE xmlns="http://www.sii.cl/SiiDte" version="1.0">
             <SetDTE ID="SetDoc">
              <Caratula version="1.0">
               <RutEmisor>88888888-8</RutEmisor>
               <RutEnvia>8414240-9</RutEnvia>
               <RutReceptor>78397017-1</RutReceptor>
              </Caratula>
              <DTE version="1.0"><Documento ID="T1">
               <Encabezado>
                <IdDoc><TipoDTE>33</TipoDTE><Folio>52235</Folio><FchEmis>2026-07-23</FchEmis></IdDoc>
                <Emisor><RUTEmisor>88888888-8</RUTEmisor></Emisor>
                <Receptor><RUTRecep>78397017-1</RUTRecep></Receptor>
                <Totales><MntTotal>5390</MntTotal></Totales>
               </Encabezado>
              </Documento></DTE>
              <DTE version="1.0"><Documento ID="T2">
               <Encabezado>
                <IdDoc><TipoDTE>33</TipoDTE><Folio>52236</Folio><FchEmis>2013-06-21</FchEmis></IdDoc>
                <Emisor><RUTEmisor>88888888-8</RUTEmisor></Emisor>
                <Receptor><RUTRecep>69507000-4</RUTRecep></Receptor>
                <Totales><MntTotal>7770</MntTotal></Totales>
               </Encabezado>
              </Documento></DTE>
             </SetDTE>
             <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">
              <SignedInfo><Reference URI="#SetDoc"><DigestValue>1WGHYu7oiVjSTV1/Bjcejc02gcA=</DigestValue></Reference></SignedInfo>
             </Signature>
            </EnvioDTE>
            """;

    @SuppressWarnings("unchecked")
    private IntercambioService servicio() {
        FirmaElectronicaStub firma = new FirmaElectronicaStub();
        DteXmlValidator validatorSalida = new DteXmlValidator(true);   // valida los artefactos generados
        DteXmlValidator validatorEntrada = new DteXmlValidator(false); // el sobre minimo no lleva firma real

        RespuestaDteGenerator respGen = new RespuestaDteGenerator(firma, validatorSalida);
        EnvioRecibosGenerator recGen = new EnvioRecibosGenerator(firma, validatorSalida);

        EmpresaRepository repo = mock(EmpresaRepository.class);
        when(repo.findById(5L)).thenReturn(Optional.of(Empresa.builder()
                .rut("78397017-1").razonSocial("NEXO SOFTWARE SPA").giro("Informatica")
                .direccion("Santiago").comuna("Santiago")
                .telefono("+56222222222").email("contacto@nexosoftware.cl").build()));

        ObjectProvider<CertificadoDigital> sinCert = mock(ObjectProvider.class);
        when(sinCert.getIfAvailable()).thenReturn(null);

        return new IntercambioService(new LectorSobreDte(), validatorEntrada, respGen, recGen,
                repo, sinCert, RELOJ);
    }

    @Test
    @DisplayName("acepta el 52235, rechaza el 52236 (codigo 3) y produce los tres artefactos firmados")
    void aceptaNuestroYRechazaAjeno() {
        RespuestaIntercambioResponse r = servicio().responder(5L, SOBRE, "set_intercambio.xml");

        assertThat(r.decisiones()).hasSize(2);
        DecisionDte d1 = r.decisiones().get(0);
        assertThat(d1.folio()).isEqualTo(52235L);
        assertThat(d1.aceptado()).isTrue();
        assertThat(d1.estadoRecepDte()).isZero();

        DecisionDte d2 = r.decisiones().get(1);
        assertThat(d2.folio()).isEqualTo(52236L);
        assertThat(d2.aceptado()).isFalse();
        assertThat(d2.estadoRecepDte()).isEqualTo(3);

        // Los tres artefactos existen (hay un DTE aceptado).
        assertThat(r.respuestaIntercambio()).contains("<RespuestaDTE").contains("<RecepcionEnvio>");
        assertThat(r.reciboMercaderias()).contains("<EnvioRecibos").contains("<DocumentoRecibo ID=\"Recibo52235\">");
        assertThat(r.resultadoComercial()).contains("<ResultadoDTE>").contains("<Folio>52235</Folio>");
        // El recibo y el resultado comercial NO incluyen el DTE ajeno.
        assertThat(r.reciboMercaderias()).doesNotContain("52236");
        assertThat(r.resultadoComercial()).doesNotContain("52236");
    }

    @Test
    @DisplayName("si ningun DTE es para nuestro RUT, solo se genera la Respuesta de Intercambio")
    void sinAceptadosSoloRespuesta() {
        // Empresa con OTRO RUT: ningun DTE del sobre va dirigido a ella.
        @SuppressWarnings("unchecked")
        ObjectProvider<CertificadoDigital> sinCert = mock(ObjectProvider.class);
        when(sinCert.getIfAvailable()).thenReturn(null);
        EmpresaRepository repo = mock(EmpresaRepository.class);
        when(repo.findById(9L)).thenReturn(Optional.of(Empresa.builder()
                .rut("76000000-0").razonSocial("OTRA SPA").giro("x")
                .direccion("y").comuna("z").build()));
        IntercambioService svc = new IntercambioService(new LectorSobreDte(),
                new DteXmlValidator(false),
                new RespuestaDteGenerator(new FirmaElectronicaStub(), new DteXmlValidator(true)),
                new EnvioRecibosGenerator(new FirmaElectronicaStub(), new DteXmlValidator(true)),
                repo, sinCert, RELOJ);

        RespuestaIntercambioResponse r = svc.responder(9L, SOBRE, "set_intercambio.xml");

        assertThat(r.respuestaIntercambio()).isNotNull();
        assertThat(r.reciboMercaderias()).isNull();
        assertThat(r.resultadoComercial()).isNull();
        assertThat(r.decisiones()).allMatch(d -> !d.aceptado());
    }
}
