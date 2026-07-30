# Plan de continuidad — CI, `fctProp` y documentación

> **Propósito de este archivo.** Es el estado vivo del trabajo en curso, pensado para
> **retomar en una sesión distinta sin contexto previo**. Cada fase lleva su estado, lo
> que ya se hizo y el siguiente paso concreto. Al terminar todas las fases este documento
> se archiva (su contenido definitivo vive en `PROGRESS.md`).
>
> Última actualización: **2026-07-29**.

## Empieza aquí — mensaje para la próxima sesión

Sesión del **2026-07-29**. **Las cuatro fases de este plan están cerradas**: de la 4 se agotaron
todos los follow-ups accionables salvo dos campos opcionales del DTE. Todo empujado a `main` y
validado en CI; el árbol está limpio y sincronizado con `origin/main`.

### Estado en una línea
**370 tests unitarios + 90 de integración, 0 fallos y 0 errores**, ejecutados por CI en cada push
—verde desde su primera corrida—. Los libros de compras con IVA de uso común ya se pueden enviar,
la resolución de los envíos llega sola, y el RCOF dejó de sobredeclarar boletas rechazadas.

### Qué hacer, en este orden

**1. ~~El RCOF no se envía al SII~~ — CERRADO el 2026-07-29, y no como decía este punto.**
*Hallazgo de esta sesión; no estaba en ninguna fase.*
[`RcofController`](../backend/src/main/java/cl/nexosoftware/factura/rcof/RcofController.java)
sólo expone dos GET: el reporte en JSON y el XML **sin firmar**. `SiiGateway` tiene `enviar`,
`enviarLibro` y `enviarLote` — **no hay método para el RCOF**.

No es un descuido oculto: el `ROADMAP` siempre dijo «sin firmar/enviar». Lo que cambió es el
motivo. El javadoc justificaba el diferimiento en que *«requiere certificado real, igual que la
firma del DTE»*, y **ese bloqueo desapareció en el Sprint 7**: hay certificado y resolución por
empresa. Ya se corrigió ese comentario.

> **Corrección del mismo día, en dos golpes — este punto ya no encabeza nada.**
>
> (1) Se escribió afirmando que «el sistema emite boletas en producción» y que por eso había una
> **obligación tributaria incumplida**. **Era falso.** Producción tiene cuatro CAF (33, 34, 61,
> 56) y **ninguno de boleta**, y su BD contiene sólo la factura 33 de humo —anulada— y su nota de
> crédito. La boleta 39 aceptada fue en **certificación**. Lo detectó el usuario.
>
> (2) La obligación **tampoco existe**: la **Res. Ex. SII N°53 de 2022** eliminó el envío del
> RCOF —hoy *Resumen de Ventas Diarias*— **desde el 01/08/2022**; el registro de ventas se
> alimenta del XML de las boletas. Lo único que le queda al RCOF es ser un **archivo firmado que
> se adjunta al correo de certificación de boletas** (`SII_BE_Certificacion@sii.cl`). No hay que
> construir ningún canal de envío. Fuentes y detalle en [ROADMAP.md §15](ROADMAP.md).

Lo que falta para cerrarlo:
- Firmar el `ConsumoFolios` y postearlo. [`LibroEnvioService`](../backend/src/main/java/cl/nexosoftware/factura/libro/LibroEnvioService.java)
  es la plantilla exacta: construir → firmar enveloped → validar contra el XSD → `SiiGateway` →
  persistir el TrackID. Se puede copiar la forma casi tal cual.
- **`SEC_ENVIO_PLACEHOLDER` está fijo en 1** y el SII espera que la secuencia incremente. Hace
  falta persistirla por empresa, igual que se persiste el TrackID del libro.
- Reutilizar lo ya construido en esta sesión: el job de consulta de estados y el patrón de
  «una transacción por ítem, un fallo no aborta el lote».

**Resuelto el mismo día.** Disparo **manual** desde la pantalla RCOF, **sin job automático**;
nada que regularizar hacia atrás (no hay boletas emitidas); la periodicidad la respondió la Res.
53/2022 (no hay, no se envía). Se implementó firma + descarga con el XSD oficial vendoreado,
carátula completa y secuencia por día (`V18`); **no** se construyó canal de envío. Suite en verde:
361 unitarios + 80 ITs. Ver [ROADMAP.md §15](ROADMAP.md).

**2. Archivar este plan.** Ya no tiene trabajo pendiente. Su contenido definitivo va a
`PROGRESS.md` (Sprint 8 ya está escrito); este archivo se borra o se mueve a un histórico.

**3. `MedioPago` / `GeoRefEmision`** — campos opcionales del DTE que nadie ha pedido. La
verificación de la FRMA del CAF **no es accionable**: el SII no publica la clave pública por IDK.

> **No confíes en la lista de la Fase 4 sin mirar el código.** De los cuatro follow-ups que
> quedaban, **uno ya estaba implementado** (el signo de las NC, con tests) y otro subestimaba el
> problema. Verificalo antes de ponerte a trabajar sobre algo que quizá ya existe.

