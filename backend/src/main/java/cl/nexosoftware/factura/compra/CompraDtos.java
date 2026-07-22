package cl.nexosoftware.factura.compra;

import cl.nexosoftware.factura.common.validation.RutValido;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class CompraDtos {

    private CompraDtos() {}

    public record CompraRequest(
            @NotNull Integer tipoDte,
            @NotNull @Positive Long folio,
            @NotBlank @RutValido String rutProveedor,
            @NotBlank String razonSocial,
            @NotNull LocalDate fechaEmision,
            @NotNull @PositiveOrZero Long neto,
            @NotNull @PositiveOrZero Long exento,
            @NotNull @PositiveOrZero Long iva,
            // IVA retenido por el comprador (cambio de sujeto, factura de compra
            // 46); opcional, null = 0. Resta del total.
            @PositiveOrZero Long ivaRetenido,
            @NotNull @Positive Long total,
            String observacion,
            // IVA de uso comun (credito proporcional con el factor del periodo).
            Boolean ivaUsoComun,
            // Codigo IVA no recuperable (1/2/3/4/9); null = con derecho a credito.
            Integer codIvaNoRec
    ) {
        /** Forma sin uso comun / no recuperable (compatibilidad con usos previos). */
        public CompraRequest(Integer tipoDte, Long folio, String rutProveedor, String razonSocial,
                             LocalDate fechaEmision, Long neto, Long exento, Long iva,
                             Long ivaRetenido, Long total, String observacion) {
            this(tipoDte, folio, rutProveedor, razonSocial, fechaEmision, neto, exento, iva,
                    ivaRetenido, total, observacion, null, null);
        }

        public long ivaRetenidoODefecto() {
            return ivaRetenido != null ? ivaRetenido : 0L;
        }

        public boolean esIvaUsoComun() {
            return Boolean.TRUE.equals(ivaUsoComun);
        }
    }

    public record CompraResponse(
            Long id,
            int tipoDte,
            long folio,
            String rutProveedor,
            String razonSocial,
            LocalDate fechaEmision,
            long neto,
            long exento,
            long iva,
            long ivaRetenido,
            long total,
            String observacion,
            boolean ivaUsoComun,
            Integer codIvaNoRec,
            OffsetDateTime creadoEn
    ) {}
}
