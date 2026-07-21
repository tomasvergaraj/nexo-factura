package cl.nexosoftware.factura.tributario;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.PDF417Writer;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Genera el codigo de barras bidimensional PDF417 del timbre electronico (TED).
 * El contenido se codifica en ISO-8859-1, tal como exige el SII para el timbre.
 */
@Component
public class Pdf417Generator {

    /**
     * Codifica el contenido (el fragmento XML del TED) como una imagen PNG PDF417
     * en memoria. Las dimensiones se pasan en 0/0 para que zxing elija el tamano
     * minimo segun la cantidad de datos.
     */
    public byte[] generarPng(String contenido) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
            // Nivel de correccion de error 5: exigencia oficial del SII para el
            // timbre (Instructivo A.2.5), junto con codificacion binaria.
            hints.put(EncodeHintType.ERROR_CORRECTION, 5);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matriz = new PDF417Writer().encode(contenido, BarcodeFormat.PDF_417, 0, 0, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF417 del timbre", e);
        }
    }
}