### Lo que conviene saber antes de tocar nada

- **El entorno no tiene Java ni Maven.** Todo pasa por el contenedor `maven`. Los comandos
  exactos están abajo, y el de `verify` **no funciona sin** el montaje del socket y el
  `TESTCONTAINERS_HOST_OVERRIDE`. Para iterar sobre un solo IT hace falta apagar Surefire con
  **su** propiedad, `-Dsurefire.failIfNoSpecifiedTests=false`; la genérica no sirve.
- **El usuario no tiene contador: declara él mismo.** Dos consecuencias. (1) Ningún texto de la
  UI debe derivar una decisión tributaria a un asesor —había dos así y hubo que reescribirlos—;
  la referencia correcta es el **F29**. (2) No hay una segunda mirada profesional después del
  sistema, así que ante semántica tributaria con más de una lectura defendible, **preguntá al
  usuario** en vez de elegir por él. Así se resolvió lo del `RECHAZADO` en el RCOF.
- **La documentación de este repo ha afirmado cosas falsas, repetidamente.** Seis casos
  confirmados: los ITs «corrían en CI» (no existía CI); el fixture `cert_prueba.p12` estaba
  «commiteado» (lo excluía el `.gitignore`); el fallo de Testcontainers se atribuía a Docker
  anidado (era la versión de API); el plan daba por hecho que `Libros.tsx` permitía sobreescribir
  `fctProp` por período (esa pantalla no lo mencionaba en absoluto); especificaba
  `NUMERIC(3,2)` para una columna que Hibernate exige como `float(53)` —eso tumbaba el contexto
  entero, y lo cazó un IT, no la compilación—; y **«el sistema emite boletas en producción»**,
  que era la premisa entera del hallazgo del RCOF y **nunca ocurrió** (producción no tiene CAF de
  boleta; lo detectó el usuario, no el repo). **Verificá antes de apoyarte en cualquier
  afirmación**, sobre todo si es la premisa de lo que vas a hacer. Cuando encuentres una falsa,
  dejala escrita con nombre y apellido en vez de corregirla en silencio.

### Lo confirmado de primera mano en esta sesión

- CI existe, corre `mvn -B verify` completo y **está verde**; el badge del README refleja el
  último run, así que esa afirmación se mantiene sola en vez de depender de que alguien la
  actualice. El runner trae Docker 28.0.4 (API 1.48, mín. 1.24): el pin a `1.44` entra holgado.
- La suite entera pasa local **y** en el runner, con los mismos números.
- La emisión de humo en producción sobre palena, que el usuario dio por verificada. Fue una
  **factura 33** (hoy anulada, con su NC): en producción no hay CAF de boleta ni boletas emitidas.

---

## Contexto de partida

Los sprints 1–6, la reconciliación por folio y el corte a producción sobre **palena**
están completos, y la **emisión de humo en producción está verificada y funcionando**
(confirmado por el usuario el 2026-07-29; el paso 6 de [RUNBOOK-produccion.md](../RUNBOOK-produccion.md)
está cumplido). Lo que queda son las brechas de infraestructura y documentación que
detecta este plan.

### Entorno de trabajo (importante para retomar)

- **No hay Java ni Maven instalados en el host** (Windows). Todo lo de backend corre
  dentro del contenedor `maven:3.9-eclipse-temurin-21`, montando el volumen `nexo_m2`
  para la caché de dependencias — igual que en todos los sprints anteriores.
- Docker Desktop **sí** está disponible (server 29.2.1). Node 22 en el host.
- Comando para los **tests unitarios** (no necesita Docker dentro del contenedor):
  ```bash
  docker run --rm -v "$PWD/backend:/app" -v nexo_m2:/root/.m2 -w /app \
    maven:3.9-eclipse-temurin-21 mvn -B test
  ```
- Comando para la **suite completa** (unitarios + los `*IT` con Testcontainers). El montaje
  del socket y el `HOST_OVERRIDE` son obligatorios; el porqué está en la Fase 1.2:
  ```bash
  docker run --rm -v "$PWD/backend:/app" -v nexo_m2:/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    -w /app maven:3.9-eclipse-temurin-21 mvn -B verify
  ```
  **No** poner `TESTCONTAINERS_RYUK_DISABLED=true`: con el contenedor singleton, Ryuk es
  justamente quien recoge el PostgreSQL al terminar la JVM.
  En PowerShell hay que **entrecomillar cada `-D`** (`"-Dit.test=CafCifradoIT"`), o PowerShell
  parte el argumento y Maven lo lee como una fase de ciclo de vida inexistente.
- Para iterar sobre **un solo IT** sin pagar los 352 unitarios cada vez, hay que apagar
  Surefire con su propia propiedad — `-DfailIfNoSpecifiedTests=false` (la genérica) **no**
  sirve, Surefire exige la suya con prefijo:
  ```bash
  mvn -B verify -Dtest=SkipAllUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
    -Dit.test=FolioServiceConcurrencyIT
  ```

