package cl.nexosoftware.factura.producto;

import cl.nexosoftware.factura.common.PageResponse;
import cl.nexosoftware.factura.common.exception.RecursoNoEncontradoException;
import cl.nexosoftware.factura.producto.ProductoDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository repository;
    private final ProductoMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<ProductoResponse> listar(Long empresaId, String q, Pageable pageable) {
        Page<Producto> page = StringUtils.hasText(q)
                ? repository.findByEmpresaIdAndNombreContainingIgnoreCase(empresaId, q, pageable)
                : repository.findByEmpresaId(empresaId, pageable);
        return PageResponse.de(page.map(mapper::toResponse));
    }

    @Transactional
    public ProductoResponse crear(Long empresaId, ProductoRequest req) {
        Producto producto = mapper.toEntity(req);
        producto.setEmpresaId(empresaId);
        return mapper.toResponse(repository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest req) {
        Producto producto = buscar(id);
        mapper.actualizar(producto, req);
        return mapper.toResponse(repository.save(producto));
    }

    @Transactional(readOnly = true)
    public Producto buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id));
    }
}
