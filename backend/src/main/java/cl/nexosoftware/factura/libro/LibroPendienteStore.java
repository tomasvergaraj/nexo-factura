package cl.nexosoftware.factura.libro;

import cl.nexosoftware.factura.libro.LibroDtos.TipoOperacion;
import cl.nexosoftware.factura.libro.LibroPendiente.Estado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia transaccional del marcador de libro pendiente, en su PROPIA
 * transaccion.
 *
 * Es un bean aparte a proposito: la revision ({@link RevisionLibroService})
 * prepara el libro llamando a {@code xmlFirmado()}, que es {@code @Transactional};
 * si esa preparacion falla y compartiera transaccion con la escritura del
 * marcador, la transaccion quedaria marcada rollback-only y se perderia el
 * marcador (incluido el de otra operacion que si se preparo). Al escribir aca,
 * cada upsert/borrado va en una transaccion independiente, aislada del fallo.
 */
@Component
@RequiredArgsConstructor
class LibroPendienteStore {

    private final LibroPendienteRepository repository;

    /** Upsert del marcador (uno por empresa+periodo+operacion). */
    @Transactional
    public void guardar(Long empresaId, String periodo, TipoOperacion operacion,
                        Estado estado, String detalle) {
        LibroPendiente marcador = repository
                .findByEmpresaIdAndPeriodoAndTipoOperacion(empresaId, periodo, operacion)
                .orElseGet(() -> LibroPendiente.builder()
                        .empresaId(empresaId).periodo(periodo).tipoOperacion(operacion).build());
        marcador.setEstado(estado);
        marcador.setDetalle(detalle);
        repository.save(marcador);
    }

    /** Borra el marcador de un libro (ya enviado o sin movimiento). */
    @Transactional
    public void borrar(Long empresaId, String periodo, TipoOperacion operacion) {
        repository.deleteByEmpresaIdAndPeriodoAndTipoOperacion(empresaId, periodo, operacion);
    }
}
