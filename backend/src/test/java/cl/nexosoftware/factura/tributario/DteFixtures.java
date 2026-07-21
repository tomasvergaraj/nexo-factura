package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.folio.CafData;
import cl.nexosoftware.factura.folio.CafParser;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Fixtures de los tests tributarios: CAFs SINTETICOS (clave RSA-512 generada
 * por nosotros, FRMA falsa — el parser no la verifica) y documentos alineados
 * con ellos. Los activos reales de secrets/ jamas se usan en tests.
 *
 * El RUT del emisor (76543210-9) coincide con el RE de los CAFs sinteticos,
 * igual que en produccion.
 */
public final class DteFixtures {

    public static final String RUT_EMISOR = "76543210-9";

    private static final CafParser PARSER = new CafParser();

    private DteFixtures() {}

    public static String xmlCaf(int tipo) {
        try (InputStream in = new ClassPathResource("sii/caf_prueba_" + tipo + ".xml").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el CAF de prueba tipo " + tipo, e);
        }
    }

    public static CafData caf(int tipo) {
        return PARSER.parsear(xmlCaf(tipo));
    }

    public static Empresa emisor() {
        return Empresa.builder()
                .rut(RUT_EMISOR)
                .razonSocial("Empresa de Prueba SpA")
                .giro("Servicios informaticos")
                .actividadEconomica(620200)
                .direccion("Calle 1 #100")
                .comuna("Quillota")
                .build();
    }

    public static DocumentoTributario factura(double cantidad, long precio, boolean afecto) {
        long monto = Math.round(cantidad * precio);
        long iva = afecto ? Math.round(monto * 0.19) : 0;
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
                .neto(afecto ? monto : 0)
                .exento(afecto ? 0 : monto)
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

    /** Boleta 39 (afecta): monto bruto con neto/IVA desglosados. */
    public static DocumentoTributario boletaAfecta(long bruto) {
        long neto = Math.round(bruto / 1.19);
        long iva = bruto - neto;
        DocumentoTributario doc = DocumentoTributario.builder()
                .empresaId(1L)
                .tipoDte(TipoDte.BOLETA_AFECTA)
                .folio(1L)
                .estado(EstadoDte.FIRMADO)
                .fechaEmision(LocalDate.of(2026, 6, 26))
                .receptorRut("66666666-6")
                .receptorRazonSocial("Consumidor final")
                .neto(neto)
                .exento(0)
                .tasaIva(19.0)
                .iva(iva)
                .total(bruto)
                .creadoEn(OffsetDateTime.now())
                .build();
        doc.agregarLinea(LineaDetalle.builder()
                .nombre("Cafe")
                .cantidad(1.0)
                .unidad("UN")
                .precioUnitario(bruto)
                .descuentoMonto(0)
                .afecto(true)
                .montoLinea(bruto)
                .build());
        return doc;
    }

    /** Boleta exenta 41: todo el monto exento, sin IVA. */
    public static DocumentoTributario boletaExenta(long monto) {
        DocumentoTributario doc = DocumentoTributario.builder()
                .empresaId(1L)
                .tipoDte(TipoDte.BOLETA_EXENTA)
                .folio(1L)
                .estado(EstadoDte.FIRMADO)
                .fechaEmision(LocalDate.of(2026, 6, 26))
                .receptorRut("66666666-6")
                .receptorRazonSocial("Consumidor final")
                .neto(0)
                .exento(monto)
                .tasaIva(19.0)
                .iva(0)
                .total(monto)
                .creadoEn(OffsetDateTime.now())
                .build();
        doc.agregarLinea(LineaDetalle.builder()
                .nombre("Servicio exento")
                .cantidad(1.0)
                .unidad("UN")
                .precioUnitario(monto)
                .descuentoMonto(0)
                .afecto(false)
                .montoLinea(monto)
                .build());
        return doc;
    }

    /**
     * Flujo de emision completo con el stub de firma (forma schema-valida):
     * TED real con el CAF sintetico → XML → firma stub. El resultado valida
     * contra los XSD oficiales.
     */
    public static String xmlFirmado(DocumentoTributario doc) {
        CafData caf = caf(doc.getTipoDte().getCodigo() == 39 || doc.getTipoDte().getCodigo() == 41 ? 39 : 33);
        String ted = new TedGenerator().generar(doc, RUT_EMISOR, caf);
        String xml = new XmlDteGenerator().generar(doc, emisor(), ted);
        return new FirmaElectronicaStub().firmar(xml);
    }
}
