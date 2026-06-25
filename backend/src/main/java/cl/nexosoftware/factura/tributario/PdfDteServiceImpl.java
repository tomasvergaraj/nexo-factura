package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.LineaDetalle;
import cl.nexosoftware.factura.empresa.Empresa;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Representacion impresa del DTE con OpenPDF. Replica el formato chileno: recuadro
 * rojo con RUT, tipo y folio; datos de emisor y receptor; tabla de detalle;
 * totales; y un espacio reservado para el timbre (PDF417). El codigo de barras
 * PDF417 real se genera a partir del TED al integrar el CAF.
 */
@Service
public class PdfDteServiceImpl implements PdfDteService {

    private static final Color COBALTO = new Color(14, 123, 214);
    private static final Color ROJO = new Color(193, 39, 45);
    private static final Color GRIS = new Color(91, 107, 123);
    private static final DecimalFormat CLP =
            new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.forLanguageTag("es-CL")));

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
            pdf.add(timbre());

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
        fila(montos, "TOTAL", doc.getTotal(), true);
        PdfPCell cont = new PdfPCell(montos);
        cont.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(cont);
        return wrapper;
    }

    private Paragraph timbre() {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(18f);
        p.add(new Chunk("[ Timbre Electronico SII - PDF417 ]\n", font(9, Font.BOLD, GRIS)));
        p.add(new Chunk("Res. XX de XXXX - Verifique en www.sii.cl\n",
                font(8, Font.NORMAL, GRIS)));
        p.add(new Chunk("El codigo PDF417 se genera a partir del TED al integrar un CAF real.",
                font(7, Font.ITALIC, GRIS)));
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    // ---- helpers ----
    private void fila(PdfPTable t, String etiqueta, long valor, boolean destacado) {
        Font f = destacado ? font(11, Font.BOLD, COBALTO) : font(9, Font.NORMAL, Color.BLACK);
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, f));
        e.setHorizontalAlignment(Element.ALIGN_LEFT);
        e.setBorder(destacado ? Rectangle.TOP : Rectangle.NO_BORDER);
        e.setPadding(4f);
        PdfPCell v = new PdfPCell(new Phrase("$" + CLP.format(valor), f));
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
