package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.empresa.EmpresaDtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface EmpresaMapper {
    Empresa toEntity(EmpresaRequest req);
    EmpresaResponse toResponse(Empresa empresa);
    void actualizar(@MappingTarget Empresa empresa, EmpresaRequest req);
}
