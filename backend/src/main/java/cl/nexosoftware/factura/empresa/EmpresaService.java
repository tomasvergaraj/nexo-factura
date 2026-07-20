package cl.nexosoftware.factura.empresa;

import cl.nexosoftware.factura.auth.SecurityUtils;
import cl.nexosoftware.factura.auth.UsuarioPrincipal;
import cl.nexosoftware.factura.auth.UsuarioRepository;
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
    private final UsuarioRepository usuarioRepository;

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
        Empresa empresa = repository.save(mapper.toEntity(req));
        // Onboarding: si quien la crea aun no tiene empresa (registro recien
        // hecho), queda asociado a ella. El claim empresaId del JWT se
        // actualiza en el siguiente /auth/refresh.
        UsuarioPrincipal actual = SecurityUtils.currentUser();
        if (actual != null && actual.getEmpresaId() == null) {
            usuarioRepository.findById(actual.getId())
                    .filter(u -> u.getEmpresa() == null)
                    .ifPresent(u -> {
                        u.setEmpresa(empresa);
                        usuarioRepository.save(u);
                    });
        }
        return mapper.toResponse(empresa);
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
