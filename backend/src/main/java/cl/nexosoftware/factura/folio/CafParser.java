package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Parsea y valida el archivo AUTORIZACION (CAF) que entrega el SII.
 *
 * Extrae los datos del bloque DA (emisor, tipo, rango de folios, fecha, claves),
 * la clave privada RSASK (PEM PKCS#1, decodificada con un lector DER minimo sin
 * dependencias) y el fragmento {@code <CAF>...</CAF>} verbatim para el timbre.
 *
 * Validaciones: estructura completa, rango coherente, correspondencia entre la
 * clave privada (RSASK) y la publica del DA (RSAPK) — incluida una firma de
 * prueba SHA1withRSA verificada — de modo que un CAF corrupto se rechace al
 * cargarlo y no al emitir. NOTA: la firma FRMA (la del SII sobre el DA) no se
 * verifica — el SII no publica formalmente su clave publica por IDK — asi que
 * un DA editado a mano que conserve el par de claves pasaria; es el follow-up
 * documentado en SPRINT-6-PLAN §7.
 */
@Component
public class CafParser {

    private static final SecureRandom RANDOM = new SecureRandom();

    public CafData parsear(String xmlCaf) {
        if (xmlCaf == null || xmlCaf.isBlank()) {
            throw new ReglaNegocioException("El XML del CAF es obligatorio");
        }
        Element autorizacion = parsearDom(xmlCaf);
        Element da = hijoObligatorio(autorizacion, "DA");

        String re = texto(da, "RE");
        String rs = texto(da, "RS");
        int td = enteroPositivo(texto(da, "TD"), "TD");
        Element rng = hijoObligatorio(da, "RNG");
        long desde = largoPositivo(texto(rng, "D"), "RNG/D");
        long hasta = largoPositivo(texto(rng, "H"), "RNG/H");
        LocalDate fa = fecha(texto(da, "FA"));
        int idk = enteroPositivo(texto(da, "IDK"), "IDK");

        if (hasta < desde) {
            throw new ReglaNegocioException(
                    "El rango de folios del CAF es incoherente (D=" + desde + ", H=" + hasta + ")");
        }

        Element rsapk = hijoObligatorio(da, "RSAPK");
        RSAPublicKey clavePublica = clavePublica(texto(rsapk, "M"), texto(rsapk, "E"));
        RSAPrivateKey clavePrivada = clavePrivadaPkcs1(xmlCaf);

        if (!clavePrivada.getModulus().equals(clavePublica.getModulus())) {
            throw new ReglaNegocioException(
                    "La clave privada del CAF (RSASK) no corresponde a su clave publica (RSAPK)");
        }
        verificarParDeClaves(clavePrivada, clavePublica);

        return new CafData(re, rs, td, desde, hasta, fa, idk, clavePublica, clavePrivada,
                extraerCafVerbatim(xmlCaf));
    }

    // ---------- DOM ----------

