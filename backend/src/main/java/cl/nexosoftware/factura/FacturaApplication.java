package cl.nexosoftware.factura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de Nexo Factura.
 *
 * Plataforma de facturacion electronica para el SII de Chile. Permite emitir
 * Documentos Tributarios Electronicos (DTE): facturas afectas/exentas, boletas
 * y notas de credito/debito, gestionando folios (CAF), firma electronica,
 * envio al SII y representacion impresa en PDF.
 */
@SpringBootApplication
@EnableScheduling
public class FacturaApplication {
    public static void main(String[] args) {
        SpringApplication.run(FacturaApplication.class, args);
    }
}