---

## Fase 1 — CI real y ejecución de los ITs

**Estado: ✅ hecha** — los 14 ITs corren de verdad, la suite entera está verde y CI la
ejecuta en cada push (validado, no supuesto).

### El hallazgo que motiva la fase

Los 14 tests de integración (`*IT.java`) **nunca se han ejecutado, en ningún entorno**.
No es la limitación del host que documentan los sprints: `backend/pom.xml` no declara
`maven-failsafe-plugin`, y los includes por defecto de Surefire (`**/*Test.java`) no
matchean `*IT.java`. Compilan y ahí termina todo.

El caso inverso también existía: `FolioServiceConcurrencyTest` **sí** termina en `Test`,
así que Surefire lo intentaba y fallaba sin Docker — de ahí el "único error conocido"
que arrastran todos los sprints en sus tablas de verificación.

### 1.1 — Binding de los tests · ✅ hecho

- [x] `backend/pom.xml`: `maven-failsafe-plugin` enlazado a `integration-test` + `verify`.
- [x] `FolioServiceConcurrencyTest` → `FolioServiceConcurrencyIT`.

**Resultado:** `mvn test` quedó en **352 tests, 0 fallos, 0 errores** — la primera vez que la
suite unitaria pasa completa. El "único error conocido" que arrastraban todas las tablas de
verificación de los sprints 1–6 era solo este test mal nombrado.

### 1.2 — Primera ejecución real de los ITs · ✅ hecho — suite completa en verde

#### 1.2a — Desbloqueo de Testcontainers · ✅ hecho — **la causa documentada era equivocada**

Los sprints 1–6 atribuyeron el fallo a correr Maven **dentro** de un contenedor (Docker
anidado). **No es eso.** La causa real:

- `docker-java` (el cliente que trae Testcontainers) pide `/v1.32/info`.
- Docker ≥ 29 declara `MinAPIVersion 1.44` y, para versiones por debajo, **no devuelve un
  error**: responde **200 con un JSON degenerado**, todos los campos vacíos
  (`"ID":""`, `"Driver":""`, `"NCPU":0`).
- Testcontainers lo interpreta como `BadRequestException` → *"Could not find a valid Docker
  environment"*. Ese es exactamente el `/info` degenerado que describe `PROGRESS.md`, pero
  el diagnóstico era otro.

Verificado a mano desde el contenedor: `curl --unix-socket /var/run/docker.sock` a
`/v1.32/info` y `/v1.41/info` devuelve el JSON degenerado; `/v1.44/version` responde bien.

**Fix (dos partes):**
1. `backend/pom.xml` — `<docker.api.version>1.44</docker.api.version>` pasada al JVM
   bifurcado por failsafe (`<argLine>-Dapi.version=…</argLine>`). Requiere Docker ≥ 25.
2. `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` **al correr Maven en contenedor**:
   sin esto, Testcontainers publica el JDBC contra `172.17.0.1` (gateway del bridge) y
   Docker Desktop no expone ahí los puertos → `Connection refused`. **Es solo para el
   entorno local**; en CI, Maven corre directo en el runner y no hay que ponerlo.

Con eso, `CafCifradoIT` corrió de verdad: **3/3 verde, BUILD SUCCESS**.

#### 1.2b — Fallout de la suite completa · ✅ hecho — de 53 errores a 0

> **Riesgo conocido:** estos ITs se escribieron entre los sprints 1 y 5 y se fueron
> "migrando al contrato nuevo" sin ejecutarse nunca. El Sprint 6 reescribió firmas
> completas y las migraciones V7–V16 cambiaron el esquema. **Hay que esperar rojo.**
> Ese fallout es el valor de la fase, no un obstáculo.

**Bitácora de ejecuciones**

| # | Comando | Resultado |
|---|---|---|
| 1 | `mvn test` (tras 1.1) | ✅ 352 / 0 fallos / 0 errores |
| 2 | `mvn verify` (sin el fix de 1.2a) | ❌ 14 ITs, 14 errores — todos `Could not find a valid Docker environment` |
| 3 | `mvn verify -Dit.test=CafCifradoIT` (con el fix) | ✅ 3/3 — Testcontainers levanta PostgreSQL 16 |
| 4 | `mvn verify` completo — **línea base real** | ❌ **66 tests de integración: 53 errores, 13 verdes** |
| 5 | tras el fix del singleton | ⏱️ **colgado 25 min** en `FolioServiceConcurrencyIT` (causa C); hubo que matar el contenedor |
| 6 | tras arreglar la barrera de concurrencia y `SiiStubController` | ❌ 66 tests: **1 fallo, 4 errores** |
| 7 | tras el `rutUnicoDeTest()` en 3 ITs | ❌ 66 tests: **1 fallo, 1 error** |
| 8 | tras el `rutUnicoDeTest()` en `LibroCompraVentaIT` | ⚠️ 66 tests: **1 fallo, 0 errores** — solo queda la causa (D) |
| 9 | tras sembrar el `xmlCaf` (causa D) | ✅ **352 unitarios + 66 ITs, 0 fallos, 0 errores** — BUILD SUCCESS en 1:47 min |

