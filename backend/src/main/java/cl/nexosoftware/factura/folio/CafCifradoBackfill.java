package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.seguridad.CifradorSecretos;
import cl.nexosoftware.factura.seguridad.SecretoTextoConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Migra al arrancar los CAF que quedaron en TEXTO PLANO antes de que el
 * {@link SecretoTextoConverter} cifrara la columna. Va en codigo y no en una
 * migracion Flyway porque cifrar necesita la clave maestra del entorno, que SQL
 * no tiene.
 *
 * <p>Se hace por JDBC a proposito: cargando las entidades, Hibernate compara el
 * XML ya descifrado contra si mismo, no ve cambio y no emitiria el UPDATE.
 *
 * <p>Sin APP_MASTER_KEY no aborta el arranque (el ambiente GLOBAL sin
 * certificados en BD sigue operando): avisa y deja las filas como estan, que es
 * exactamente el estado previo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class CafCifradoBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final CifradorSecretos cifrador;
    private final SecretoTextoConverter converter;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> pendientes = jdbc.queryForList(
                "select id, xml_caf from caf where xml_caf is not null and xml_caf not like ?",
                SecretoTextoConverter.PREFIJO + "%");
        if (pendientes.isEmpty()) {
            return;
        }
        if (!cifrador.disponible()) {
            log.warn("Hay {} CAF con su XML (y su clave privada RSA) en TEXTO PLANO en la BD y no hay "
                    + "APP_MASTER_KEY configurada para cifrarlos. Configurela para migrarlos al proximo arranque.",
                    pendientes.size());
            return;
        }
        for (Map<String, Object> fila : pendientes) {
            Long id = ((Number) fila.get("id")).longValue();
            String claro = (String) fila.get("xml_caf");
            jdbc.update("update caf set xml_caf = ? where id = ?",
                    converter.convertToDatabaseColumn(claro), id);
        }
        log.info("CAF migrados a cifrado en reposo: {}", pendientes.size());
    }
}
