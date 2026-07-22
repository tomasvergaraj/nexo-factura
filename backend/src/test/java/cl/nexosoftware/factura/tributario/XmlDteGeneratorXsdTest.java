package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.documento.Referencia;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.documento.TipoReferencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifica de extremo a extremo (sin Spring ni base de datos) que el flujo de
 * emision — TED real con CAF sintetico → XML → firma (stub con forma valida) —
 * produce documentos que cumplen los XSD OFICIALES del SII: DTE_v10 para
 * facturas/notas y EnvioBOLETA_v11 (via el wrapper local) para boletas.
 * Complementa a EmisionXsdIT (Testcontainers) corriendo en el gate unitario.
 */
class XmlDteGeneratorXsdTest {

    private final DteXmlValidator validator = new DteXmlValidator(true);

    @Test
    @DisplayName("una factura normal firmada cumple el XSD oficial DTE_v10")
    void facturaNormalEsValida() {
        String xml = DteFixtures.xmlFirmado(DteFixtures.factura(1.0, 10000L, true));

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
        // Namespace oficial como default en la raiz (JAXB emite version antes del
        // xmlns) + xsi declarado para que la C14N inclusive del Documento sea la
        // misma dentro del sobre (que tambien declara xsi) que al firmar suelto.
        assertThat(xml).contains("<DTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
        // Acteco y TmstFirma: obligatorios del XSD oficial que antes no se emitian.
        assertThat(xml).contains("<Acteco>620200</Acteco>").contains("<TmstFirma>");
        assertThat(xml).contains("<QtyItem>1.0</QtyItem>").contains("<TasaIVA>19.0</TasaIVA>");
        // Regresion: una factura sin otros impuestos no emite ImptoReten ni CodImpAdic.
        assertThat(xml).doesNotContain("<ImptoReten>").doesNotContain("<CodImpAdic>");
    }

    @Test
    @DisplayName("el TED va aplanado, con el CAF embebido verbatim y FRMT real")
    void tedRealEmbebido() {
        DocumentoTributario doc = DteFixtures.factura(1.0, 10000L, true);
        String xml = DteFixtures.xmlFirmado(doc);

        // El CAF sintetico quedo dentro del DD, APLANADO (regla A.2.4 sobre el
        // DD completo: el SII re-aplana antes de verificar el FRMT); los valores
        // terminales del CAF no cambian.
        String cafAplanado = DteFixtures.caf(33).cafXmlVerbatim().replaceAll(">\\s+<", "><");
        assertThat(xml).contains(cafAplanado);
        // El TED no re-declara namespaces (regla de aplanado del SII).
        int ted = xml.indexOf("<TED");
        int finTed = xml.indexOf("</TED>");
        assertThat(xml.substring(ted, finTed)).doesNotContain("xmlns");
        // FRMT real (no placeholder).
        assertThat(xml).contains("<FRMT algoritmo=\"SHA1withRSA\">");
        assertThat(xml).doesNotContain("FRMT-PENDIENTE");
    }

    @Test
    @DisplayName("una factura con impuesto adicional emite ImptoReten/CodImpAdic en orden y cumple el XSD")
    void facturaConImpuestoAdicionalEsValida() {
        DocumentoTributario doc = DteFixtures.factura(1.0, 100000L, true);
        doc.getLineas().get(0).setCodImpAdic(27); // ILA analcoholicas 10%
        doc.setImpuestosAdicionales(10000);
        doc.setTotal(doc.getNeto() + doc.getIva() + 10000);
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<CodImpAdic>27</CodImpAdic>")
                .contains("<TipoImp>27</TipoImp>")
                .contains("<TasaImp>10.0</TasaImp>")
                .contains("<MontoImp>10000</MontoImp>");
        // Orden en Totales: IVA < ImptoReten < MntTotal.
        assertThat(xml.indexOf("<IVA>")).isLessThan(xml.indexOf("<ImptoReten>"));
        assertThat(xml.indexOf("<ImptoReten>")).isLessThan(xml.indexOf("<MntTotal>"));
        // Orden del XSD oficial en Detalle: IndExe/NmbItem antes, CodImpAdic < MontoItem.
        assertThat(xml.indexOf("<CodImpAdic>")).isLessThan(xml.indexOf("<MontoItem>"));
    }

    @Test
    @DisplayName("una factura exenta 34 emite MntExe sin MntNeto/TasaIVA/IVA y cumple el XSD")
    void facturaExentaOmiteNetoEIva() {
        DocumentoTributario doc = DteFixtures.factura(1.0, 25000L, false);
        doc.setTipoDte(TipoDte.FACTURA_EXENTA);
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_EXENTA)).doesNotThrowAnyException();
        assertThat(xml).contains("<MntExe>25000</MntExe>")
                .doesNotContain("<MntNeto>").doesNotContain("<TasaIVA>").doesNotContain("<IVA>");
    }

    @Test
    @DisplayName("una linea exenta emite IndExe ANTES de NmbItem (orden del XSD oficial)")
    void indExeAntesDeNmbItem() {
        DocumentoTributario doc = DteFixtures.factura(1.0, 8000L, false);
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
        assertThat(xml.indexOf("<IndExe>")).isPositive().isLessThan(xml.indexOf("<NmbItem>"));
    }

    @Test
    @DisplayName("una linea de regalo (precio 0) omite PrcItem y cumple el XSD")
    void lineaConPrecioCeroOmitePrcItem() {
        DocumentoTributario doc = DteFixtures.factura(1.0, 10000L, true);
        doc.agregarLinea(LineaDetalle.builder()
                .nombre("Muestra de regalo")
                .cantidad(1.0)
                .unidad("UN")
                .precioUnitario(0)
                .descuentoMonto(0)
                .afecto(true)
                .montoLinea(0)
                .build());
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
        // Dec12_6Type exige minimo 0.000001: la linea con precio 0 no emite PrcItem.
        assertThat(xml.split("<PrcItem>", -1).length - 1).isEqualTo(1);
        assertThat(xml).contains("<NmbItem>Muestra de regalo</NmbItem>");
    }

    @Test
    @DisplayName("una cantidad >= 1e7 se marshalla como decimal plano y cumple xs:decimal")
    void cantidadGrandeSeMarshalaPlana() {
        DocumentoTributario doc = DteFixtures.factura(1.0E7, 1L, true);
        String xml = DteFixtures.xmlFirmado(doc);

        // Sin el adaptador JAXB emitiria "1.0E7", invalido como xs:decimal.
        assertThat(xml).contains("<QtyItem>10000000</QtyItem>");
        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una factura con DescuentoPct por linea y DscRcgGlobal cumple el XSD y el orden oficial")
    void facturaConDescuentosDelSetEsValida() {
        // Caso basico-2/-4 del set de certificacion: % por linea + global 24%.
        DocumentoTributario doc = DteFixtures.factura(807.0, 6241L, true);
        LineaDetalle l = doc.getLineas().get(0);
        l.setDescuentoPct(10.0);
        l.setDescuentoMonto(503649);
        l.setMontoLinea(4532838);
        doc.setDescuentoGlobalPct(24.0);
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<DescuentoPct>10.0</DescuentoPct>")
                .contains("<DescuentoMonto>503649</DescuentoMonto>")
                .contains("<DscRcgGlobal><NroLinDR>1</NroLinDR><TpoMov>D</TpoMov>"
                        + "<TpoValor>%</TpoValor><ValorDR>24.0</ValorDR></DscRcgGlobal>");
        // Orden del XSD: DescuentoPct < DescuentoMonto en el detalle; el bloque
        // DscRcgGlobal va despues del detalle y antes del TED.
        assertThat(xml.indexOf("<DescuentoPct>")).isLessThan(xml.indexOf("<DescuentoMonto>"));
        assertThat(xml.indexOf("</Detalle>")).isLessThan(xml.indexOf("<DscRcgGlobal>"));
        assertThat(xml.indexOf("<DscRcgGlobal>")).isLessThan(xml.indexOf("<TED"));
    }

    @Test
    @DisplayName("la unidad de medida de la linea viaja en UnmdItem (caso exenta: Hora)")
    void unidadDeMedidaPersonalizadaEsValida() {
        DocumentoTributario doc = DteFixtures.factura(10.0, 6180L, false);
        doc.setTipoDte(TipoDte.FACTURA_EXENTA);
        doc.getLineas().get(0).setUnidad("Hora");
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_EXENTA)).doesNotThrowAnyException();
        assertThat(xml).contains("<UnmdItem>Hora</UnmdItem>");
    }

    @Test
    @DisplayName("un DTE con setCaso emite la Referencia SET (sin CodRef) y cumple el XSD")
    void referenciaAlSetDeCertificacionEsValida() {
        // El revisor del set asocia el DTE a su caso por esta referencia; sin
        // ella rechaza con "El Documento No Esta en el Envio".
        DocumentoTributario doc = DteFixtures.factura(172.0, 3728L, true);
        doc.setSetCaso("4965879-1");
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.FACTURA_AFECTA)).doesNotThrowAnyException();
        assertThat(xml).contains("<Referencia><NroLinRef>1</NroLinRef><TpoDocRef>SET</TpoDocRef>"
                + "<FolioRef>4965879-1</FolioRef>");
        assertThat(xml).contains("<RazonRef>CASO 4965879-1</RazonRef>");
        // La referencia SET no lleva CodRef (no corrige ni anula nada).
        assertThat(xml).doesNotContain("<CodRef>");
    }

    @Test
    @DisplayName("una NC con setCaso mantiene la referencia SET primero y la del documento despues")
    void notaConSetCasoLlevaAmbasReferencias() {
        DocumentoTributario doc = DteFixtures.factura(1.0, 0L, true);
        doc.setTipoDte(TipoDte.NOTA_CREDITO);
        doc.setSetCaso("4965879-5");
        doc.agregarReferencia(Referencia.builder()
                .tipoDocumentoRef(33).folioRef(16L).fechaRef(LocalDate.of(2026, 7, 22))
                .tipoReferencia(TipoReferencia.CORRIGE_TEXTO)
                .razon("CORRIGE GIRO DEL RECEPTOR")
                .build());
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.NOTA_CREDITO)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<NroLinRef>1</NroLinRef><TpoDocRef>SET</TpoDocRef><FolioRef>4965879-5</FolioRef>")
                .contains("<NroLinRef>2</NroLinRef><TpoDocRef>33</TpoDocRef><FolioRef>16</FolioRef>")
                .contains("<CodRef>2</CodRef>");
    }

    @Test
    @DisplayName("una NC de correccion de texto (totales 0) cumple el XSD: MntTotal 0 sin MntNeto/IVA")
    void notaCreditoCorrigeGiroConTotalesCeroEsValida() {
        // Caso basico-5 del set: NC que corrige el giro del receptor, sin montos.
        DocumentoTributario doc = DteFixtures.factura(1.0, 0L, true);
        doc.setTipoDte(TipoDte.NOTA_CREDITO);
        doc.getLineas().get(0).setNombre("DONDE DICE Comercio DEBE DECIR Servicios");
        doc.agregarReferencia(Referencia.builder()
                .tipoDocumentoRef(33).folioRef(5L).fechaRef(LocalDate.of(2026, 6, 26))
                .tipoReferencia(TipoReferencia.CORRIGE_TEXTO)
                .razon("CORRIGE GIRO DEL RECEPTOR")
                .build());
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.NOTA_CREDITO)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<MntTotal>0</MntTotal>")
                .contains("<CodRef>2</CodRef>")
                .contains("<RazonRef>CORRIGE GIRO DEL RECEPTOR</RazonRef>");
        // Sin monto afecto no se declara neto/IVA. La linea de precio 0 omite
        // PrcItem Y TAMBIEN QtyItem/UnmdItem: el revisor del set exige
        // QtyItem*PrcItem = MontoItem y una cantidad sin precio no le cuadra.
        assertThat(xml).doesNotContain("<MntNeto>").doesNotContain("<TasaIVA>")
                .doesNotContain("<IVA>").doesNotContain("<PrcItem>")
                .doesNotContain("<QtyItem>").doesNotContain("<UnmdItem>");
    }

    @Test
    @DisplayName("una NC 100% exenta (set exenta) emite MntExe sin IVA y cumple el XSD")
    void notaCreditoExentaOmiteIva() {
        // Caso exenta-2 del set: NC que modifica el monto de una exenta 34.
        DocumentoTributario doc = DteFixtures.factura(10.0, 773L, false);
        doc.setTipoDte(TipoDte.NOTA_CREDITO);
        doc.getLineas().get(0).setNombre("HORAS PROGRAMADOR");
        doc.agregarReferencia(Referencia.builder()
                .tipoDocumentoRef(34).folioRef(3L).fechaRef(LocalDate.of(2026, 6, 26))
                .tipoReferencia(TipoReferencia.CORRIGE_MONTO)
                .razon("MODIFICA MONTO")
                .build());
        String xml = DteFixtures.xmlFirmado(doc);

        assertThatCode(() -> validator.validar(xml, TipoDte.NOTA_CREDITO)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<MntExe>7730</MntExe>")
                .contains("<MntTotal>7730</MntTotal>")
                .contains("<IndExe>1</IndExe>")
                .contains("<CodRef>3</CodRef>");
        assertThat(xml).doesNotContain("<MntNeto>").doesNotContain("<TasaIVA>").doesNotContain("<IVA>");
    }

    @Test
    @DisplayName("una boleta afecta cumple EnvioBOLETA_v11: RznSocEmisor, IndServicio=3, sin TasaIVA")
    void boletaAfectaEsValida() {
        String xml = DteFixtures.xmlFirmado(DteFixtures.boletaAfecta(11900L));

        assertThatCode(() -> validator.validar(xml, TipoDte.BOLETA_AFECTA)).doesNotThrowAnyException();
        // La rama boleta tambien declara xsi en la raiz (contexto C14N del sobre).
        assertThat(xml).contains("<DTE version=\"1.0\" xmlns=\"http://www.sii.cl/SiiDte\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
        // El esquema de boleta es DISTINTO al de factura:
        assertThat(xml)
                .contains("<RznSocEmisor>")
                .contains("<GiroEmisor>")
                .contains("<IndServicio>3</IndServicio>")
                .contains("<MntNeto>10000</MntNeto>")
                .contains("<IVA>1900</IVA>")
                .contains("<MntTotal>11900</MntTotal>")
                .contains("<TmstFirma>");
        assertThat(xml).doesNotContain("<TasaIVA>").doesNotContain("<Acteco>")
                .doesNotContain("<RznSoc>").doesNotContain("<GiroEmis>");
    }

    @Test
    @DisplayName("una boleta exenta emite MntExe sin MntNeto/IVA y cumple el esquema")
    void boletaExentaEsValida() {
        String xml = DteFixtures.xmlFirmado(DteFixtures.boletaExenta(8000L));

        assertThatCode(() -> validator.validar(xml, TipoDte.BOLETA_EXENTA)).doesNotThrowAnyException();
        assertThat(xml)
                .contains("<MntExe>8000</MntExe>")
                .contains("<MntTotal>8000</MntTotal>")
                .contains("<IndExe>1</IndExe>");
        assertThat(xml).doesNotContain("<MntNeto>").doesNotContain("<IVA>");
    }

    @Test
    @DisplayName("un receptor consumidor final (sin giro/dir/comuna) cumple el esquema de boleta")
    void receptorConsumidorFinalEsValido() {
        String xml = DteFixtures.xmlFirmado(DteFixtures.boletaAfecta(11900L));

        assertThat(xml).contains("<RUTRecep>66666666-6</RUTRecep>").doesNotContain("<GiroRecep>");
        assertThatCode(() -> validator.validar(xml, TipoDte.BOLETA_AFECTA)).doesNotThrowAnyException();
    }
}
