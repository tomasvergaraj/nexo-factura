package cl.nexosoftware.factura.rcof;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofPorTipo;
import cl.nexosoftware.factura.rcof.RcofDtos.RcofResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integracion del RCOF: agrega los folios de boletas (39/41) de un dia,
 * separando utilizados de anulados, con sus rangos y montos (los anulados se
 * cuentan pero no suman monto). Verifica tambien el dia sin movimiento y el XML.
 */
class RcofServiceIT extends AbstractIntegrationTest {

    private static final LocalDate DIA = LocalDate.of(2026, 6, 26);
    private static final LocalDate OTRO_DIA = LocalDate.of(2026, 6, 25);

    @Autowired private RcofService rcofService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private DocumentoRepository documentoRepository;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("91000000-" + ThreadLocalRandom.current().nextInt(0, 9))
                .razonSocial("Empresa RCOF")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();

        // Boletas afectas (39) del dia: 2 vigentes (folios 10, 11) + 1 anulada (folio 12).
        boleta(TipoDte.BOLETA_AFECTA, 10, EstadoDte.ACEPTADO, DIA, 10000, 1900, 0, 11900);
        boleta(TipoDte.BOLETA_AFECTA, 11, EstadoDte.ENVIADO, DIA, 20000, 3800, 0, 23800);
        boleta(TipoDte.BOLETA_AFECTA, 12, EstadoDte.ANULADO, DIA, 5000, 950, 0, 5950);
        // Boleta exenta (41) del dia.
        boleta(TipoDte.BOLETA_EXENTA, 5, EstadoDte.ACEPTADO, DIA, 0, 0, 8000, 8000);
        // Ruido: otra fecha y un borrador sin folio (ambos excluidos).
        boleta(TipoDte.BOLETA_AFECTA, 13, EstadoDte.ACEPTADO, OTRO_DIA, 30000, 5700, 0, 35700);
        boleta(TipoDte.BOLETA_AFECTA, null, EstadoDte.BORRADOR, DIA, 7000, 1330, 0, 8330);
    }

    @Test
    @DisplayName("agrega folios utilizados/anulados, rangos y montos por tipo")
    void agregaConsumoDelDia() {
        RcofResponse rep = rcofService.generar(empresaId, DIA);

        assertThat(rep.fecha()).isEqualTo(DIA);
        assertThat(rep.sinMovimiento()).isFalse();
        assertThat(rep.documentos()).hasSize(2);

        RcofPorTipo afecta = porTipo(rep, 39);
        assertThat(afecta.foliosUtilizados()).isEqualTo(2);
        assertThat(afecta.folioInicial()).isEqualTo(10);
        assertThat(afecta.folioFinal()).isEqualTo(11);
        assertThat(afecta.foliosAnulados()).isEqualTo(1);
        assertThat(afecta.folioAnuladoInicial()).isEqualTo(12);
        assertThat(afecta.folioAnuladoFinal()).isEqualTo(12);
        assertThat(afecta.foliosEmitidos()).isEqualTo(3);
        assertThat(afecta.montoNeto()).isEqualTo(30000);
        assertThat(afecta.montoIva()).isEqualTo(5700);
        assertThat(afecta.montoExento()).isZero();
        assertThat(afecta.montoTotal()).isEqualTo(35700); // el anulado (5950) NO suma

        RcofPorTipo exenta = porTipo(rep, 41);
        assertThat(exenta.foliosUtilizados()).isEqualTo(1);
        assertThat(exenta.folioInicial()).isEqualTo(5);
        assertThat(exenta.folioFinal()).isEqualTo(5);
        assertThat(exenta.foliosAnulados()).isZero();
        assertThat(exenta.folioAnuladoInicial()).isNull();
        assertThat(exenta.montoExento()).isEqualTo(8000);
        assertThat(exenta.montoTotal()).isEqualTo(8000);

        assertThat(rep.totales().foliosUtilizados()).isEqualTo(3);
        assertThat(rep.totales().foliosEmitidos()).isEqualTo(4);
        assertThat(rep.totales().montoTotal()).isEqualTo(43700);
    }

    @Test
    @DisplayName("un dia sin boletas devuelve dos entradas en cero y sinMovimiento")
    void reporteDiaSinBoletas() {
        RcofResponse rep = rcofService.generar(empresaId, LocalDate.of(2020, 1, 1));

        assertThat(rep.documentos()).hasSize(2);
        assertThat(rep.sinMovimiento()).isTrue();
        assertThat(porTipo(rep, 39).foliosEmitidos()).isZero();
        assertThat(porTipo(rep, 39).folioInicial()).isNull();
        assertThat(porTipo(rep, 41).foliosEmitidos()).isZero();
        assertThat(rep.totales().montoTotal()).isZero();
    }

    @Test
    @DisplayName("genera el XML ConsumoFolios con tipos, rango anulado y header ISO-8859-1")
    void generaXmlNoVacio() {
        String xml = rcofService.generarXml(empresaId, DIA);

        assertThat(xml).contains("<ConsumoFolios");
        assertThat(xml).contains("<TipoDocumento>39</TipoDocumento>");
        assertThat(xml).contains("<TipoDocumento>41</TipoDocumento>");
        assertThat(xml).contains("<RangoAnulados>");
        assertThat(xml).contains("ISO-8859-1");
    }

    private RcofPorTipo porTipo(RcofResponse rep, int tipoDocumento) {
        return rep.documentos().stream()
                .filter(d -> d.tipoDocumento() == tipoDocumento)
                .findFirst()
                .orElseThrow();
    }

    private void boleta(TipoDte tipo, Integer folio, EstadoDte estado, LocalDate fecha,
                        long neto, long iva, long exento, long total) {
        documentoRepository.save(DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(tipo)
                .folio(folio != null ? folio.longValue() : null)
                .estado(estado)
                .fechaEmision(fecha)
                .receptorRut("66666666-6")
                .receptorRazonSocial("Consumidor final")
                .neto(neto)
                .iva(iva)
                .exento(exento)
                .total(total)
                .tasaIva(19.0)
                .build());
    }
}
