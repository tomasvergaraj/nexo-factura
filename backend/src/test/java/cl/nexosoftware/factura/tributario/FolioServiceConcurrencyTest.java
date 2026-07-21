package cl.nexosoftware.factura.tributario;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.folio.Caf;
import cl.nexosoftware.factura.folio.CafRepository;
import cl.nexosoftware.factura.folio.FolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la asignacion de folios sea segura bajo concurrencia: 50 emisiones
 * simultaneas deben producir 50 folios distintos, sin duplicados ni perdidas. Si
 * el bloqueo pesimista del CAF fallara, apareceria al menos un folio repetido.
 */
class FolioServiceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private FolioService folioService;
    @Autowired private CafRepository cafRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private PlatformTransactionManager txManager;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("90000000-" + ThreadLocalRandom.current().nextInt(0, 9))
                .razonSocial("Empresa Concurrencia")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();

        cafRepository.save(Caf.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .folioDesde(1)
                .folioHasta(1000)
                .folioActual(0)
                .agotado(false)
                .creadoEn(OffsetDateTime.now())
                .build());
    }

    @Test
    @DisplayName("50 emisiones concurrentes generan 50 folios unicos")
    void asignaFoliosUnicosBajoConcurrencia() throws InterruptedException {
        int hilos = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch partida = new CountDownLatch(1);
        Set<Long> folios = ConcurrentHashMap.newKeySet();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                listos.countDown();
                try {
                    partida.await();
                    Long folio = tx.execute(status ->
                            folioService.siguienteFolio(empresaId, TipoDte.FACTURA_AFECTA).folio());
                    folios.add(folio);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        listos.await();
        partida.countDown(); // largada simultanea
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 50 folios, todos distintos: no hubo condicion de carrera.
        assertThat(folios).hasSize(hilos);
        assertThat(folios).allMatch(f -> f >= 1 && f <= 50);

        Caf caf = cafRepository.findByEmpresaIdOrderByTipoDteAscFolioDesdeAsc(empresaId).get(0);
        assertThat(caf.getFolioActual()).isEqualTo(50);
    }
}