**Estado al cierre de la sesión:** `mvn verify` **entero en verde** — 352/352 unitarios y
66/66 tests de integración, 0 fallos y 0 errores, BUILD SUCCESS. De 53 errores a 0 en nueve
ejecuciones. Las 14 clases de IT pasan:

| IT | Tests | IT | Tests |
|---|---|---|---|
| `ContingenciaReenvioIT` | ✅ 14 | `AislamientoMultiTenantIT` | ✅ 4 |
| `EmisionXsdIT` | ✅ 10 | `LoginRateLimitIT` | ✅ 4 |
| `AuthRefreshIT` | ✅ 8 | `CafCifradoIT` | ✅ 3 |
| `BoletaConsumidorFinalIT` | ✅ 5 | `RcofServiceIT` | ✅ 3 |
| `DocumentoServiceTransicionesIT` | ✅ 4 | `NotasCreditoDebitoIT` | ✅ 2 |
| `LibroCompraVentaIT` | ✅ 4 | `RobustezIT` | ✅ 2 |
| | | `PerfilProdContextoIT` | ✅ 2 |
| | | `FolioServiceConcurrencyIT` | ✅ 1 |

**Línea base por clase (ejecución 4)** — la primera foto honesta de los ITs:

| IT | Tests | Errores |
|---|---|---|
| `ContingenciaReenvioIT` | 14 | 14 |
| `EmisionXsdIT` | 10 | 10 |
| `AuthRefreshIT` | 8 | 8 |
| `BoletaConsumidorFinalIT` | 5 | 2 |
| `DocumentoServiceTransicionesIT` | 4 | 4 |
| `LibroCompraVentaIT` | 4 | 4 |
| `CafCifradoIT` | 3 | 3 |
| `RcofServiceIT` | 3 | 3 |
| `NotasCreditoDebitoIT` | 2 | 2 |
| `RobustezIT` | 2 | 2 |
| `FolioServiceConcurrencyIT` | 1 | 1 |
| `AislamientoMultiTenantIT` | 4 | ✅ 0 |
| `LoginRateLimitIT` | 4 | ✅ 0 |
| `PerfilProdContextoIT` | 2 | ✅ 0 |

#### Causa raíz del grueso de los errores: contenedor parado bajo contexto cacheado

No son 53 defectos distintos. `AbstractIntegrationTest` declaraba el PostgreSQL como
`@Container static` bajo `@Testcontainers`, que **para el contenedor en el `afterAll` de
cada clase**. Pero Spring **cachea el contexto** y lo reutiliza en la clase siguiente: su
datasource apuntaba a un PostgreSQL ya muerto, y la clase entera moría con
`CannotCreateTransactionException: Could not open JPA EntityManager for transaction` tras
agotar el timeout de Hikari (de ahí los tiempos absurdos: 181 s, 241 s). Las tres clases
verdes son, justamente, las que alcanzaron a correr con un contenedor vivo.

**Fix aplicado:** patrón **contenedor singleton** en `AbstractIntegrationTest` — se arranca
una vez por JVM en un bloque `static` y no se para entre clases, de modo que su ciclo de
vida coincida con el del contexto cacheado. Se quitaron `@Testcontainers` y `@Container`.

> **Consecuencia a vigilar:** ahora **todas las clases comparten una misma base**. Los ITs
> se escribieron asumiendo BD limpia por clase, así que pueden aparecer colisiones de datos
> (RUT duplicados, conteos que incluyen filas de otra clase). Eso es lo que mide la
> ejecución 5.

#### Resultado de la ejecución 5 (con el singleton) y trabajo restante

El fix redujo los errores de forma sustancial y **eliminó los timeouts** (los tiempos por
clase pasaron de 181 s / 241 s a décimas de segundo). Pasaron a verde completo:
`AuthRefreshIT` (8/8), `RobustezIT` (2/2) y `CafCifradoIT` (3/3), sumándose a
`AislamientoMultiTenantIT`, `LoginRateLimitIT` y `PerfilProdContextoIT`.

Lo que queda son **dos causas raíz distintas**, ambas reales:

**(A) Colisión de datos entre clases — `duplicate key ... "empresa_rut_key"`**
Consecuencia directa y esperada de compartir la base: varios ITs siembran su empresa con
el **mismo RUT fijo**, así que la segunda clase que lo intenta revienta. Es un defecto de
los tests, no del producto.
**Arreglo aplicado:** helper `rutUnicoDeTest()` en `AbstractIntegrationTest`, con un
`AtomicInteger`. El patrón que había —`"91000000-" + random(0,9)`— ofrecía **nueve** valores
posibles para decenas de siembras: las colisiones eran prácticamente seguras (paradoja del
cumpleaños), y de hecho ya fallaban **dentro de una misma clase**, entre métodos. Aplicado
en `RcofServiceIT`, `BoletaConsumidorFinalIT`, `LibroCompraVentaIT` y
`FolioServiceConcurrencyIT`. Se descartó `@Transactional` con rollback por clase: no sirve
donde el test necesita commits reales (concurrencia de folios, contingencia).

