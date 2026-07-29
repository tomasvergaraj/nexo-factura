package cl.nexosoftware.factura.rcof;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Firma del RCOF: el archivo que pide el set de pruebas de certificacion de
 * boletas. Cubre lo que no se puede verificar sin base de datos —la secuencia
 * del dia, que arranca en 1 y sube al rehacer— y el rechazo del dia sin
 * movimiento, que no tiene consumo que declarar.
 *
 * El envio al SII no se prueba porque no existe: la Res. Ex. SII N°53 de 2022
 * elimino esa obligacion (ver ROADMAP §15).
 */
class RcofFirmaServiceIT extends AbstractIntegrationTest {

    private static final LocalDate DIA = LocalDate.of(2026, 6, 26);
    private static final LocalDate DIA_VACIO = LocalDate.of(2026, 6, 20);

    @Autowired private RcofFirmaService firmaService;
    @Autowired private RcofService rcofService;
    @Autowired private RcofFirmadoRepository firmadoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private DocumentoRepository documentoRepository;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        // Con resolucion: la caratula del ConsumoFolios exige FchResol/NroResol,
        // igual que la del libro IECV. Sin ellos el servicio falla antes de firmar.
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut(rutUnicoDeTest())
                .razonSocial("Empresa RCOF firmado")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .fchResol(LocalDate.of(2014, 8, 22))
                .nroResol(80)
                .build());
        empresaId = empresa.getId();

        boleta(10, EstadoDte.ACEPTADO, 10000, 1900, 11900);
        boleta(11, EstadoDte.ANULADO, 5000, 950, 5950);
    }

    @Test
    @DisplayName("firma el ConsumoFolios del dia y registra la secuencia 1")
    void firmaYRegistra() {
        String xml = firmaService.xmlFirmado(empresaId, DIA, null);

        assertThat(xml).contains("<ConsumoFolios")
                .contains("<DocumentoConsumoFolios ID=\"NexoRCOF\">")
                .contains("<SecEnvio>1</SecEnvio>")
                .contains("<Signature")    // el XSD la exige; sin ella no habria pasado la validacion
                // La resolucion de la empresa llega a la caratula (no un placeholder).
                .contains("<FchResol>2014-08-22</FchResol>")
                .contains("<NroResol>80</NroResol>")
                .contains("<TmstFirmaEnv>");
        assertThat(firmadoRepository.findFirstByEmpresaIdAndFechaOrderBySecEnvioDesc(empresaId, DIA))
                .get()
                .satisfies(r -> {
                    assertThat(r.getSecEnvio()).isEqualTo(1);
                    assertThat(r.getTmstFirma()).isNotNull();
                });
    }

    @Test
    @DisplayName("rehacer el mismo dia declara la secuencia siguiente, no otra vez la 1")
    void secuenciaIncrementaAlRehacer() {
        firmaService.xmlFirmado(empresaId, DIA, null);

        assertThat(rcofService.generar(empresaId, DIA).secEnvio()).isEqualTo(2);
        assertThat(firmaService.xmlFirmado(empresaId, DIA, null)).contains("<SecEnvio>2</SecEnvio>");
        assertThat(firmaService.xmlFirmado(empresaId, DIA, null)).contains("<SecEnvio>3</SecEnvio>");
    }

    @Test
    @DisplayName("la secuencia es por dia: otro dia vuelve a empezar en 1")
    void secuenciaPorDia() {
        firmaService.xmlFirmado(empresaId, DIA, null);

        LocalDate otroDia = DIA.plusDays(1);
        boletaEn(otroDia, 12, EstadoDte.ACEPTADO, 3000, 570, 3570);

        assertThat(firmaService.xmlFirmado(empresaId, otroDia, null)).contains("<SecEnvio>1</SecEnvio>");
    }

    @Test
    @DisplayName("el override rehace un archivo con la misma secuencia, sin declarar una correccion")
    void overrideDeSecuencia() {
        firmaService.xmlFirmado(empresaId, DIA, null);   // 1

        // El archivo anterior nunca se presento: se rehace como 1, no como 2.
        assertThat(firmaService.xmlFirmado(empresaId, DIA, 1)).contains("<SecEnvio>1</SecEnvio>");
        // Y la propuesta automatica sigue siendo la siguiente de la mayor vista.
        assertThat(rcofService.generar(empresaId, DIA).secEnvio()).isEqualTo(2);
    }

    @Test
    @DisplayName("un SecEnvio fuera del rango del esquema (1-999) se rechaza antes de firmar")
    void secuenciaFueraDeRango() {
        assertThatThrownBy(() -> firmaService.xmlFirmado(empresaId, DIA, 1000))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("SecEnvio");
    }

    @Test
    @DisplayName("un dia sin boletas no se firma: no hay consumo que declarar")
    void diaSinMovimiento() {
        assertThatThrownBy(() -> firmaService.xmlFirmado(empresaId, DIA_VACIO, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no se emitieron boletas");
        assertThat(firmadoRepository.findFirstByEmpresaIdAndFechaOrderBySecEnvioDesc(empresaId, DIA_VACIO))
                .isEmpty();
    }

    private void boleta(long folio, EstadoDte estado, long neto, long iva, long total) {
        boletaEn(DIA, folio, estado, neto, iva, total);
    }

    private void boletaEn(LocalDate fecha, long folio, EstadoDte estado, long neto, long iva, long total) {
        documentoRepository.save(DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.BOLETA_AFECTA)
                .folio(folio)
                .estado(estado)
                .fechaEmision(fecha)
                .receptorRut("66666666-6")
                .receptorRazonSocial("Consumidor final")
                .neto(neto)
                .iva(iva)
                .exento(0)
                .total(total)
                .tasaIva(19.0)
                .build());
    }
}