    private Element parsearDom(String xmlCaf) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Endurecimiento XXE: el CAF lo sube un usuario.
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setExpandEntityReferences(false);
            dbf.setXIncludeAware(false);
            // StringReader: los CAF del SII declaran <?xml version="1.0"?> sin
            // encoding; por bytes el parser asumiria UTF-8 (rompe con enes).
            Document doc = dbf.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlCaf)));
            Element raiz = doc.getDocumentElement();
            if (!"AUTORIZACION".equals(raiz.getNodeName())) {
                throw new ReglaNegocioException(
                        "El XML no es un CAF del SII (raiz esperada AUTORIZACION, vino " + raiz.getNodeName() + ")");
            }
            return raiz;
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new ReglaNegocioException("El XML del CAF no se pudo parsear: " + e.getMessage());
        }
    }

    private Element hijoObligatorio(Element padre, String nombre) {
        NodeList lista = padre.getElementsByTagName(nombre);
        if (lista.getLength() == 0) {
            throw new ReglaNegocioException("El CAF no contiene el elemento obligatorio " + nombre);
        }
        return (Element) lista.item(0);
    }

    private String texto(Element padre, String nombre) {
        String valor = hijoObligatorio(padre, nombre).getTextContent();
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El elemento " + nombre + " del CAF esta vacio");
        }
        return valor.trim();
    }

    private int enteroPositivo(String valor, String campo) {
        try {
            int n = Integer.parseInt(valor);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("El campo " + campo + " del CAF no es un entero valido: " + valor);
        }
    }

    private long largoPositivo(String valor, String campo) {
        try {
            long n = Long.parseLong(valor);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("El campo " + campo + " del CAF no es un folio valido: " + valor);
        }
    }

    private LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException("La fecha de autorizacion (FA) del CAF no es valida: " + valor);
        }
    }

    // ---------- claves ----------

    private RSAPublicKey clavePublica(String m, String e) {
        try {
            BigInteger modulo = new BigInteger(1, Base64.getMimeDecoder().decode(m));
            BigInteger exponente = new BigInteger(1, Base64.getMimeDecoder().decode(e));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulo, exponente));
        } catch (Exception ex) {
            throw new ReglaNegocioException("La clave publica del CAF (RSAPK) no es valida: " + ex.getMessage());
        }
    }

    private RSAPrivateKey clavePrivadaPkcs1(String xmlCaf) {
        String pem = substringEntre(xmlCaf, "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----",
                "la clave privada (RSASK)");
        try {
            BigInteger[] enteros = decodificarRsaPrivateKey(Base64.getMimeDecoder().decode(pem.trim()));
            RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(
                    enteros[1], enteros[2], enteros[3], enteros[4],
                    enteros[5], enteros[6], enteros[7], enteros[8]);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new ReglaNegocioException("La clave privada del CAF (RSASK) no se pudo decodificar: " + e.getMessage());
        }
    }

    /**
     * Decodifica el DER PKCS#1 de RSASK: {@code RSAPrivateKey ::= SEQUENCE} de 9
     * INTEGER (version, n, e, d, p, q, dP, dQ, qInv) — RFC 8017 A.1.2.
     */
    private BigInteger[] decodificarRsaPrivateKey(byte[] der) {
        int[] pos = {0};
        exigirTag(der, pos, 0x30); // SEQUENCE
        leerLargo(der, pos);
        BigInteger[] enteros = new BigInteger[9];
        for (int i = 0; i < 9; i++) {
            exigirTag(der, pos, 0x02); // INTEGER
            int largo = leerLargo(der, pos);
            enteros[i] = new BigInteger(Arrays.copyOfRange(der, pos[0], pos[0] + largo));
            pos[0] += largo;
        }
        return enteros;
    }

    private void exigirTag(byte[] der, int[] pos, int tag) {
        if (pos[0] >= der.length || (der[pos[0]++] & 0xFF) != tag) {
            throw new ReglaNegocioException("La clave privada del CAF no tiene el formato PKCS#1 esperado");
        }
    }

    private int leerLargo(byte[] der, int[] pos) {
        int b = der[pos[0]++] & 0xFF;
        int largo;
        if (b < 0x80) {
            largo = b;
        } else {
            int n = b & 0x7F;
            // Un RSASK real cabe de sobra en largos de 2 bytes; 4+ bytes o un
            // largo mayor que el buffer es un DER malformado/malicioso — sin esta
            // cota, copyOfRange intentaria asignar hasta 2 GB (OutOfMemoryError)
            // o rellenaria con ceros produciendo una clave corrupta silenciosa.
            if (n > 3) {
                throw new ReglaNegocioException("La clave privada del CAF no tiene el formato PKCS#1 esperado");
            }
            largo = 0;
            for (int i = 0; i < n; i++) {
                if (pos[0] >= der.length) {
                    throw new ReglaNegocioException("La clave privada del CAF esta truncada");
                }
                largo = (largo << 8) | (der[pos[0]++] & 0xFF);
            }
        }
        if (largo < 0 || largo > der.length - pos[0]) {
            throw new ReglaNegocioException("La clave privada del CAF esta truncada o malformada");
        }
        return largo;
    }

    /** Firma un vector aleatorio con la privada y lo verifica con la publica del DA. */
    private void verificarParDeClaves(RSAPrivateKey privada, RSAPublicKey publica) {
        try {
            byte[] vector = new byte[32];
            RANDOM.nextBytes(vector);
            Signature firmador = Signature.getInstance("SHA1withRSA");
            firmador.initSign(privada);
            firmador.update(vector);
            byte[] firma = firmador.sign();

            Signature verificador = Signature.getInstance("SHA1withRSA");
            verificador.initVerify(publica);
            verificador.update(vector);
            if (!verificador.verify(firma)) {
                throw new ReglaNegocioException(
                        "Las claves del CAF no se corresponden: la firma de prueba no verifica");
            }
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new ReglaNegocioException("No se pudo verificar el par de claves del CAF: " + e.getMessage());
        }
    }

    // ---------- fragmento verbatim ----------

    /**
     * Extrae {@code <CAF ...>...</CAF>} como substring literal del archivo, sin
     * re-serializar: es lo que exige el instructivo del SII para el timbre.
     */
    private String extraerCafVerbatim(String xmlCaf) {
        int inicio = xmlCaf.indexOf("<CAF");
        int cierre = xmlCaf.indexOf("</CAF>");
        // "<CAF" debe ser el elemento (seguido de espacio o '>'), no un prefijo de otro tag.
        while (inicio >= 0) {
            char siguiente = inicio + 4 < xmlCaf.length() ? xmlCaf.charAt(inicio + 4) : ' ';
            if (siguiente == ' ' || siguiente == '>' || siguiente == '\t' || siguiente == '\n' || siguiente == '\r') {
                break;
            }
            inicio = xmlCaf.indexOf("<CAF", inicio + 1);
        }
        if (inicio < 0 || cierre < 0 || cierre < inicio) {
            throw new ReglaNegocioException("El CAF no contiene el bloque <CAF>...</CAF>");
        }
        return xmlCaf.substring(inicio, cierre + "</CAF>".length());
    }

    private String substringEntre(String s, String desde, String hasta, String descripcion) {
        int i = s.indexOf(desde);
        int j = s.indexOf(hasta);
        if (i < 0 || j < 0 || j < i) {
            throw new ReglaNegocioException("El CAF no contiene " + descripcion);
        }
        return s.substring(i + desde.length(), j);
    }
}
