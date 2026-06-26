package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.common.exception.DteInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitario del validador XSD del DTE (sin contexto de Spring). Comprueba que
 * los DTE bien formados de cada variante (afecto, exento, consumidor final, con
 * referencia) pasan, y que los malformados o con elementos fuera de orden fallan.
 * Las fixturas reproducen EXACTAMENTE lo que marshalla XmlDteGenerator.
 */
class DteXmlValidatorTest {

    private final DteXmlValidator validator = new DteXmlValidator(true);

    /** Factura afecta (33), una linea afecta -> sin IndExe, receptor completo. */
    private static final String DTE_VALIDO = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <DTE version="1.0">
                <Documento ID="T33F1">
                    <Encabezado>
                        <IdDoc>
                            <TipoDTE>33</TipoDTE>
                            <Folio>1</Folio>
                            <FchEmis>2026-06-26</FchEmis>
                        </IdDoc>
                        <Emisor>
                            <RUTEmisor>91000000-0</RUTEmisor>
                            <RznSoc>Empresa Demo</RznSoc>
                            <GiroEmis>Servicios</GiroEmis>
                            <DirOrigen>Calle 1</DirOrigen>
                            <CmnaOrigen>Quillota</CmnaOrigen>
                        </Emisor>
                        <Receptor>
                            <RUTRecep>77111222-3</RUTRecep>
                            <RznSocRecep>Cliente de prueba</RznSocRecep>
                            <GiroRecep>Comercio</GiroRecep>
                            <DirRecep>Av 2</DirRecep>
                            <CmnaRecep>Vina</CmnaRecep>
                        </Receptor>
                        <Totales>
                            <MntNeto>10000</MntNeto>
                            <MntExe>0</MntExe>
                            <TasaIVA>19.0</TasaIVA>
                            <IVA>1900</IVA>
                            <MntTotal>11900</MntTotal>
                        </Totales>
                    </Encabezado>
                    <Detalle>
                        <NroLinDet>1</NroLinDet>
                        <NmbItem>Servicio</NmbItem>
                        <QtyItem>1.0</QtyItem>
                        <UnmdItem>UN</UnmdItem>
                        <PrcItem>10000</PrcItem>
                        <DescuentoMonto>0</DescuentoMonto>
                        <MontoItem>10000</MontoItem>
                    </Detalle>
                    <TED version="1.0">
                        <DD>
                            <RE>91000000-0</RE>
                            <TD>33</TD>
                            <F>1</F>
                            <FE>2026-06-26</FE>
                            <RR>77111222-3</RR>
                            <RSR>Cliente de prueba</RSR>
                            <MNT>11900</MNT>
                            <IT1>Servicio</IT1>
                            <TSTED>2026-06-26T14:03:21.123</TSTED>
                        </DD>
                        <FRMT algoritmo="SHA1withRSA">RlJNVC1QRU5ESUVOVEU=</FRMT>
                    </TED>
                </Documento>
            </DTE>
            """;

    /** Nota de credito (61) con bloque Referencia entre Detalle y TED. */
    private static final String DTE_CON_REFERENCIA = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <DTE version="1.0">
                <Documento ID="T61F1">
                    <Encabezado>
                        <IdDoc>
                            <TipoDTE>61</TipoDTE>
                            <Folio>1</Folio>
                            <FchEmis>2026-06-26</FchEmis>
                        </IdDoc>
                        <Emisor>
                            <RUTEmisor>91000000-0</RUTEmisor>
                            <RznSoc>Empresa Demo</RznSoc>
                            <GiroEmis>Servicios</GiroEmis>
                            <DirOrigen>Calle 1</DirOrigen>
                            <CmnaOrigen>Quillota</CmnaOrigen>
                        </Emisor>
                        <Receptor>
                            <RUTRecep>77111222-3</RUTRecep>
                            <RznSocRecep>Cliente de prueba</RznSocRecep>
                        </Receptor>
                        <Totales>
                            <MntNeto>10000</MntNeto>
                            <MntExe>0</MntExe>
                            <TasaIVA>19.0</TasaIVA>
                            <IVA>1900</IVA>
                            <MntTotal>11900</MntTotal>
                        </Totales>
                    </Encabezado>
                    <Detalle>
                        <NroLinDet>1</NroLinDet>
                        <NmbItem>Anula factura</NmbItem>
                        <QtyItem>1.0</QtyItem>
                        <UnmdItem>UN</UnmdItem>
                        <PrcItem>10000</PrcItem>
                        <DescuentoMonto>0</DescuentoMonto>
                        <MontoItem>10000</MontoItem>
                    </Detalle>
                    <Referencia>
                        <NroLinRef>1</NroLinRef>
                        <TpoDocRef>33</TpoDocRef>
                        <FolioRef>1</FolioRef>
                        <FchRef>2026-06-26</FchRef>
                        <CodRef>1</CodRef>
                        <RazonRef>Anula la factura</RazonRef>
                    </Referencia>
                    <TED version="1.0">
                        <DD>
                            <RE>91000000-0</RE>
                            <TD>61</TD>
                            <F>1</F>
                            <FE>2026-06-26</FE>
                            <RR>77111222-3</RR>
                            <RSR>Cliente de prueba</RSR>
                            <MNT>11900</MNT>
                            <IT1>Anula factura</IT1>
                            <TSTED>2026-06-26T14:03:21.123</TSTED>
                        </DD>
                        <FRMT algoritmo="SHA1withRSA">RlJNVC1QRU5ESUVOVEU=</FRMT>
                    </TED>
                </Documento>
            </DTE>
            """;

