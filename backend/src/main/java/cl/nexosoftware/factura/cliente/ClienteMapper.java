package cl.nexosoftware.factura.cliente;

import cl.nexosoftware.factura.cliente.ClienteDtos.*;
import cl.nexosoftware.factura.common.validation.Rut;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(imports = Rut.class)
public interface ClienteMapper {

    // El RUT se almacena siempre canonico (sin puntos): ver EmpresaMapper.
    @Mapping(target = "rut", expression = "java(Rut.normalizar(req.rut()))")
    Cliente toEntity(ClienteRequest req);

    ClienteResponse toResponse(Cliente cliente);

    @Mapping(target = "rut", expression = "java(Rut.normalizar(req.rut()))")
    void actualizar(@MappingTarget Cliente cliente, ClienteRequest req);
}
