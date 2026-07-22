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
            @Valid List<ReferenciaRequest> referencias,
            // Descuento global % sobre las lineas afectas (DscRcgGlobal); null = sin
            // descuento. Rango y compatibilidad con el tipo se validan en el servicio.
            @Positive Double descuentoGlobalPct,
            // Numero de caso del set de pruebas de certificacion (ej: "4965879-1");
            // el DTE lo referencia con TpoDocRef=SET. Null = emision normal.
            @Size(max = 18) @Pattern(regexp = "\\d+-\\d+",
                    message = "setCaso debe tener la forma <atencion>-<caso>, ej: 4965879-1")
            String setCaso
    ) {
        /** Forma sin descuento global (compatibilidad con los usos previos). */
        public CrearDocumentoRequest(TipoDte tipoDte, Long clienteId, LocalDate fechaEmision,
                                     String observacion, List<LineaRequest> lineas,
                                     List<ReferenciaRequest> referencias) {
            this(tipoDte, clienteId, fechaEmision, observacion, lineas, referencias, null, null);
        }

        /** Forma sin caso de certificacion (compatibilidad con los usos previos). */
        public CrearDocumentoRequest(TipoDte tipoDte, Long clienteId, LocalDate fechaEmision,
                                     String observacion, List<LineaRequest> lineas,
                                     List<ReferenciaRequest> referencias, Double descuentoGlobalPct) {
            this(tipoDte, clienteId, fechaEmision, observacion, lineas, referencias,
                    descuentoGlobalPct, null);
        }
    }

    public record LineaRequest(
            Long productoId,
            String nombre,
            @NotNull @Positive Double cantidad,
            // 0 permitido (linea de regalo: PrcItem se omite en el XML); negativo no.
            @PositiveOrZero Long precioUnitario,
            @PositiveOrZero Long descuentoMonto,
            // Descuento % de la linea (DescuentoPct); excluyente con descuentoMonto.
            @Positive Double descuentoPct,
            Boolean afecto,
            // Codigo de otro impuesto (catalogo TipoImpuesto); null = solo IVA. La
            // validez del codigo y su compatibilidad con el tipo/linea se chequean
            // en DocumentoService (regla de negocio, no Bean Validation).
            Integer codImpAdic,
            // Unidad de medida (UnmdItem, max 4 chars); null = "UN". El revisor del
            // set compara la unidad contra el caso (ej: "Hora" en la exenta).
            @Size(max = 4) String unidad
    ) {
        /** Forma sin descuento porcentual (compatibilidad con los usos previos). */
        public LineaRequest(Long productoId, String nombre, Double cantidad, Long precioUnitario,
                            Long descuentoMonto, Boolean afecto, Integer codImpAdic) {
            this(productoId, nombre, cantidad, precioUnitario, descuentoMonto, null, afecto, codImpAdic, null);
        }

        /** Forma sin unidad de medida (compatibilidad con los usos previos). */
        public LineaRequest(Long productoId, String nombre, Double cantidad, Long precioUnitario,
                            Long descuentoMonto, Double descuentoPct, Boolean afecto, Integer codImpAdic) {
            this(productoId, nombre, cantidad, precioUnitario, descuentoMonto, descuentoPct, afecto, codImpAdic, null);
        }
    }

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
            Double descuentoPct,
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
            // Descuento global sobre afectos: porcentaje y monto rebajado (0 si no hay).
            Double descuentoGlobalPct,
            long descuentoGlobal,
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

    /** Resultado del envio de un LOTE (un sobre con varios documentos, un TrackID). */
    public record LoteEnvioResponse(
            String trackId,
            List<DocumentoResumen> documentos
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
