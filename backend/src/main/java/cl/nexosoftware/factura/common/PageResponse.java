package cl.nexosoftware.factura.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Envoltorio liviano para respuestas paginadas. */
public record PageResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {
    public static <T> PageResponse<T> de(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
