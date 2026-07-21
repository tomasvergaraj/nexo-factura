/**
 * Documentos tributarios del SII. Todos los modelos JAXB del paquete marshallan
 * en el namespace oficial {@code http://www.sii.cl/SiiDte} (elementos
 * calificados, como exigen los XSD del SII); el hint de prefijo vacio hace que
 * el namespace salga como declaracion default ({@code xmlns="..."}) en la raiz.
 */
@XmlSchema(
        namespace = "http://www.sii.cl/SiiDte",
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = @XmlNs(prefix = "", namespaceURI = "http://www.sii.cl/SiiDte"))
package cl.nexosoftware.factura.tributario;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
