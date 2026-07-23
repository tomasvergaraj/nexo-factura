package cl.nexosoftware.factura.folio;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.seguridad.SecretoTextoConverter;
import cl.nexosoftware.factura.tributario.DteFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El XML del CAF nunca queda en claro en la BD: ni al guardarlo por JPA (el
 * converter cifra) ni en las filas que venian de antes (las migra el backfill).
 * Se mira la columna con SQL crudo, que es justo lo que veria un volcado.
 */
class CafCifradoIT extends AbstractIntegrationTest {

    @Autowired private CafRepository cafRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CafCifradoBackfill backfill;

    /** La empresa emisora es compartida entre ITs: no dejar CAF sueltos a otros. */
    @BeforeEach
    void limpiarAntes() {
        jdbc.update("delete from caf where empresa_id = ?", empresa().getId());
    }

    @AfterEach
    void limpiarDespues() {
        jdbc.update("delete from caf where empresa_id = ?", empresa().getId());
    }

    @Test
    @DisplayName("un CAF guardado por JPA queda cifrado en la columna y se lee en claro")
    void seGuardaCifradoYSeLeeEnClaro() {
        String xml = DteFixtures.xmlCaf(33);
        Long id = cafRepository.save(nuevoCaf(xml)).getId();

        String almacenado = columnaCruda(id);
        assertThat(almacenado).startsWith(SecretoTextoConverter.PREFIJO);
        assertThat(almacenado).doesNotContain("RSA PRIVATE KEY").doesNotContain("<AUTORIZACION");

        cafRepository.flush();
        assertThat(cafRepository.findById(id)).get()
                .extracting(Caf::getXmlCaf).isEqualTo(xml);
    }

    @Test
    @DisplayName("avanzar el folio no reescribe el XML cifrado")
    void avanzarFolioNoReescribeElXml() {
        Long id = cafRepository.save(nuevoCaf(DteFixtures.xmlCaf(33))).getId();
        String antes = columnaCruda(id);

        Caf caf = cafRepository.findById(id).orElseThrow();
        caf.setFolioActual(caf.getFolioActual() + 1);
        cafRepository.saveAndFlush(caf);

        assertThat(columnaCruda(id)).isEqualTo(antes);
    }

    @Test
    @DisplayName("una fila legacy en texto plano queda cifrada tras el backfill, sin perder el XML")
    void backfillMigraLasFilasLegacy() {
        String xml = DteFixtures.xmlCaf(39);
        Long id = insertarLegacyEnClaro(xml);
        assertThat(columnaCruda(id)).isEqualTo(xml); // punto de partida: en claro

        backfill.run(null);

        assertThat(columnaCruda(id)).startsWith(SecretoTextoConverter.PREFIJO);
        assertThat(cafRepository.findById(id)).get()
                .extracting(Caf::getXmlCaf).isEqualTo(xml);
    }

    private Caf nuevoCaf(String xml) {
        return Caf.builder()
                .empresaId(empresa().getId())
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .folioDesde(1).folioHasta(1000).folioActual(0)
                .agotado(false)
                .xmlCaf(xml)
                .creadoEn(OffsetDateTime.now())
                .build();
    }

    /** Inserta por SQL para simular una fila anterior al cifrado en reposo. */
    private Long insertarLegacyEnClaro(String xml) {
        return jdbc.queryForObject("""
                insert into caf (empresa_id, tipo_dte, folio_desde, folio_hasta, folio_actual,
                                 xml_caf, agotado, version, creado_en)
                values (?, 'BOLETA_AFECTA', 1, 100, 0, ?, false, 0, now())
                returning id
                """, Long.class, empresa().getId(), xml);
    }

    private String columnaCruda(Long id) {
        return jdbc.queryForObject("select xml_caf from caf where id = ?", String.class, id);
    }

    private Empresa empresa() {
        return empresaRepository.findAll().stream()
                .filter(e -> DteFixtures.RUT_EMISOR.equals(e.getRut()))
                .findFirst()
                .orElseGet(() -> empresaRepository.save(Empresa.builder()
                        .rut(DteFixtures.RUT_EMISOR)
                        .razonSocial("Empresa CAF cifrado")
                        .giro("Pruebas")
                        .actividadEconomica(620200)
                        .direccion("Calle 1")
                        .comuna("Quillota")
                        .build()));
    }
}
