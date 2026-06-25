package cl.nexosoftware.factura.tributario;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica que el PDF417 del timbre se genera (zxing) y se puede embeber en un PDF (OpenPDF). */
class Pdf417GeneratorTest {

    private final Pdf417Generator generator = new Pdf417Generator();
    private static final String TED =
            "<TED version=\"1.0\"><DD><RE>76543210-9</RE><TD>33</TD><F>1</F>"
            + "<FE>2026-06-25</FE><RR>77111222-3</RR><MNT>59500</MNT></DD>"
            + "<FRMT algoritmo=\"SHA1withRSA\">FRMT-PENDIENTE-CAF</FRMT></TED>";

    @Test
    @DisplayName("zxing produce un PNG PDF417 valido y con dimensiones")
    void generaPngValido() throws Exception {
        byte[] png = generator.generarPng(TED);
        assertThat(png).isNotEmpty();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img).as("el PNG debe ser una imagen legible").isNotNull();
        assertThat(img.getWidth()).isGreaterThan(0);
        assertThat(img.getHeight()).isGreaterThan(0);
    }

    @Test
    @DisplayName("la imagen PDF417 se embebe en un PDF de OpenPDF")
    void seEmbebeEnPdf() throws Exception {
        byte[] png = generator.generarPng(TED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document();
        PdfWriter.getInstance(pdf, out);
        pdf.open();
        Image img = Image.getInstance(png);
        pdf.add(img);
        pdf.close();
        String contenido = out.toString("ISO-8859-1");
        assertThat(contenido).contains("/Image");
    }
}
