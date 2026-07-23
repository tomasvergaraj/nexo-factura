package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El lector del sobre EnvioDTE recibido extrae la caratula, el ID/DigestValue
 * del SetDTE y el resumen de cada DTE (leidos del Encabezado, no del TED).
 */
class LectorSobreDteTest {

    private final LectorSobreDte lector = new LectorSobreDte();

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

    @Test
    @DisplayName("extrae caratula, EnvioDTEID, DigestValue del SetDTE y los dos DTE con su RUT receptor")
    void leeElSobre() {
        SobreRecibido sobre = lector.leer(SOBRE);

        assertThat(sobre.rutEmisor()).isEqualTo("88888888-8");
        assertThat(sobre.rutReceptor()).isEqualTo("78397017-1");
        assertThat(sobre.envioDteId()).isEqualTo("SetDoc");
        assertThat(sobre.digest()).isEqualTo("1WGHYu7oiVjSTV1/Bjcejc02gcA=");
        assertThat(sobre.documentos()).hasSize(2);

        SobreRecibido.DteRecibido d1 = sobre.documentos().get(0);
        assertThat(d1.tipoDte()).isEqualTo(33);
        assertThat(d1.folio()).isEqualTo(52235L);
        assertThat(d1.fchEmis()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(d1.rutEmisor()).isEqualTo("88888888-8");
        assertThat(d1.rutReceptor()).isEqualTo("78397017-1");
        assertThat(d1.mntTotal()).isEqualTo(5390L);

        SobreRecibido.DteRecibido d2 = sobre.documentos().get(1);
        assertThat(d2.folio()).isEqualTo(52236L);
        assertThat(d2.rutReceptor()).isEqualTo("69507000-4"); // la trampa
    }

    @Test
    @DisplayName("un XML que no es EnvioDTE se rechaza con mensaje claro")
    void rechazaXmlQueNoEsSobre() {
        assertThatThrownBy(() -> lector.leer("<foo/>"))
                .isInstanceOf(DteInvalidoException.class)
                .hasMessageContaining("SetDTE");
    }
}
