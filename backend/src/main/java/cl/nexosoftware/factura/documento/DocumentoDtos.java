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
            // 0 permitido (linea de regalo: PrcItem se omite en el XML); negativo no.
            @PositiveOrZero Long precioUnitario,
            @PositiveOrZero Long descuentoMonto,
            Boolean afecto,
            // Codigo de otro impuesto (catalogo TipoImpuesto); null = solo IVA. La
            // validez del codigo y su compatibilidad con el tipo/linea se chequean
            // en DocumentoService (regla de negocio, no Bean Validation).
            Integer codImpAdic
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
            Integer codImpAdic,
            long montoLinea
    ) {}

    /** Desglose de un otro-impuesto del documento (bloque ImptoReten del XML). */
    public record ImpuestoResponse(
            int codigo,
            String nombre,
            double tasa,
            boolean esRetencion,
            long base,
            long monto
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
            long impuestosAdicionales,
            long ivaRetenido,
            long total,
            String trackId,
            String observacion,
            List<LineaResponse> lineas,
            OffsetDateTime creadoEn,
            List<ReferenciaResponse> referencias,
            List<ImpuestoResponse> impuestos,
            String sello,
            // Traza del envio al SII (contingencia): intentos realizados, momento
            // del ultimo intento y motivo del ultimo fallo (null si fue exitoso).
            int intentosEnvio,
            OffsetDateTime ultimoEnvioEn,
            String ultimoErrorEnvio
    ) {}

    /** Resultado del reenvio masivo de documentos EN_CONTINGENCIA. */
    public record ReenvioMasivoResponse(
            int procesados,
            int enviados,
            int enContingencia,
            List<DocumentoResumen> documentos
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
