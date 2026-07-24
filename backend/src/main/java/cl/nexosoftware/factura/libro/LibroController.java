package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.libro.LibroDtos.EnvioLibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroEnvioResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroPendienteResponse;
import cl.nexosoftware.factura.libro.LibroDtos.LibroResponse;
import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.tributario.SiiGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/empresas/{empresaId}/libros")
@RequiredArgsConstructor
@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")
@Tag(name = "Libros IECV", description = "Libros de compra y venta del periodo tributario")
public class LibroController {

    private final LibroService service;
    private final LibroEnvioService envioService;
    private final RevisionLibroService revisionService;

    @GetMapping("/{tipo}")
    @Operation(summary = "Libro del periodo (YYYY-MM; por defecto el mes actual). tipo: ventas | compras. "
            + "Ventas: boletas resumidas, rechazados excluidos. Compras: documentos recibidos registrados; "
            + "fctProp = factor de proporcionalidad del IVA uso comun (p.ej. 0.60).")
    public LibroResponse libro(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam(required = false) Double fctProp) {
        return service.libro(empresaId, operacion(tipo), normalizar(periodo), fctProp);
    }

    @GetMapping(value = "/{tipo}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Libro del periodo como XML LibroCompraVenta oficial, SIN firmar (inspeccion).")
    public ResponseEntity<String> libroXml(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam(required = false) Double fctProp) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(service.libroXml(empresaId, operacion(tipo), normalizar(periodo), fctProp));
    }

    @GetMapping(value = "/{tipo}/xml-firmado", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Libro del periodo firmado (XMLDSig) y validado contra LibroCV_v10, sin enviarlo. "
            + "tipoLibro: MENSUAL (defecto) | ESPECIAL (certificacion, con folioNotificacion).")
    public ResponseEntity<String> libroXmlFirmado(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam(required = false) Double fctProp,
            @RequestParam(defaultValue = "MENSUAL") String tipoLibro,
            @RequestParam(required = false) Long folioNotificacion) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(envioService.xmlFirmado(empresaId, operacion(tipo), normalizar(periodo),
                        fctProp, tipoLibro, folioNotificacion));
    }

    @PostMapping("/{tipo}/enviar")
    @Operation(summary = "Firma el libro del periodo y lo envia al SII (canal clasico); devuelve el TrackID. "
            + "Para el set de certificacion: tipoLibro=ESPECIAL y folioNotificacion=numero de atencion.")
    public LibroEnvioResponse enviar(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam(required = false) Double fctProp,
            @RequestParam(defaultValue = "MENSUAL") String tipoLibro,
            @RequestParam(required = false) Long folioNotificacion) {
        return envioService.enviar(empresaId, operacion(tipo), normalizar(periodo),
                fctProp, tipoLibro, folioNotificacion);
    }

    @GetMapping("/{tipo}/envios")
    @Operation(summary = "Envios ya registrados del libro del periodo (mas reciente primero), "
            + "con su TrackID y ultimo estado consultado.")
    public List<EnvioLibroResponse> envios(
            @PathVariable Long empresaId,
            @PathVariable String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return envioService.listar(empresaId, operacion(tipo), normalizar(periodo));
    }

    @GetMapping("/envios/{trackId}/estado")
    @Operation(summary = "Estado del envio de un libro por TrackID (QueryEstUp del canal clasico).")
    public Map<String, String> estadoEnvio(@PathVariable Long empresaId, @PathVariable String trackId) {
        SiiGateway.EstadoEnvio estado = envioService.estadoEnvio(empresaId, trackId);
        return Map.of("trackId", trackId, "estado", estado.name());
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Libros pendientes de envio detectados por la revision automatica del mes anterior: "
            + "PREPARADO (listo para enviar) o ERROR (con el motivo). No incluye los ya enviados.")
    public List<LibroPendienteResponse> pendientes(@PathVariable Long empresaId) {
        return revisionService.listarPendientes(empresaId);
    }

    private static TipoOperacion operacion(String tipo) {
        return switch (tipo) {
            case "ventas" -> TipoOperacion.VENTA;
            case "compras" -> TipoOperacion.COMPRA;
            default -> throw RecursoNoEncontradoException.de("Libro", tipo);
        };
    }

    private static YearMonth normalizar(YearMonth periodo) {
        return periodo != null ? periodo : YearMonth.now();
    }
}
