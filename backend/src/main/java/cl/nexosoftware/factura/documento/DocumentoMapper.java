package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.documento.DocumentoDtos.*;

import java.util.List;

/**
 * Mapeo de entidades a DTOs de respuesta. Se hace manualmente (en vez de
 * MapStruct) porque requiere derivar campos calculados como el codigo numerico
 * del tipo de DTE.
 */
public final class DocumentoMapper {

    private DocumentoMapper() {}

    public static DocumentoResponse toResponse(DocumentoTributario d) {
        List<LineaResponse> lineas = d.getLineas().stream()
                .map(DocumentoMapper::toLinea)
                .toList();
        return new DocumentoResponse(
                d.getId(), d.getTipoDte(), d.getTipoDte().getCodigo(), d.getFolio(), d.getEstado(),
                d.getFechaEmision(), d.getReceptorRut(), d.getReceptorRazonSocial(),
                d.getNeto(), d.getExento(), d.getTasaIva(), d.getIva(), d.getTotal(),
                d.getTrackId(), d.getObservacion(), lineas, d.getCreadoEn());
    }

    public static DocumentoResumen toResumen(DocumentoTributario d) {
        return new DocumentoResumen(
                d.getId(), d.getTipoDte(), d.getTipoDte().getCodigo(), d.getFolio(), d.getEstado(),
                d.getFechaEmision(), d.getReceptorRazonSocial(), d.getTotal());
    }

    private static LineaResponse toLinea(LineaDetalle l) {
        return new LineaResponse(
                l.getNumeroLinea(), l.getNombre(), l.getCantidad(), l.getUnidad(),
                l.getPrecioUnitario(), l.getDescuentoMonto(), l.isAfecto(), l.getMontoLinea());
    }
}
