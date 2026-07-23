package cl.nexosoftware.factura.intercambio;

import cl.nexosoftware.factura.intercambio.IntercambioDtos.RespuestaIntercambioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Intercambio de informacion (Ley 19.983): recibe un sobre {@code EnvioDTE}
 * ajeno y devuelve los tres acuses firmados que el portal de postulantes pide
 * subir — Respuesta de Intercambio, Recibo de Mercaderias y Resultado
 * Aprobacion Comercial.
 *
 * El sobre se recibe como cuerpo binario y se decodifica en ISO-8859-1 (el
 * encoding que declara el DTE): decodificar como UTF-8 mutilaria acentos y
 * romperia la firma que se re-canoniza sobre esos bytes.
 */
@RestController
@RequestMapping("/api/empresas/{empresaId}/intercambio")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Intercambio", description = "Acuses de recepcion de DTE recibidos (Ley 19.983)")
public class IntercambioController {

    private final IntercambioService service;

    @PostMapping(value = "/responder",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Genera los 3 acuses firmados (Respuesta de Intercambio, Recibo de "
            + "Mercaderias, Resultado Aprobacion Comercial) para un sobre EnvioDTE recibido. "
            + "Acepta y rechaza cada DTE segun el RUT receptor.")
    public RespuestaIntercambioResponse responder(
            @PathVariable Long empresaId,
            @RequestParam(required = false) String nombreArchivo,
            @RequestBody byte[] sobre) {
        String xml = new String(sobre, StandardCharsets.ISO_8859_1);
        return service.responder(empresaId, xml, nombreArchivo);
    }
}