**(B) `SiiStubController` no puede inyectarse — defecto de acoplamiento del código de
producción, no de los tests**
```
Error creating bean with name 'siiStubController': Unsatisfied dependency ...
No qualifying bean of type 'cl.nexosoftware.factura.tributario.SiiGatewayStub'
```
`SiiStubController` depende de la **clase concreta** `SiiGatewayStub`. Cuando un IT
sustituye el gateway con `@MockBean SiiGateway`, Mockito reemplaza ese bean por un mock de
la **interfaz** y el tipo concreto desaparece del contexto → el contexto entero no
arranca. Como Spring no reintenta un contexto que ya falló
(`ApplicationContext failure threshold (1) exceeded`), **todas** las clases que comparten
esa firma de contexto caen en cascada: `ContingenciaReenvioIT` (14), `EmisionXsdIT` (10),
`DocumentoServiceTransicionesIT` (4) y `NotasCreditoDebitoIT` (2) — 30 de los errores
restantes salen de aquí.
**Arreglo aplicado:** `SiiStubController` pasa a resolver el stub por
`ObjectProvider<SiiGatewayStub>` en vez de inyectarlo por constructor. Así el controller
siempre se construye y solo falla —con un mensaje explícito— quien de verdad llame al
endpoint sin stub detrás. Es un cambio de **código de producción del perfil dev**; se
prefirió a `@ConditionalOnBean` (cuyo orden de evaluación frente a `@MockBean` es frágil)
y a introducir una interfaz nueva solo para esto.

**(C) `FolioServiceConcurrencyIT` colgaba la build indefinidamente — deadlock garantizado**
La ejecución 5 se quedó **25 minutos sin avanzar** y hubo que matar el contenedor. No era
contención de base (`pg_stat_activity` mostraba todas las conexiones ociosas en
`ClientRead`): el cuelgue estaba en la JVM.

El test lanzaba `hilos = 50` tareas sobre un `newFixedThreadPool(**16**)`. Cada tarea hace
`listos.countDown()` y **queda bloqueada** en `partida.await()`, la barrera de largada
simultánea. Con solo 16 hilos, únicamente 16 tareas llegan a contar; las otras 34 nunca
salen de la cola, `listos` jamás llega a cero y el `listos.await()` **sin timeout** del hilo
principal espera para siempre.

O sea: el test que el README exhibe como la prueba estrella de la seguridad de folios bajo
concurrencia no solo nunca se ejecutó — **no podía pasar**. Habría colgado cualquier CI.

**Arreglo aplicado:** pool de `hilos` hilos (uno por tarea, que es lo que exige una barrera)
y `listos.await(30, SECONDS)` aseverado, para que un fallo futuro **falle** en vez de colgar.

> Vale la pena subrayarlo: (B) y (C) son justo el tipo de defecto que sólo aparece al
> ejecutar los ITs. (B) estuvo latente desde el Sprint 5; (C), desde el Sprint 1.

**(D) `FolioServiceConcurrencyIT` — deriva contra el contrato del Sprint 6 · ✅ arreglada**

Con la barrera ya arreglada, el test **falla** (ya no cuelga) con `folios` vacío: las 50
tareas lanzan. La causa es que el `@BeforeEach` crea el CAF **sin `xmlCaf`**:

```java
cafRepository.save(Caf.builder()
        .empresaId(empresaId).tipoDte(TipoDte.FACTURA_AFECTA)
        .folioDesde(1).folioHasta(1000).folioActual(0)
        .agotado(false).creadoEn(OffsetDateTime.now())
        .build());   // <-- sin xmlCaf
```

Desde **P0-5 (Sprint 6)** un CAF sin XML no sirve para timbrar, así que el selector de
folios los **salta** (y `V7` marcó agotados los legacy). El test nunca se actualizó a ese
contrato porque nunca se ejecutó.

**Confirmado en la ejecución 8** — el `Queue<Throwable> fallos` que se agregó al test lo dice
sin ambigüedad: `ReglaNegocioException: No hay folios disponibles ni vigentes para Factura
electronica`, lanzada desde `FolioService.siguienteFolio` (`FolioService.java:26`).

**Arreglo aplicado:** `.xmlCaf(DteFixtures.xmlCaf(33))` en el `@BeforeEach`, que es el mismo
patrón que ya usaban `DocumentoServiceTransicionesIT`, `ContingenciaReenvioIT`, `EmisionXsdIT`
y `NotasCreditoDebitoIT` — este IT era el único que se había quedado atrás. Con eso pasa
**1/1** y la suite entera queda verde (ejecución 9).

Dos precauciones que se anotaron aquí y que, al ejecutarlas, resultaron **no aplicar**:

