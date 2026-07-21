package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.empresa.Empresa;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Representacion impresa del DTE con OpenPDF. Replica el formato chileno: recuadro
 * rojo con RUT, tipo y folio; datos de emisor y receptor; tabla de detalle;
 * totales; y el timbre electronico (PDF417).
 *
 * El PDF417 codifica el TED tal como quedo EN EL XML FIRMADO almacenado (se
 * extrae como substring, jamas se regenera: regenerarlo produciria una segunda
 * firma FRMT con otro TSTED, distinta de la del documento emitido). Un
 * documento sin XML (borrador) muestra el texto de respaldo en vez del timbre.
 */
@Service
@RequiredArgsConstructor
public class PdfDteServiceImpl implements PdfDteService {

    private static final Color COBALTO = new Color(14, 123, 214);
    private static final Color ROJO = new Color(193, 39, 45);
    private static final Color GRIS = new Color(91, 107, 123);
    private static final DecimalFormat CLP =
            new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.forLanguageTag("es-CL")));

    private static final Logger log = LoggerFactory.getLogger(PdfDteServiceImpl.class);

    private final Pdf417Generator pdf417Generator;

    @Override
    public byte[] generar(DocumentoTributario doc, Empresa emisor) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.LETTER, 40, 40, 40, 40);
            PdfWriter.getInstance(pdf, out);
            pdf.open();

            pdf.add(encabezado(doc, emisor));
            pdf.add(new Paragraph(" "));
            pdf.add(receptor(doc));
            pdf.add(new Paragraph(" "));
            pdf.add(detalle(doc));
            pdf.add(totales(doc));

            agregarTimbre(pdf, extraerTed(doc.getXmlDte()));

            pdf.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del DTE", e);
        }
    }

    private PdfPTable encabezado(DocumentoTributario doc, Empresa emisor) {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        try {
            tabla.setWidths(new int[]{62, 38});
        } catch (DocumentException ignored) {
        }

        Font fEmpresa = font(15, Font.BOLD, COBALTO);
        Font fDato = font(9, Font.NORMAL, GRIS);
        Phrase emisorPhrase = new Phrase();
        emisorPhrase.add(new Chunk(emisor.getRazonSocial() + "\n", fEmpresa));
        emisorPhrase.add(new Chunk("Giro: " + emisor.getGiro() + "\n", fDato));
        emisorPhrase.add(new Chunk(emisor.getDireccion() + ", " + emisor.getComuna(), fDato));
        tabla.addCell(sinBorde(emisorPhrase));

        PdfPCell recuadro = new PdfPCell();
        recuadro.setBorderColor(ROJO);
        recuadro.setBorderWidth(2f);
        recuadro.setPadding(10f);
        Paragraph rut = new Paragraph("R.U.T.: " + emisor.getRut(), font(11, Font.BOLD, ROJO));
        rut.setAlignment(Element.ALIGN_CENTER);
        Paragraph tipo = new Paragraph(doc.getTipoDte().getDescripcion().toUpperCase(), font(10, Font.BOLD, ROJO));
        tipo.setAlignment(Element.ALIGN_CENTER);
        Paragraph folio = new Paragraph("N° " + (doc.getFolio() != null ? doc.getFolio() : "-----"),
                font(13, Font.BOLD, ROJO));
        folio.setAlignment(Element.ALIGN_CENTER);
        recuadro.addElement(rut);
        recuadro.addElement(tipo);
        recuadro.addElement(folio);
        tabla.addCell(recuadro);
        return tabla;
    }

    private PdfPTable receptor(DocumentoTributario doc) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        Font etiqueta = font(9, Font.BOLD, GRIS);
        Font valor = font(10, Font.NORMAL, Color.BLACK);
        Phrase p = new Phrase();
        p.add(new Chunk("SEÑOR(ES): ", etiqueta));
        p.add(new Chunk(doc.getReceptorRazonSocial() + "   ", valor));
        p.add(new Chunk("R.U.T.: ", etiqueta));
        p.add(new Chunk(doc.getReceptorRut() + "\n", valor));
        if (doc.getReceptorDireccion() != null) {
            p.add(new Chunk("DIRECCION: ", etiqueta));
            p.add(new Chunk(doc.getReceptorDireccion() + ", " + nvl(doc.getReceptorComuna()) + "\n", valor));
        }
        p.add(new Chunk("FECHA EMISION: ", etiqueta));
        p.add(new Chunk(doc.getFechaEmision().toString(), valor));
        PdfPCell celda = new PdfPCell(p);
        celda.setPadding(8f);
        celda.setBackgroundColor(new Color(244, 247, 250));
        celda.setBorderColor(new Color(227, 233, 240));
        tabla.addCell(celda);
        return tabla;
    }

    private PdfPTable detalle(DocumentoTributario doc) {
        PdfPTable tabla = new PdfPTable(new float[]{10, 46, 14, 14, 16});
        tabla.setWidthPercentage(100);
        for (String h : new String[]{"Cant.", "Detalle", "P. Unit.", "Desc.", "Importe"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, font(9, Font.BOLD, Color.WHITE)));
            c.setBackgroundColor(COBALTO);
            c.setPadding(6f);
            tabla.addCell(c);
        }
        Font cuerpo = font(9, Font.NORMAL, Color.BLACK);
        for (LineaDetalle l : doc.getLineas()) {
            tabla.addCell(celdaCuerpo(fmt(l.getCantidad()), cuerpo, Element.ALIGN_CENTER));
            tabla.addCell(celdaCuerpo(l.getNombre(), cuerpo, Element.ALIGN_LEFT));
            tabla.addCell(celdaCuerpo("$" + CLP.format(l.getPrecioUnitario()), cuerpo, Element.ALIGN_RIGHT));
            tabla.addCell(celdaCuerpo(l.getDescuentoMonto() > 0 ? "$" + CLP.format(l.getDescuentoMonto()) : "-",
                    cuerpo, Element.ALIGN_RIGHT));
            tabla.addCell(celdaCuerpo("$" + CLP.format(l.getMontoLinea()), cuerpo, Element.ALIGN_RIGHT));
        }
        return tabla;
    }

    private PdfPTable totales(DocumentoTributario doc) {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        try {
            wrapper.setWidths(new int[]{60, 40});
        } catch (DocumentException ignored) {
        }
        wrapper.setSpacingBefore(8f);
        wrapper.addCell(sinBorde(new Phrase("")));

        PdfPTable montos = new PdfPTable(2);
        if (doc.getExento() > 0) fila(montos, "Exento", doc.getExento(), false);
        fila(montos, "Neto", doc.getNeto(), false);
        fila(montos, "IVA " + (int) doc.getTasaIva() + "%", doc.getIva(), false);
        // Otros impuestos (P1-6): un renglon por codigo. Los adicionales suman (+),
        // la retencion de IVA resta (-), para que el TOTAL cuadre con lo visible.
        for (var imp : CalculadoraImpuestos.desglosarImpuestos(doc.getLineas())) {
            if (imp.esRetencion()) {
                filaTexto(montos, "IVA retenido", "-$" + CLP.format(imp.monto()), false);
            } else {
                fila(montos, "Imp. adicional " + imp.codigo() + " (" + fmt(imp.tasa()) + "%)", imp.monto(), false);
            }
        }
        fila(montos, "TOTAL", doc.getTotal(), true);
        PdfPCell cont = new PdfPCell(montos);
        cont.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(cont);
        return wrapper;
    }

    /**
     * Agrega el timbre electronico al PDF: el codigo de barras PDF417 que codifica
     * el TED real, centrado, con la leyenda del SII debajo. La imagen se agrega como
     * elemento de bloque (no como Chunk inline) y escalada para caber en el ancho de
     * la pagina; de lo contrario OpenPDF descarta una imagen mas ancha que la linea.
     * Si la generacion del PDF417 falla (zxing o el render), cae al texto de respaldo
     * para no romper la emision.
     */
    private void agregarTimbre(Document pdf, String tedXml) throws DocumentException {
        pdf.add(new Paragraph(" "));
        if (tedXml == null) {
            // Borrador sin emitir: no hay XML firmado ni timbre que representar.
            Paragraph p = new Paragraph();
            p.setAlignment(Element.ALIGN_CENTER);
            p.add(new Chunk("DOCUMENTO EN BORRADOR - SIN TIMBRE\n", font(9, Font.BOLD, GRIS)));
            p.add(new Chunk("El timbre electronico se genera al emitir.", font(7, Font.ITALIC, GRIS)));
            pdf.add(p);
            return;
        }
        try {
            byte[] png = pdf417Generator.generarPng(tedXml);
            Image img = Image.getInstance(png);
            img.setAlignment(Image.ALIGN_CENTER);
            img.scaleToFit(360f, 120f); // cabe en el ancho util de la pagina (LETTER, margenes 40)
            pdf.add(img);

            Paragraph leyenda = new Paragraph();
            leyenda.setAlignment(Element.ALIGN_CENTER);
            leyenda.add(new Chunk("Timbre Electronico SII\n", font(8, Font.BOLD, GRIS)));
            leyenda.add(new Chunk("Verifique en www.sii.cl", font(7, Font.NORMAL, GRIS)));
            pdf.add(leyenda);
        } catch (Exception e) {
            // Fallback: si zxing falla, mantener el texto para no romper la emision.
            log.warn("No se pudo generar el PDF417 del timbre, usando texto de respaldo", e);
            Paragraph p = new Paragraph();
            p.setAlignment(Element.ALIGN_CENTER);
            p.add(new Chunk("[ Timbre Electronico SII - PDF417 ]\n", font(9, Font.BOLD, GRIS)));
            p.add(new Chunk("Res. XX de XXXX - Verifique en www.sii.cl\n",
                    font(8, Font.NORMAL, GRIS)));
            p.add(new Chunk("No se pudo generar el codigo PDF417 del timbre.",
                    font(7, Font.ITALIC, GRIS)));
            pdf.add(p);
        }
    }

    /**
     * Extrae el fragmento {@code <TED ...>...</TED>} del XML firmado almacenado,
     * byte-identico a como fue firmado. Null si el documento no tiene XML.
     */
    private String extraerTed(String xmlDte) {
        if (xmlDte == null) return null;
        int inicio = xmlDte.indexOf("<TED");
        int fin = xmlDte.indexOf("</TED>");
        if (inicio < 0 || fin < 0) return null;
        return xmlDte.substring(inicio, fin + "</TED>".length());
    }

    // ---- helpers ----
    private void fila(PdfPTable t, String etiqueta, long valor, boolean destacado) {
        filaTexto(t, etiqueta, "$" + CLP.format(valor), destacado);
    }

    private void filaTexto(PdfPTable t, String etiqueta, String valorTexto, boolean destacado) {
        Font f = destacado ? font(11, Font.BOLD, COBALTO) : font(9, Font.NORMAL, Color.BLACK);
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, f));
        e.setHorizontalAlignment(Element.ALIGN_LEFT);
        e.setBorder(destacado ? Rectangle.TOP : Rectangle.NO_BORDER);
        e.setPadding(4f);
        PdfPCell v = new PdfPCell(new Phrase(valorTexto, f));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setBorder(destacado ? Rectangle.TOP : Rectangle.NO_BORDER);
        v.setPadding(4f);
        t.addCell(e);
        t.addCell(v);
    }

    private PdfPCell celdaCuerpo(String texto, Font font, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, font));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(5f);
        c.setBorderColor(new Color(227, 233, 240));
        return c;
    }

    private PdfPCell sinBorde(Phrase phrase) {
        PdfPCell c = new PdfPCell(phrase);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private Font font(float size, int style, Color color) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        f.setColor(color);
        return f;
    }

    private String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
