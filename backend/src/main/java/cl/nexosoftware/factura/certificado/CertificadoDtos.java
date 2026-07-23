package cl.nexosoftware.factura.certificado;

import java.time.OffsetDateTime;

/**
 * DTO de metadata del certificado. NUNCA expone el PKCS#12 ni la clave: solo lo
 * necesario para que la UI muestre el estado y avise del vencimiento.
 */
public final class CertificadoDtos {

    private CertificadoDtos() {}

    public record CertificadoResponse(
            Long id,
            String nombreArchivo,
            String rutFirmante,
            String subject,
            OffsetDateTime validoDesde,
            OffsetDateTime validoHasta,
            String huellaSha256,
            boolean vigente,
            long diasParaVencer,
            OffsetDateTime creadoEn,
            String creadoPor
    ) {}
}