    /** Boleta (39) a consumidor final: receptor solo con RUT y razon social. */
    private static final String DTE_CONSUMIDOR_FINAL = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <DTE version="1.0">
                <Documento ID="T39F1">
                    <Encabezado>
                        <IdDoc>
                            <TipoDTE>39</TipoDTE>
                            <Folio>1</Folio>
                            <FchEmis>2026-06-26</FchEmis>
                        </IdDoc>
                        <Emisor>
                            <RUTEmisor>91000000-0</RUTEmisor>
                            <RznSoc>Empresa Demo</RznSoc>
                            <GiroEmis>Servicios</GiroEmis>
                            <DirOrigen>Calle 1</DirOrigen>
                            <CmnaOrigen>Quillota</CmnaOrigen>
                        </Emisor>
                        <Receptor>
                            <RUTRecep>66666666-6</RUTRecep>
                            <RznSocRecep>Consumidor final</RznSocRecep>
                        </Receptor>
                        <Totales>
                            <MntNeto>10000</MntNeto>
                            <MntExe>0</MntExe>
                            <TasaIVA>19.0</TasaIVA>
                            <IVA>1900</IVA>
                            <MntTotal>11900</MntTotal>
                        </Totales>
                    </Encabezado>
                    <Detalle>
                        <NroLinDet>1</NroLinDet>
                        <NmbItem>Cafe</NmbItem>
                        <QtyItem>1.0</QtyItem>
                        <UnmdItem>UN</UnmdItem>
                        <PrcItem>11900</PrcItem>
                        <DescuentoMonto>0</DescuentoMonto>
                        <MontoItem>11900</MontoItem>
                    </Detalle>
                    <TED version="1.0">
                        <DD>
                            <RE>91000000-0</RE>
                            <TD>39</TD>
                            <F>1</F>
                            <FE>2026-06-26</FE>
                            <RR>66666666-6</RR>
                            <RSR>Consumidor final</RSR>
                            <MNT>11900</MNT>
                            <IT1>Cafe</IT1>
                            <TSTED>2026-06-26T14:03:21.123</TSTED>
                        </DD>
                        <FRMT algoritmo="SHA1withRSA">RlJNVC1QRU5ESUVOVEU=</FRMT>
                    </TED>
                </Documento>
            </DTE>
            """;

    /** Boleta exenta (41): una linea con IndExe=1, IVA cero, todo exento. */
    private static final String DTE_EXENTO = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <DTE version="1.0">
                <Documento ID="T41F1">
                    <Encabezado>
                        <IdDoc>
                            <TipoDTE>41</TipoDTE>
                            <Folio>1</Folio>
                            <FchEmis>2026-06-26</FchEmis>
                        </IdDoc>
                        <Emisor>
                            <RUTEmisor>91000000-0</RUTEmisor>
                            <RznSoc>Empresa Demo</RznSoc>
                            <GiroEmis>Servicios</GiroEmis>
                            <DirOrigen>Calle 1</DirOrigen>
                            <CmnaOrigen>Quillota</CmnaOrigen>
                        </Emisor>
                        <Receptor>
                            <RUTRecep>66666666-6</RUTRecep>
                            <RznSocRecep>Consumidor final</RznSocRecep>
                        </Receptor>
                        <Totales>
                            <MntNeto>0</MntNeto>
                            <MntExe>8000</MntExe>
                            <TasaIVA>19.0</TasaIVA>
                            <IVA>0</IVA>
                            <MntTotal>8000</MntTotal>
                        </Totales>
                    </Encabezado>
                    <Detalle>
                        <NroLinDet>1</NroLinDet>
                        <NmbItem>Servicio exento</NmbItem>
                        <QtyItem>1.0</QtyItem>
                        <UnmdItem>UN</UnmdItem>
                        <PrcItem>8000</PrcItem>
                        <DescuentoMonto>0</DescuentoMonto>
                        <IndExe>1</IndExe>
                        <MontoItem>8000</MontoItem>
                    </Detalle>
                    <TED version="1.0">
                        <DD>
                            <RE>91000000-0</RE>
                            <TD>41</TD>
                            <F>1</F>
                            <FE>2026-06-26</FE>
                            <RR>66666666-6</RR>
                            <RSR>Consumidor final</RSR>
                            <MNT>8000</MNT>
                            <IT1>Servicio exento</IT1>
                            <TSTED>2026-06-26T14:03:21.123</TSTED>
                        </DD>
                        <FRMT algoritmo="SHA1withRSA">RlJNVC1QRU5ESUVOVEU=</FRMT>
                    </TED>
                </Documento>
            </DTE>
            """;

