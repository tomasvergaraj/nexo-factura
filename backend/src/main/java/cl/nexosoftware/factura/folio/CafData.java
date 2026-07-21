package cl.nexosoftware.factura.folio;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;

/**
 * Contenido parseado y validado de un CAF (archivo AUTORIZACION del SII).
 *
 * {@code cafXmlVerbatim} es el fragmento {@code <CAF ...>...</CAF>} extraido
 * como substring literal del archivo original: el instructivo del SII exige
 * incorporarlo al timbre "sin modificacion alguna", y la verificacion de la
 * FRMA/FRMT del SII se hace sobre esa representacion (re-aplanada por ellos).
 */
public record CafData(
        String re,
        String rs,
        int td,
        long folioDesde,
        long folioHasta,
        LocalDate fechaAutorizacion,
        int idk,
        RSAPublicKey clavePublica,
        RSAPrivateKey clavePrivada,
        String cafXmlVerbatim
) {}
