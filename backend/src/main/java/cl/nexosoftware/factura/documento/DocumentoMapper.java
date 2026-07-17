package cl.nexosoftware.factura.documento;

import cl.nexosoftware.factura.documento.DocumentoDtos.*;
import cl.nexosoftware.factura.tributario.CalculadoraImpuestos;

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
        List<ReferenciaResponse> referencias = d.getReferencias().stream()
                .map(DocumentoMapper::toReferencia)
                .toList();
        // Desglose de otros impuestos: derivado del MISMO calculo determinista que el
        // XML y los montos agregados (impuestosAdicionales/ivaRetenido) del documento.
        List<ImpuestoResponse> impuestos = CalculadoraImpuestos.desglosarImpuestos(d.getLineas()).stream()
                .map(i -> new ImpuestoResponse(i.codigo(), i.nombre(), i.tasa(), i.esRetencion(), i.base(), i.monto()))
                .toList();
        return new DocumentoResponse(
                d.getId(), d.getTipoDte(), d.getTipoDte().getCodigo(), d.getFolio(), d.getEstado(),
                d.getFechaEmision(), d.getReceptorRut(), d.getReceptorRazonSocial(),
                d.getNeto(), d.getExento(), d.getTasaIva(), d.getIva(),
                d.getImpuestosAdicionales(), d.getIvaRetenido(), d.getTotal(),
                d.getTrackId(), d.getObservacion(), lineas, d.getCreadoEn(), referencias, impuestos, d.getSello(),
                d.getIntentosEnvio(), d.getUltimoEnvioEn(), d.getUltimoErrorEnvio());
    }

    public static DocumentoResumen toResumen(DocumentoTributario d) {
        return new DocumentoResumen(
                d.getId(), d.getTipoDte(), d.getTipoDte().getCodigo(), d.getFolio(), d.getEstado(),
                d.getFechaEmision(), d.getReceptorRazonSocial(), d.getTotal());
    }

    private static LineaResponse toLinea(LineaDetalle l) {
        return new LineaResponse(
                l.getNumeroLinea(), l.getNombre(), l.getCantidad(), l.getUnidad(),
                l.getPrecioUnitario(), l.getDescuentoMonto(), l.isAfecto(), l.getCodImpAdic(), l.getMontoLinea());
    }

    private static ReferenciaResponse toReferencia(Referencia r) {
        return new ReferenciaResponse(
                r.getTipoDocumentoRef(), r.getFolioRef(), r.getFechaRef(),
                r.getTipoReferencia(), r.getTipoReferencia().getCodigo(), r.getRazon());
    }
}