- **El `RE` del CAF frente al RUT de la empresa.** No hace falta alinearlos ni cambiar el
  `rutUnicoDeTest()` por el RUT del fixture. Este test no llega a timbrar: sólo llama a
  `siguienteFolio()`, que bloquea el CAF e incrementa el folio sin mirar el contenido del XML
  (`bloquearCafDisponible` sólo exige `xmlCaf is not null`). Alinear los RUT habría
  reintroducido las colisiones de `empresa_rut_key` que arregló la causa (A).
- **`APP_MASTER_KEY` en el contexto de test.** No hubo nada que configurar; el cifrado del
  `SecretoTextoConverter` funciona en los ITs tal cual (`CafCifradoIT` ya lo cubría).

### 1.3 — Workflow de GitHub Actions · ✅ hecho — verde en la primera ejecución

- [x] `.github/workflows/ci.yml` con dos jobs: `backend` (JDK 21 temurin, caché maven,
      `mvn -B verify`, sube los reportes de test como artefacto) y `frontend`
      (Node 20, `npm ci`, `npm run build`). Dispara en `push` a `main`, `pull_request`
      y `workflow_dispatch`.
- [x] Job `frontend` **validado a mano** antes del push: `npm ci` + `npm run build` → `tsc`
      sin errores y `vite build` en 1.93 s (1664 módulos). El `package-lock.json` sí está
      versionado y el script `build` es `tsc && vite build`, tal como asume el workflow.
