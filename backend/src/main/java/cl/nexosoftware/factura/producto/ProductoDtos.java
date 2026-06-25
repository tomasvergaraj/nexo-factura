package cl.nexosoftware.factura.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class ProductoDtos {

    private ProductoDtos() {}

    public record ProductoRequest(
            String codigo,
            @NotBlank String nombre,
            @NotNull @PositiveOrZero Long precioNeto,
            String unidad,
            boolean afecto
    ) {}

    public record ProductoResponse(
            Long id,
            String codigo,
            String nombre,
            Long precioNeto,
            String unidad,
            boolean afecto,
            boolean activo
    ) {}
}
