package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import cl.nexosoftware.factura.documento.DocumentoDtos.*;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integracion de boletas (39/41): receptor "Consumidor final" cuando se
 * emiten sin cliente y desglose de IVA desde precios brutos (IVA incluido). Las
 * facturas/notas siguen exigiendo cliente.
 */
class BoletaConsumidorFinalIT extends AbstractIntegrationTest {

    @Autowired private DocumentoService documentoService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ClienteRepository clienteRepository;

    private Long empresaId;
    private Long clienteId;

    @BeforeEach
    void preparar() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("91000000-" + ThreadLocalRandom.current().nextInt(0, 9))
                .razonSocial("Empresa Boletas")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();

        Cliente cliente = clienteRepository.save(Cliente.builder()
                .empresaId(empresaId)
                .rut("77111222-3")
                .razonSocial("Cliente de prueba")
                .build());
        clienteId = cliente.getId();
    }

    @Test
    @DisplayName("boleta afecta sin cliente queda con receptor Consumidor final 66666666-6")
    void boletaSinClienteUsaConsumidorFinal() {
        DocumentoResponse boleta = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.BOLETA_AFECTA, null, null, "Boleta consumidor final",
                List.of(new LineaRequest(null, "Cafe", 1.0, 11900L, null, true)),
                null));

        assertThat(boleta.receptorRut()).isEqualTo("66666666-6");
        assertThat(boleta.receptorRazonSocial()).isEqualTo("Consumidor final");

        // Persistido (no solo en la respuesta inmediata).
        DocumentoResponse recargada = documentoService.obtener(empresaId, boleta.id());
        assertThat(recargada.receptorRut()).isEqualTo("66666666-6");
    }

    @Test
    @DisplayName("boleta afecta desglosa neto/IVA del precio bruto (11900 -> 10000 + 1900)")
    void boletaAfectaDesglosaBruto() {
        DocumentoResponse boleta = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.BOLETA_AFECTA, null, null, null,
                List.of(new LineaRequest(null, "Producto bruto", 1.0, 11900L, null, true)),
                null));

        assertThat(boleta.neto()).isEqualTo(10000);
        assertThat(boleta.iva()).isEqualTo(1900);
        assertThat(boleta.exento()).isZero();
        assertThat(boleta.total()).isEqualTo(11900);
    }

    @Test
    @DisplayName("boleta exenta sin cliente: todo el monto es exento, sin IVA")
    void boletaExentaSoloExento() {
        DocumentoResponse boleta = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.BOLETA_EXENTA, null, null, null,
                List.of(new LineaRequest(null, "Servicio exento", 1.0, 8000L, null, false)),
                null));

        assertThat(boleta.neto()).isZero();
        assertThat(boleta.iva()).isZero();
        assertThat(boleta.exento()).isEqualTo(8000);
        assertThat(boleta.total()).isEqualTo(8000);
        assertThat(boleta.receptorRut()).isEqualTo("66666666-6");
    }

    @Test
    @DisplayName("una factura sin cliente es rechazada (solo boletas admiten consumidor final)")
    void facturaSinClienteFalla() {
        CrearDocumentoRequest req = new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA, null, null, null,
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true)),
                null);

        assertThatThrownBy(() -> documentoService.crear(empresaId, req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("una factura con cliente real sigue funcionando (regresion)")
    void facturaConClienteOk() {
        DocumentoResponse factura = documentoService.crear(empresaId, new CrearDocumentoRequest(
                TipoDte.FACTURA_AFECTA, clienteId, null, null,
                List.of(new LineaRequest(null, "Servicio", 1.0, 10000L, null, true)),
                null));

        assertThat(factura.receptorRut()).isEqualTo("77111222-3");
        assertThat(factura.neto()).isEqualTo(10000);
        assertThat(factura.iva()).isEqualTo(1900);
        assertThat(factura.total()).isEqualTo(11900);
    }
}