- [x] **Validado con un push real.** [Run 30467377561](https://github.com/tomasvergaraj/nexo-factura/actions/runs/30467377561),
      el primero del proyecto, **verde en los dos jobs**: `backend` 1m25s (352 unitarios +
      66 ITs, 0 fallos, 0 errores, BUILD SUCCESS en 1:09) y `frontend` 20s. Los ITs levantan
      PostgreSQL con Testcontainers en el runner sin ninguna configuración extra: el
      `TESTCONTAINERS_HOST_OVERRIDE` es sólo para correr Maven en contenedor localmente.
- ✅ **El punto frágil no lo era.** El paso de diagnóstico imprimió
      `Server 28.0.4 — API 1.48 (min 1.24)`: el pin a `1.44` entra holgado y no hubo que
      tocar `-Ddocker.api.version`. Ojo con la asimetría, que es la que causó todo el
      problema local: el runner declara `min 1.24` y acepta APIs viejas, mientras que el
      Docker 29.2.1 de la máquina de desarrollo declara `min 1.44` y responde a las
      anteriores con el `/info` degenerado de la sección 1.2a.
- ⚠️ **Aviso pendiente (no rompe nada):** GitHub anota que `actions/checkout@v4`,
      `setup-java@v4`, `setup-node@v4` y `upload-artifact@v4` apuntan a Node 20, ya
      deprecado, y el runner los fuerza a Node 24. Subirlos a `@v5` es un follow-up de la
      Fase 4.

#### Bloqueador de CI encontrado y corregido

`backend/src/test/resources/sii/cert_prueba.p12` —el certificado **sintético** de los
tests (autofirmado, RUN `11111111-1`, clave `test123`)— **nunca estuvo versionado**: la
regla global `*.p12` del `.gitignore` lo excluía. Existe solo en esta máquina, así que en
un clon limpio (o sea, en CI) fallarían todos los tests que lo usan.
`PROGRESS.md` afirma que el fixture estaba commiteado; **no lo estaba**.

Corregido con una excepción explícita en `.gitignore` (solo esa ruta; `secrets/`, `*.pfx`
y el resto de los `*.p12` siguen ignorados).

---

## Fase 3 — Documentación al día

**Estado: ✅ hecha**

Lo del **23–24 de julio no está documentado en ningún lado**. `PROGRESS.md` dice
"última actualización 2026-07-22" y `ROADMAP.md` 2026-07-21; faltan ~12 commits y las
migraciones **V8–V16**.

- [x] **`docs/PROGRESS.md`** — bloque nuevo (Sprint 7) con: multi-tenant del certificado
      y la resolución por empresa (V13), cifrado en reposo del XML del CAF (V14), corte a
      producción sobre palena, seed de demo fuera de prod, dashboard con datos reales,
      envío de libros IECV al SII (V15), job de revisión automática (V16) y el fix de
      rollback del marcador. Más la **emisión de humo en palena verificada** como gate de
      cierre, y el resultado de la Fase 1.
- [x] **`docs/ROADMAP.md`** — §12 con el sprint y actualización del saldo (§10).
- [x] **`README.md`** — es lo más desalineado: todavía se describe como *"proyecto de
      portafolio… la integración tributaria está aislada tras interfaces y simulada"* y
      *"no como un producto en producción ante el SII"*, falso desde el corte. Actualizar
      además la tabla de endpoints (certificado por empresa, libros `enviar` / `pendientes`
      / `estado`), las variables de config nuevas (`APP_LIBRO_REVISION_*`,
      `APP_SII_FIRMA_MODO=POR_EMPRESA`) y el rango de migraciones (V1–V16, dice V1–V6).

---

## Fase 2 — `fctProp` persistente

**Estado: ✅ hecha** — factor por empresa, con el override por período intacto y un factor
sugerido que se ofrece como pista.

**El problema que resolvía.** [`RevisionLibroService.revisarOperacion`](../backend/src/main/java/cl/nexosoftware/factura/libro/RevisionLibroService.java)
llamaba a `xmlFirmado(...)` con `fctProp = null`, y `LibroXmlGenerator` exige el factor si
el libro trae IVA de uso común. Consecuencia: **cualquier período con uso común quedaba en
`ERROR` de forma permanente** y el aviso automático no servía para ese libro. Peor de lo
documentado: la UI tampoco ofrecía cómo informarlo (ver el hallazgo al final del checklist).

- [x] Migración `V17__fct_prop.sql`: `fct_prop` nullable en `empresa` **y en `envio_libro`**.
      **No es `NUMERIC(3,2)`,** como decía este plan: el factor viaja como `Double` de punta a
      punta y la validación de esquema de Hibernate tumba el contexto entero con *«wrong column
      type … found [numeric], but expecting [float(53)]»*. Lo cazó el IT, no la compilación.
      Queda `DOUBLE PRECISION` con un `CHECK` de rango, que además es mejor garantía: un
      `NUMERIC(3,2)` habría **redondeado** un `0.605` en silencio en vez de rechazarlo.
- [x] `Empresa` + `EmpresaRequest`/`EmpresaResponse` (`@DecimalMin`/`@DecimalMax`) + campo en
      `Configuracion.tsx`, validado 0–1 y solo editable por ADMIN como el resto de la pantalla.
- [x] El job usa el valor de la empresa. **La resolución no vive en el job**: está en
      `LibroService.construir`, el único punto por el que pasan *todos* los caminos —preview de
      la UI, XML de descarga, envío y revisión automática—. Puesta en el job, el preview habría
      mostrado crédito proporcional 0 mientras el XML declarado al SII llevaba otro número.
- [x] El override por período del envío manual sigue ganando al valor de la empresa.
- [x] Si hay uso común y no hay factor, el marcador queda en `ERROR` con **mensaje accionable**
      y **sin intentar firmar**. El mensaje del generador (*«informe el factor (fctProp)»*) se
      dejó como está: le sirve a quien llama la API, no a quien mira la UI y necesita saber
      *dónde* se configura.
- [x] `envio_libro.fct_prop` guarda el factor **efectivamente declarado en cada envío**. El de
      la empresa es editable, así que sin esto, después de cambiarlo no habría forma de saber
      qué se declaró en un envío ya hecho.
- [x] Factor **sugerido** (`GET …/libros/factor-proporcionalidad`): ventas afectas sobre totales
      acumuladas desde enero, ofrecido como pista junto al campo y **nunca aplicado solo**. La
      respuesta incluye `documentos` y `primeraEmision` justamente para que se vea si el
      acumulado arranca de verdad en enero — que es la incertidumbre que motivó todo el diseño.

**Un hallazgo del camino:** el plan daba por hecho que «el envío manual desde `Libros.tsx` lo
sigue pudiendo sobreescribir por período». **`Libros.tsx` no menciona `fctProp` en ninguna
parte**: el override sólo existía por la API. O sea que el problema era mayor de lo
documentado — con IVA de uso común, el libro de compras no se podía enviar **en absoluto**
desde la UI, no sólo desde el job. El default por empresa arregla los dos casos; añadir el
input de override a `Libros.tsx` queda como follow-up de la Fase 4, ya sin urgencia.

> **Decisión de diseño — resuelta el 2026-07-29.** El usuario aprobó la recomendación de
> abajo: **factor por empresa**, con los dos ajustes al checklist y el factor sugerido. El
> planteamiento original de este recuadro («por período es lo legalmente correcto, por empresa
> es la simplificación») quedó descartado por el argumento que sigue.

**La recomendación, y por qué el encuadre original estaba mal planteado.** No era
«simple vs. legalmente correcto»:

- **El cálculo automático no sería más correcto, sería equivocado con más confianza.** La
  fórmula necesita las ventas **acumuladas desde enero**, y el sistema sólo conoce los DTE que
  emitió él. Si la empresa adoptó nexo-factura a mitad de año o vende por otro canal, el
  acumulado está incompleto y el factor sale mal **en silencio, dentro de una declaración
  tributaria**. En este dominio, «automático pero mal» es peor que «manual y a la vista de quien
  responde por el número», que aquí es el propio contribuyente: **no hay contador en el medio**
  (confirmado por el usuario), así que el sistema es la única salvaguarda y no puede permitirse
  un valor plausible pero incorrecto.
- **El costo real es bajo.** `fctProp` ya viaja como parámetro de punta a punta
  (`LibroController` → `LibroService` → `LibroEnvioService` → generador). El campo por empresa
  sólo aporta un default donde hoy el job pasa `null` a pelo:
  [`RevisionLibroService`](../backend/src/main/java/cl/nexosoftware/factura/libro/RevisionLibroService.java)
  líneas 63 y 73.

Dos ajustes al checklist de arriba, ambos ya implementados:

1. **Persistir el factor usado en cada período**, no sólo el default de la empresa. Si alguien
   edita la constante entre el primer envío y un reenvío del mismo período, hoy se declararían
   dos números distintos para el mismo libro. El valor declarado tiene que ser reproducible.
2. **Calcular un factor sugerido** con las ventas que el sistema sí tiene y mostrarlo junto al
   campo como pista (*«según tus ventas registradas: 0.87»*), **nunca** como valor automático.
   Captura casi todo el valor del cálculo por período a una fracción del costo.

> **Resuelto el 2026-07-29.** La duda era si el acumulado desde enero está completo en el
> sistema para el caso concreto de Nexo Software. No lo está ni puede estarlo: la emisión en
> producción arrancó **este mismo mes**, así que para cualquier período de 2026 faltan siete
> meses de ventas. El primer argumento no solo se sostiene, se vuelve certeza.
>
> **Cuándo revisarlo:** a partir de un año calendario que el sistema haya cubierto entero
> (2027, si se usa desde enero). El `primeraEmision` del factor sugerido es justamente el dato
> que lo delata, así que no hace falta acordarse: cuando sea el 1 de enero, el argumento cae.
>
> Nota para quien retome esto: **el usuario no tiene contador**. Cualquier texto que derive la
> decisión a uno —había dos en `Configuracion.tsx`— está mandando al usuario con alguien que no
> existe. La referencia correcta es el **F29**, que es donde ese mismo factor se informa.

---

## Fase 4 — Follow-ups (a elegir después)

**Estado: ⬜ no comprometida** — ordenados por relación valor/esfuerzo, no se harán todos.

| Item | Nota |
|---|---|
| ~~Consulta automática del estado de los envíos de libro~~ | ✅ **hecha** — segundo `@Scheduled` en `RevisionLibroJob` (cron propio, `app.libro.revision-auto.cron-estado`) que resuelve a diario los envíos sin estado terminal |
| ~~Subir las actions de CI a `@v5`~~ | ✅ **hecha** — se va el aviso de Node 20 deprecado |
| ~~Input de override de `fctProp` en `Libros.tsx`~~ | ✅ **hecha** — aparece solo cuando el período trae IVA de uso común |
| ~~Motivo de fallo por documento en el reenvío masivo~~ | ✅ **hecha** — `ReenvioResultado` lleva el motivo por documento y el dashboard los lista. Sin él, la respuesta decía «N siguen en contingencia» y había que abrir cada uno |
| ~~Signo de las NC en los totales del libro~~ | ⚠️ **ya estaba hecho** — `LibroService.signo()` resta los tipos 60/61 del agregado mostrado y deja el XML por `TpoDoc` en positivo, con tests. El follow-up estaba obsoleto, no pendiente |
| ~~Semántica de `RECHAZADO` entre RCOF y libro~~ | ✅ **hecha** — una boleta rechazada por el SII pasa a folio **anulado** (sin montos), decisión del usuario. Ver abajo |
| `MedioPago` / `GeoRefEmision` | Campos opcionales del DTE |
| Verificación de la FRMA del CAF | **No accionable**: el SII no publica la clave pública por IDK. Queda como límite conocido documentado |

### `RECHAZADO` entre RCOF y libro — resuelto el 2026-07-29

**Decisión del usuario: la boleta rechazada cuenta como folio anulado**, sin montos
(`RcofService.FOLIO_SIN_DOCUMENTO_VALIDO` agrupa `ANULADO` y `RECHAZADO`). El folio se sigue
reportando —el RCOF existe para que ninguno del rango quede sin explicar— pero sus montos ya no
entran, así que el RCOF deja de contradecir al libro del mismo período. Cubierto por IT.

Lo que había antes, y por qué era la peor de las tres lecturas:

- **Libro de ventas** ([`LibroDtos`](../backend/src/main/java/cl/nexosoftware/factura/libro/LibroDtos.java)):
  excluye los `RECHAZADO`. Un DTE rechazado por el SII no es una emisión válida.
- **RCOF** ([`RcofService`](../backend/src/main/java/cl/nexosoftware/factura/rcof/RcofService.java)):
  sólo separaba `ANULADO` de todo lo demás, así que una boleta `RECHAZADO` caía en «vigentes» y
  **sumaba montos**.

Resultado: una boleta que el SII rechazó se declaraba como folio utilizado **con sus montos**, y
a la vez no aparecía en el libro. Sobredeclaraba ventas que el propio SII había rechazado.

La alternativa descartada era dejarla como utilizada pero con monto cero. Conserva la distinción
entre «yo la anulé» y «el SII la rechazó», pero declara un folio utilizado sin montos, que ante
el SII se parece bastante a un error de cuadratura.

> **Por qué se preguntó en vez de decidirlo.** Es semántica tributaria, no una elección técnica,
> y va en un envío real al SII. Con dos lecturas defendibles y sin contador que revise después,
> la decisión es del usuario.
