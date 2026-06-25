package cl.nexosoftware.factura.cliente;

import cl.nexosoftware.factura.cliente.ClienteDtos.*;
import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repository;
    private final ClienteMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<ClienteResponse> listar(Long empresaId, String q, Pageable pageable) {
        Page<Cliente> page = StringUtils.hasText(q)
                ? repository.findByEmpresaIdAndRazonSocialContainingIgnoreCase(empresaId, q, pageable)
                : repository.findByEmpresaId(empresaId, pageable);
        return PageResponse.de(page.map(mapper::toResponse));
    }

    @Transactional
    public ClienteResponse crear(Long empresaId, ClienteRequest req) {
        Cliente cliente = mapper.toEntity(req);
        cliente.setEmpresaId(empresaId);
        return mapper.toResponse(repository.save(cliente));
    }

    @Transactional
    public ClienteResponse actualizar(Long empresaId, Long id, ClienteRequest req) {
        Cliente cliente = repository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", id));
        mapper.actualizar(cliente, req);
        return mapper.toResponse(repository.save(cliente));
    }

    @Transactional(readOnly = true)
    public Cliente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", id));
    }
}
