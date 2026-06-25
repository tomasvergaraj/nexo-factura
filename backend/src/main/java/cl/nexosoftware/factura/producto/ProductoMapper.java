package cl.nexosoftware.factura.producto;

import cl.nexosoftware.factura.producto.ProductoDtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface ProductoMapper {
    Producto toEntity(ProductoRequest req);
    ProductoResponse toResponse(Producto producto);
    void actualizar(@MappingTarget Producto producto, ProductoRequest req);
}
