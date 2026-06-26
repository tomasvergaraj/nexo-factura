package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.common.validation.Rut;
import cl.nexosoftware.factura.empresa.EmpresaDtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(imports = Rut.class)
public interface EmpresaMapper {

    // El RUT se almacena siempre canonico (sin puntos) para que la emision (XSD) y
    // la deduplicacion por la restriccion unica no dependan del formato de entrada.
    @Mapping(target = "rut", expression = "java(Rut.normalizar(req.rut()))")
    Empresa toEntity(EmpresaRequest req);

    EmpresaResponse toResponse(Empresa empresa);

    @Mapping(target = "rut", expression = "java(Rut.normalizar(req.rut()))")
    void actualizar(@MappingTarget Empresa empresa, EmpresaRequest req);
}
