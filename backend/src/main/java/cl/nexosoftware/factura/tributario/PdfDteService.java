package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.empresa.Empresa;

/** Genera la representacion impresa (PDF) de un DTE. */
public interface PdfDteService {
    byte[] generar(DocumentoTributario doc, Empresa emisor);
}
