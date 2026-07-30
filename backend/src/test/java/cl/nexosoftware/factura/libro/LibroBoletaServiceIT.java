package cl.nexosoftware.factura.libro;

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
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Libro de Boletas del periodo: que tome SOLO las boletas del mes, que el folio
 * anulado vaya marcado y sin montos (la misma regla del RCOF, para que los dos
 * documentos del mismo periodo no se contradigan) y que exija el folio de la
 * notificacion del SII, sin el cual el esquema no admite el libro.
 */
class LibroBoletaServiceIT extends AbstractIntegrationTest {

    private static final YearMonth PERIODO = YearMonth.of(2026, 6);
    private static final long NOTIFICACION = 4965879L;

    @Autowired private LibroBoletaService service;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private DocumentoRepository documentoRepository;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        empresaId = empresaRepository.save(Empresa.builder()
                .rut(rutUnicoDeTest())
                .razonSocial("Empresa libro boletas")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .fchResol(LocalDate.of(2014, 8, 22))
                .nroResol(80)
                .build()).getId();

        LocalDate dia = LocalDate.of(2026, 6, 26);
        boleta(TipoDte.BOLETA_AFECTA, 156, EstadoDte.ACEPTADO, dia, 10000, 1900, 0, 11900);
        boleta(TipoDte.BOLETA_AFECTA, 157, EstadoDte.ANULADO, dia, 5000, 950, 0, 5950);
        boleta(TipoDte.BOLETA_EXENTA, 158, EstadoDte.ACEPTADO, dia, 0, 0, 8000, 8000);
        // Ruido: una factura del mismo mes y una boleta de otro mes.
        boleta(TipoDte.FACTURA_AFECTA, 900, EstadoDte.ACEPTADO, dia, 100000, 19000, 0, 119000);
        boleta(TipoDte.BOLETA_AFECTA, 159, EstadoDte.ACEPTADO,
                LocalDate.of(2026, 7, 1), 7000, 1330, 0, 8330);
    }

    @Test
    @DisplayName("arma el libro del periodo con las boletas del mes, firmado y validado")
    void libroDelPeriodo() {
        String xml = service.xmlFirmado(empresaId, PERIODO, NOTIFICACION);

        assertThat(xml).contains("<LibroBoleta")
                .contains("<TipoLibro>ESPECIAL</TipoLibro>")
                .contains("<FolioNotificacion>4965879</FolioNotificacion>")
                .contains("<PeriodoTributario>2026-06</PeriodoTributario>")
                .contains("<FchResol>2014-08-22</FchResol>")
                .contains("<NroResol>80</NroResol>")
                .contains("<Signature");
        // Las tres boletas del mes, y nada mas.
        assertThat(xml).contains("<FolioDoc>156</FolioDoc>")
                .contains("<FolioDoc>157</FolioDoc>")
                .contains("<FolioDoc>158</FolioDoc>")
                .doesNotContain("<FolioDoc>900</FolioDoc>")   // la factura no es del libro de boletas
                .doesNotContain("<FolioDoc>159</FolioDoc>");  // otro periodo
        // El anulado se cuenta como documento pero no suma monto.
        assertThat(xml).contains("<Anulado>A</Anulado>")
                .contains("<TotDoc>2</TotDoc>")
                .contains("<TotAnulado>1</TotAnulado>")
                .contains("<TotMntTotal>11900</TotMntTotal>");
    }

    @Test
    @DisplayName("sin folio de notificacion no se genera: el esquema solo admite el libro ESPECIAL")
    void exigeFolioDeNotificacion() {
        assertThatThrownBy(() -> service.xmlFirmado(empresaId, PERIODO, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("notificacion");
    }

    @Test
    @DisplayName("un periodo sin boletas no se genera: el libro quedaria sin detalle")
    void periodoSinBoletas() {
        assertThatThrownBy(() -> service.xmlFirmado(empresaId, YearMonth.of(2020, 1), NOTIFICACION))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("No hay boletas");
    }

    private void boleta(TipoDte tipo, long folio, EstadoDte estado, LocalDate fecha,
                        long neto, long iva, long exento, long total) {
        documentoRepository.save(DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(tipo)
                .folio(folio)
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
