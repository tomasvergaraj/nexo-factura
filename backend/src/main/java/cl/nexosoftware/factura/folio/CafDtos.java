package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.documento.TipoDte;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public final class CafDtos {

    private CafDtos() {}

    /**
     * Alta de un CAF: se sube el XML tal como lo entrega el SII y TODO lo demas
     * (tipo, rango, fechas, claves) se deriva del parseo. Ya no se aceptan campos
     * manuales: un CAF sin su XML real no puede timbrar (el TED exige el bloque
     * CAF y su clave privada).
     */
    public record CafRequest(@NotBlank String xmlCaf) {}

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
