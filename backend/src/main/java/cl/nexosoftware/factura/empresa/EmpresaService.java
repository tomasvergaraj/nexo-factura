package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.empresa.EmpresaDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository repository;
    private final EmpresaMapper mapper;

    @Transactional(readOnly = true)
    public List<EmpresaResponse> listar() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmpresaResponse obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Transactional
    public EmpresaResponse crear(EmpresaRequest req) {
        Empresa empresa = mapper.toEntity(req);
        return mapper.toResponse(repository.save(empresa));
    }

    @Transactional
    public EmpresaResponse actualizar(Long id, EmpresaRequest req) {
        Empresa empresa = buscar(id);
        mapper.actualizar(empresa, req);
        return mapper.toResponse(repository.save(empresa));
    }

    @Transactional(readOnly = true)
    public Empresa buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Empresa", id));
    }
}