    @Test
    @DisplayName("el XSD se compila al construir el validador")
    void compilaElXsdEnConstructor() {
        assertThatCode(() -> new DteXmlValidator(true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un DTE afecto bien formado pasa la validacion")
    void dteValidoPasa() {
        assertThatCode(() -> validator.validar(DTE_VALIDO)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una nota de credito con bloque Referencia pasa")
    void dteConReferenciaPasa() {
        assertThatCode(() -> validator.validar(DTE_CON_REFERENCIA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una boleta a consumidor final sin giro/dir/comuna pasa")
    void dteConsumidorFinalPasa() {
        assertThatCode(() -> validator.validar(DTE_CONSUMIDOR_FINAL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una boleta exenta con IndExe=1 pasa")
    void dteExentoPasa() {
        assertThatCode(() -> validator.validar(DTE_EXENTO)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("falta MntTotal -> DteInvalidoException")
    void faltaMntTotalLanza() {
        String malo = DTE_VALIDO.replace("<MntTotal>11900</MntTotal>", "");
        assertThatThrownBy(() -> validator.validar(malo)).isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("Folio no numerico -> DteInvalidoException")
    void folioNoNumericoLanza() {
        String malo = DTE_VALIDO.replace("<Folio>1</Folio>", "<Folio>abc</Folio>");
        assertThatThrownBy(() -> validator.validar(malo)).isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("TipoDTE fuera del enum -> DteInvalidoException")
    void tipoDteFueraDeEnumLanza() {
        String malo = DTE_VALIDO.replace("<TipoDTE>33</TipoDTE>", "<TipoDTE>99</TipoDTE>");
        assertThatThrownBy(() -> validator.validar(malo)).isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("RUT del emisor mal formado -> DteInvalidoException")
    void rutEmisorMalFormadoLanza() {
        String malo = DTE_VALIDO.replace("<RUTEmisor>91000000-0</RUTEmisor>", "<RUTEmisor>ABCDEFGH</RUTEmisor>");
        assertThatThrownBy(() -> validator.validar(malo)).isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("elementos de IdDoc en orden invertido -> DteInvalidoException")
    void ordenElementosInvertidoLanza() {
        // Quita TipoDTE de su lugar y lo reinserta despues de Folio: rompe la secuencia.
        String malo = DTE_VALIDO
                .replace("<TipoDTE>33</TipoDTE>", "")
                .replace("<Folio>1</Folio>", "<Folio>1</Folio><TipoDTE>33</TipoDTE>");
        assertThatThrownBy(() -> validator.validar(malo)).isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("Referencia despues del TED -> DteInvalidoException (contrato de orden)")
    void referenciaDespuesDeTedLanza() {
        // El bloque Referencia debe ir ANTES del TED; insertarlo despues lo invalida.
        String malo = DTE_VALIDO.replace("</TED>",
                "</TED><Referencia><NroLinRef>1</NroLinRef><TpoDocRef>33</TpoDocRef>"
                        + "<FolioRef>1</FolioRef><FchRef>2026-06-26</FchRef><CodRef>1</CodRef>"
                        + "<RazonRef>x</RazonRef></Referencia>");
        assertThatThrownBy(() -> validator.validar(malo)).isInstanceOf(DteInvalidoException.class);
    }

    @Test
    @DisplayName("multiples errores se acumulan en getErrores()")
    void multiplesErroresSeAcumulan() {
        String malo = DTE_VALIDO
                .replace("<RUTEmisor>91000000-0</RUTEmisor>", "<RUTEmisor>ABCDEFGH</RUTEmisor>")
                .replace("<MntTotal>11900</MntTotal>", "");
        assertThatThrownBy(() -> validator.validar(malo))
                .isInstanceOf(DteInvalidoException.class)
                .satisfies(ex -> assertThat(((DteInvalidoException) ex).getErrores()).hasSizeGreaterThanOrEqualTo(2));
    }
}
