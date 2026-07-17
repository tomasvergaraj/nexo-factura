package cl.nexosoftware.factura.documento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<DocumentoTributario, Long> {

    /**
     * Trae el documento con sus lineas en una sola consulta (evita N+1).
     * Solo se hace fetch de "lineas": incluir ademas "referencias" en el mismo
     * EntityGraph provoca MultipleBagFetchException (Hibernate no puede hacer
     * fetch de dos colecciones tipo List/bag a la vez). Las referencias se
     * cargan de forma perezosa dentro de la transaccion del servicio.
     */
    @EntityGraph(attributePaths = {"lineas"})
    Optional<DocumentoTributario> findWithDetalleById(Long id);

    /** Localiza un documento por su identidad tributaria (empresa + tipo + folio). */
    Optional<DocumentoTributario> findByEmpresaIdAndTipoDteAndFolio(Long empresaId, TipoDte tipoDte, Long folio);

    /**
     * Documentos ya foliados (emitidos) de una empresa en una fecha. Base del RCOF:
     * el servicio filtra por tipo de boleta y agrega los folios consumidos del dia.
     */
    List<DocumentoTributario> findByEmpresaIdAndFechaEmisionAndFolioNotNull(Long empresaId, LocalDate fechaEmision);

    /**
     * Ids de los documentos en un estado dado, del mas antiguo al mas nuevo. Base
     * del reenvio masivo de contingencia: solo ids (cada documento se recarga en
     * su propia transaccion, sin materializar N columnas xml_dte en memoria).
     */
    @Query("""
            select d.id from DocumentoTributario d
            where d.empresaId = :empresaId and d.estado = :estado
            order by d.creadoEn asc
            """)
    List<Long> findIdsByEmpresaIdAndEstado(@Param("empresaId") Long empresaId, @Param("estado") EstadoDte estado);

    /**
     * Vista de los documentos foliados (emitidos) de un periodo, base del libro
     * de ventas. Proyeccion cerrada: trae solo las columnas del libro y NO el
     * xml_dte (texto de varios KB por fila que la agregacion no necesita).
     * El servicio ordena por codigo SII (ordenar aqui por tipoDte ordenaria por
     * el NOMBRE del enum, no por el codigo) y aplica las reglas del IECV.
     */
    List<VentaLibroView> findLibroByEmpresaIdAndFolioNotNullAndFechaEmisionBetween(
            Long empresaId, LocalDate desde, LocalDate hasta);

    /** Proyeccion del documento emitido con lo que consume el libro de ventas. */
    interface VentaLibroView {
        TipoDte getTipoDte();
        Long getFolio();
        LocalDate getFechaEmision();
        EstadoDte getEstado();
        String getReceptorRut();
        String getReceptorRazonSocial();
        long getNeto();
        long getExento();
        long getIva();
        long getImpuestosAdicionales();
        long getIvaRetenido();
        long getTotal();
    }

    Page<DocumentoTributario> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId, Pageable pageable);

    Page<DocumentoTributario> findByEmpresaIdAndEstadoOrderByCreadoEnDesc(
            Long empresaId, EstadoDte estado, Pageable pageable);

    long countByEmpresaIdAndEstado(Long empresaId, EstadoDte estado);

    @Query("""
            select coalesce(sum(d.total), 0) from DocumentoTributario d
            where d.empresaId = :empresaId
              and d.estado in (cl.nexosoftware.factura.documento.EstadoDte.ENVIADO,
                               cl.nexosoftware.factura.documento.EstadoDte.ACEPTADO,
                               cl.nexosoftware.factura.documento.EstadoDte.EN_CONTINGENCIA)
              and d.fechaEmision >= :desde
            """)
    long sumTotalEmitidoDesde(@Param("empresaId") Long empresaId, @Param("desde") LocalDate desde);

    long countByEmpresaIdAndFechaEmisionGreaterThanEqual(Long empresaId, LocalDate desde);
}
