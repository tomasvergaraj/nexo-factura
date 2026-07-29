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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la asignacion de folios sea segura bajo concurrencia: 50 emisiones
 * simultaneas deben producir 50 folios distintos, sin duplicados ni perdidas. Si
 * el bloqueo pesimista del CAF fallara, apareceria al menos un folio repetido.
 */
class FolioServiceConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private FolioService folioService;
    @Autowired private CafRepository cafRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private PlatformTransactionManager txManager;

    private Long empresaId;

    @BeforeEach
    void preparar() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut(rutUnicoDeTest())
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
                // Obligatorio: bloquearCafDisponible() descarta los CAF sin XML (sin el
                // se podria asignar folio pero no timbrar). Sembrado sin el, las 50
                // tareas fallaban con "No hay folios disponibles". El rango de la fila
                // manda para la asignacion; del XML aqui no se usa nada mas, porque este
                // test no llega a timbrar.
                .xmlCaf(DteFixtures.xmlCaf(33))
                .creadoEn(OffsetDateTime.now())
                .build());
    }

    @Test
    @DisplayName("50 emisiones concurrentes generan 50 folios unicos")
    void asignaFoliosUnicosBajoConcurrencia() throws InterruptedException {
        int hilos = 50;
        // Un hilo POR tarea, no menos: la largada simultanea es una barrera y cada
        // tarea hace countDown() y se queda bloqueada en partida.await(). Con un pool
        // mas chico que `hilos`, las tareas sobrantes nunca salen de la cola, el latch
        // `listos` no llega a cero y el await() de abajo cuelga PARA SIEMPRE (era un
        // pool de 16 para 50 tareas: el test no podia pasar, colgaba).
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch partida = new CountDownLatch(1);
        Set<Long> folios = ConcurrentHashMap.newKeySet();
        // Sin esto, un fallo de las tareas queda sepultado en sus Future (que nadie
        // consulta) y el test solo dice "esperaba 50, hubo 0", sin pista de por que.
        Queue<Throwable> fallos = new ConcurrentLinkedQueue<>();
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
                } catch (Throwable t) {
                    fallos.add(t);
                }
            });
        }

        // Con timeout: si la barrera no se completa, el test FALLA en vez de colgar.
        assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
        partida.countDown(); // largada simultanea
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(fallos)
                .as("ninguna emision debe fallar; primer fallo: %s",
                        fallos.isEmpty() ? "-" : fallos.peek())
                .isEmpty();

        // 50 folios, todos distintos: no hubo condicion de carrera.
        assertThat(folios).hasSize(hilos);
        assertThat(folios).allMatch(f -> f >= 1 && f <= 50);

        Caf caf = cafRepository.findByEmpresaIdOrderByTipoDteAscFolioDesdeAsc(empresaId).get(0);
        assertThat(caf.getFolioActual()).isEqualTo(50);
    }
}
