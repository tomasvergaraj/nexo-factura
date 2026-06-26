package cl.nexosoftware.factura.documento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentoDtos {

    private DocumentoDtos() {}

    public record CrearDocumentoRequest(
            @NotNull TipoDte tipoDte,
            Long clienteId,
            LocalDate fechaEmision,
            String observacion,
            @NotEmpty(message = "El documento debe tener al menos una linea") @Valid List<LineaRequest> lineas,
            @Valid List<ReferenciaRequest> referencias
    ) {}

    public record LineaRequest(
            Long productoId,
            String nombre,
            @NotNull @Positive Double cantidad,
            Long precioUnitario,
            @PositiveOrZero Long descuentoMonto,
            Boolean afecto
    ) {}

    public record ReferenciaRequest(
            @NotNull Integer tipoDocumentoRef,
            @NotNull Long folioRef,
            @NotNull LocalDate fechaRef,
            @NotNull TipoReferencia tipoReferencia,
            @NotBlank String razon
    ) {}

    public record LineaResponse(
            int numeroLinea,
            String nombre,
            double cantidad,
            String unidad,
            long precioUnitario,
            long descuentoMonto,
            boolean afecto,
            long montoLinea
    ) {}

    public record ReferenciaResponse(
            int tipoDocumentoRef,
            long folioRef,
            LocalDate fechaRef,
            TipoReferencia tipoReferencia,
            int codigoReferencia,
            String razon
    ) {}

    public record DocumentoResponse(
            Long id,
            TipoDte tipoDte,
            int codigoTipo,
            Long folio,
            EstadoDte estado,
            LocalDate fechaEmision,
            String receptorRut,
            String receptorRazonSocial,
            long neto,
            long exento,
            double tasaIva,
            long iva,
            long total,
            String trackId,
            String observacion,
            List<LineaResponse> lineas,
            OffsetDateTime creadoEn,
            List<ReferenciaResponse> referencias,
            String sello
    ) {}

    /** Vista compacta para listados. */
    public record DocumentoResumen(
            Long id,
            TipoDte tipoDte,
            int codigoTipo,
            Long folio,
            EstadoDte estado,
            LocalDate fechaEmision,
            String receptorRazonSocial,
            long total
    ) {}
}
