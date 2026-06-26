package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifica de extremo a extremo (sin Spring ni base de datos) que el XML que
 * realmente marshalla {@link XmlDteGenerator} cumple el XSD. Complementa a
 * EmisionXsdIT (que requiere Testcontainers) corriendo en el gate unitario.
 *
 * Cubre en particular el desglose decimal: una cantidad grande (1e7) debe
 * marshallarse como decimal plano ("10000000") y pasar xs:decimal, no como la
 * notacion cientifica "1.0E7" que JAXB emite por defecto para un double.
 */
class XmlDteGeneratorXsdTest {

    private final XmlDteGenerator generator = new XmlDteGenerator();
    private final TedGenerator tedGenerator = new TedGenerator();
    private final DteXmlValidator validator = new DteXmlValidator(true);

    @Test
    @DisplayName("una factura normal marshalla un XML que cumple el XSD")
    void facturaNormalEsValida() {
        Empresa emisor = emisor();
        DocumentoTributario doc = factura(emisor, 1.0, 10000L, true);
        String xml = generar(doc, emisor);

        assertThatCode(() -> validator.validar(xml)).doesNotThrowAnyException();
        assertThat(xml).contains("<QtyItem>1.0</QtyItem>").contains("<TasaIVA>19.0</TasaIVA>");
    }

    @Test
    @DisplayName("una cantidad >= 1e7 se marshalla como decimal plano y cumple xs:decimal")
    void cantidadGrandeSeMarshalaPlana() {
        Empresa emisor = emisor();
        DocumentoTributario doc = factura(emisor, 1.0E7, 1L, true); // 10.000.000 unidades
        String xml = generar(doc, emisor);

        // Sin el adaptador JAXB emitiria "<QtyItem>1.0E7</QtyItem>", invalido como xs:decimal.
        assertThat(xml).contains("<QtyItem>10000000</QtyItem>");
        assertThatCode(() -> validator.validar(xml)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un receptor consumidor final (sin giro/dir/comuna) cumple el XSD")
    void receptorConsumidorFinalEsValido() {
        Empresa emisor = emisor();
        DocumentoTributario doc = factura(emisor, 1.0, 11900L, true);
        doc.setReceptorRut("66666666-6");
        doc.setReceptorRazonSocial("Consumidor final");
        doc.setReceptorGiro(null);
        doc.setReceptorDireccion(null);
        doc.setReceptorComuna(null);
        String xml = generar(doc, emisor);

        assertThat(xml).contains("<RUTRecep>66666666-6</RUTRecep>").doesNotContain("<GiroRecep>");
        assertThatCode(() -> validator.validar(xml)).doesNotThrowAnyException();
    }

    private String generar(DocumentoTributario doc, Empresa emisor) {
        ModeloDte.Ted ted = tedGenerator.generar(doc, emisor.getRut());
        return generator.generar(doc, emisor, ted);
    }

    private Empresa emisor() {
        return Empresa.builder()
                .rut("91000000-0")
                .razonSocial("Empresa Demo")
                .giro("Servicios")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build();
    }

    private DocumentoTributario factura(Empresa emisor, double cantidad, long precio, boolean afecto) {
        long monto = Math.round(cantidad * precio);
        long iva = Math.round(monto * 0.19);
        DocumentoTributario doc = DocumentoTributario.builder()
                .empresaId(1L)
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .folio(1L)
                .estado(EstadoDte.FIRMADO)
                .fechaEmision(LocalDate.of(2026, 6, 26))
                .receptorRut("77111222-3")
                .receptorRazonSocial("Cliente de prueba")
                .receptorGiro("Comercio")
                .receptorDireccion("Av 2")
                .receptorComuna("Vina")
                .neto(monto)
                .exento(0)
                .tasaIva(19.0)
                .iva(iva)
                .total(monto + iva)
                .creadoEn(OffsetDateTime.now())
                .build();
        doc.agregarLinea(LineaDetalle.builder()
                .nombre("Item")
                .cantidad(cantidad)
                .unidad("UN")
                .precioUnitario(precio)
                .descuentoMonto(0)
                .afecto(afecto)
                .montoLinea(monto)
                .build());
        return doc;
    }
}
