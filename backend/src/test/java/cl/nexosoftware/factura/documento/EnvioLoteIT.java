package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.documento.DocumentoDtos.LoteEnvioResponse;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Envio de varios documentos en UN sobre (lo que exigen los sets de pruebas de
 * certificacion). Desde que el canal de boleta tambien admite lotes, el tipo del
 * primer documento decide el canal, asi que un lote MIXTO se corta antes de
 * salir: EnvioDTE y EnvioBOLETA son sobres distintos y el SII lo rechazaria.
 */
class EnvioLoteIT extends AbstractIntegrationTest {

    @Autowired private DocumentoService documentoService;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private EmpresaRepository empresaRepository;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        empresaId = empresaRepository.save(Empresa.builder()
                .rut(rutUnicoDeTest())
                .razonSocial("Empresa lote")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build()).getId();
    }

    @Test
    @DisplayName("las boletas del set van en un lote y todas quedan con el mismo TrackID")
    void loteDeBoletas() {
        Long uno = documento(TipoDte.BOLETA_AFECTA, 1L).getId();
        Long dos = documento(TipoDte.BOLETA_AFECTA, 2L).getId();

        LoteEnvioResponse res = documentoService.enviarLoteSii(empresaId, List.of(uno, dos));

        assertThat(res.trackId()).isNotBlank();
        assertThat(res.documentos()).hasSize(2);
        assertThat(documentoRepository.findById(uno).orElseThrow().getTrackId())
                .isEqualTo(res.trackId());
        assertThat(documentoRepository.findById(dos).orElseThrow().getTrackId())
                .isEqualTo(res.trackId());
    }

    @Test
    @DisplayName("un lote que mezcla boleta con factura se rechaza antes de enviarlo")
    void loteMixtoSeRechaza() {
        Long boleta = documento(TipoDte.BOLETA_AFECTA, 3L).getId();
        Long factura = documento(TipoDte.FACTURA_AFECTA, 4L).getId();

        assertThatThrownBy(() -> documentoService.enviarLoteSii(empresaId, List.of(boleta, factura)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("canal");
        // Nada cambio: el corte es previo al envio.
        assertThat(documentoRepository.findById(boleta).orElseThrow().getTrackId()).isNull();
        assertThat(documentoRepository.findById(factura).orElseThrow().getEstado())
                .isEqualTo(EstadoDte.FIRMADO);
    }

    @Test
    @DisplayName("un lote vacio se rechaza")
    void loteVacio() {
        assertThatThrownBy(() -> documentoService.enviarLoteSii(empresaId, List.of()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private DocumentoTributario documento(TipoDte tipo, long folio) {
        return documentoRepository.save(DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(tipo)
                .folio(folio)
                .estado(EstadoDte.FIRMADO)
                .fechaEmision(LocalDate.now())
                .receptorRut("66666666-6")
                .receptorRazonSocial("Consumidor final")
                .neto(10000)
                .iva(1900)
                .exento(0)
                .total(11900)
                .tasaIva(19.0)
                // enviarLoteSii solo exige que el XML firmado exista.
                .xmlDte("<DTE version=\"1.0\"><Documento ID=\"T" + tipo.getCodigo()
                        + "F" + folio + "\"/></DTE>")
                .build());
    }
}
