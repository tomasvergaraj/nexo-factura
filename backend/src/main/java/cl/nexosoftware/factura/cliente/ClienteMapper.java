package cl.nexosoftware.factura.cliente;

import cl.nexosoftware.factura.cliente.ClienteDtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface ClienteMapper {
    Cliente toEntity(ClienteRequest req);
    ClienteResponse toResponse(Cliente cliente);
    void actualizar(@MappingTarget Cliente cliente, ClienteRequest req);
}
