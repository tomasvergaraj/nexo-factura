package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.documento.TipoDte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public final class CafDtos {

    private CafDtos() {}

    /**
     * Alta de un CAF. En produccion estos datos se extraen del XML del CAF que
     * entrega el SII; aqui se aceptan tambien de forma explicita para pruebas.
     */
    public record CafRequest(
            @NotNull TipoDte tipoDte,
            @NotNull @Positive Long folioDesde,
            @NotNull @Positive Long folioHasta,
            String xmlCaf,
            LocalDate fechaAutorizacion,
            LocalDate fechaVencimiento
    ) {}

    public record CafResponse(
            Long id,
            TipoDte tipoDte,
            long folioDesde,
            long folioHasta,
            long folioActual,
            long foliosDisponibles,
            boolean agotado,
            LocalDate fechaVencimiento
    ) {
        public static CafResponse de(Caf c) {
            return new CafResponse(c.getId(), c.getTipoDte(), c.getFolioDesde(), c.getFolioHasta(),
                    c.getFolioActual(), c.foliosDisponibles(), c.isAgotado(), c.getFechaVencimiento());
        }
    }
}
