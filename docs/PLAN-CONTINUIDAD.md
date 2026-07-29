# Plan de continuidad — CI, `fctProp` y documentación

> **Propósito de este archivo.** Es el estado vivo del trabajo en curso, pensado para
> **retomar en una sesión distinta sin contexto previo**. Cada fase lleva su estado, lo
> que ya se hizo y el siguiente paso concreto. Al terminar todas las fases este documento
> se archiva (su contenido definitivo vive en `PROGRESS.md`).
>
> Última actualización: **2026-07-29**.

## Empieza aquí — mensaje para la próxima sesión

Sesión del **2026-07-29**. Se cerraron las fases 1 y 3 en tres commits (`f1508c9`,
`2831e79`, `ec9f37a`). El árbol está limpio y **no se hizo push**.

### Estado en una línea
`mvn test` **352/352 verde**; `mvn verify` en **66 tests de integración con 1 fallo**
(desde 53 errores). Ese fallo está aislado, diagnosticado y es lo primero de la lista.

### Qué hacer, en este orden

**1. Cerrar el último IT rojo — `FolioServiceConcurrencyIT`** *(lo más corto y lo que deja
la suite entera en verde)*
Está todo en la causa **(D)** de la Fase 1.2b: el test siembra su CAF sin `xmlCaf` y, desde
P0-5, el selector de folios descarta los CAF sin XML. Hay que sembrarlo con
`sii/caf_prueba_33.xml`. Ojo con el `RE` del CAF frente al RUT de la empresa sembrada y con
que el `SecretoTextoConverter` cifra al escribir. El test ya imprime la excepción exacta de
las tareas, así que iterás con evidencia y no a ciegas.

**2. Push y validación de CI** — *requiere decisión del usuario, preguntá antes*
`.github/workflows/ci.yml` está escrito pero **nunca se ha ejecutado**; solo un push lo
valida. Si se hace antes del punto 1, **el primer run vendrá en rojo** por ese IT. Las dos
opciones razonables: empujar ya y asumir el rojo como línea base visible, o cerrar el punto 1
y empujar en verde. Vale la pena mirar en el log del job el paso *«Versión de Docker del
runner»*: si su API no cubre `1.44`, hay que bajar `-Ddocker.api.version`.

**3. Fase 2 — `fctProp`** — *bloqueada por una decisión de diseño, preguntá primero*
No la empieces sin resolver con el usuario si el factor va **por empresa** (simple, desbloquea
el job) o **calculado por período** desde las ventas (más correcto legalmente, bastante más
trabajo). Está planteada al final de la sección de la Fase 2.

**4. Fase 4** — follow-ups sueltos, ninguno urgente. El de mejor relación valor/esfuerzo es la
consulta automática del estado de los envíos de libro: hoy es manual por TrackID y el job ya
tiene el andamiaje.

### Dos cosas que conviene saber antes de tocar nada

- **El entorno no tiene Java ni Maven.** Todo pasa por el contenedor `maven`. Los comandos
  exactos están abajo, y el de `verify` **no funciona sin** el montaje del socket y el
  `TESTCONTAINERS_HOST_OVERRIDE`.
- **La documentación de este repo describía cosas que no eran ciertas.** Tres ejemplos que
  costó descubrir: los ITs «corrían en CI» (no existía CI), el fixture `cert_prueba.p12`
  estaba «commiteado» (lo excluía el `.gitignore`) y el fallo de Testcontainers se atribuía a
  Docker anidado (era la versión de API). **Verificá las afirmaciones antes de apoyarte en
  ellas**, sobre todo las de las tablas de verificación de `PROGRESS.md`. Lo que sí está
  confirmado de primera mano: la emisión de humo en producción sobre palena, que el usuario
  dio por verificada y funcionando.

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

---

## Fase 1 — CI real y ejecución de los ITs

**Estado: 🟡 en curso**

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

### 1.2 — Primera ejecución real de los ITs · 🟡 en curso

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

#### 1.2b — Fallout de la suite completa · 🟡 en curso

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

**Estado al cierre de la sesión:** `mvn test` **352/352 verde**; `mvn verify` en **66 tests de
integración con 1 solo fallo**, que es la causa (D) de abajo, aislada y con el siguiente paso
escrito. De 53 errores a 1. El diagnóstico que ahora emite el propio test:

```
ninguna emision debe fallar; primer fallo: ReglaNegocioException:
No hay folios disponibles ni vigentes para Factura electronica. Cargue un nuevo CAF desde el SII.
```

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

**(D) `FolioServiceConcurrencyIT` sigue rojo — deriva contra el contrato del Sprint 6 · ⬜ ESTE ES EL SIGUIENTE PASO**

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

*Para retomar:* sembrar el CAF con el XML del fixture `sii/caf_prueba_33.xml`. A vigilar:
el `RE` del CAF debe cuadrar con el RUT de la empresa sembrada (por lo que quizá convenga
usar el RUT del fixture en vez de `rutUnicoDeTest()` en **este** IT), y el
`SecretoTextoConverter` cifra al escribir, así que hay que confirmar que el contexto de test
resuelva `APP_MASTER_KEY` (en `dev` hay default; verificar qué aplica sin perfil activo).

### 1.3 — Workflow de GitHub Actions · ✅ escrito, sin validar

- [x] `.github/workflows/ci.yml` con dos jobs: `backend` (JDK 21 temurin, caché maven,
      `mvn -B verify`, sube los reportes de test como artefacto) y `frontend`
      (Node 20, `npm ci`, `npm run build`). Dispara en `push` a `main`, `pull_request`
      y `workflow_dispatch`.
- [ ] **Validar con un push real.** No se puede comprobar desde esta máquina.
- ⚠️ **Punto frágil:** el job imprime a propósito la versión de API del daemon del runner.
      Si ese Docker no llega a `1.44`, hay que bajar `-Ddocker.api.version` a lo que soporte
      (cualquier Docker ≥ 25 cubre 1.44, así que no debería pasar).

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

**Estado: ⬜ pendiente**

Hoy [`RevisionLibroService.revisarOperacion`](../backend/src/main/java/cl/nexosoftware/factura/libro/RevisionLibroService.java)
llama a `xmlFirmado(...)` con `fctProp = null`, y `LibroXmlGenerator` exige el factor si
el libro trae IVA de uso común. Consecuencia: **cualquier período con uso común queda en
`ERROR` de forma permanente** y el aviso automático nunca sirve para ese libro.

- [ ] Migración `V17__empresa_fct_prop.sql`: `fct_prop NUMERIC(3,2)` nullable en `empresa`.
- [ ] `Empresa` + `EmpresaRequest`/`EmpresaResponse` + campo en `Configuracion.tsx`
      (validado 0–1, solo ADMIN como el resto de la pantalla).
- [ ] El job usa el valor de la empresa; el envío manual desde `Libros.tsx` lo sigue
      pudiendo sobreescribir por período.
- [ ] Si hay uso común y no hay factor configurado, el marcador sigue en `ERROR` pero con
      **mensaje accionable** ("configure el factor de proporcionalidad en Configuración")
      en vez de la excepción cruda del generador.

> **Decisión de diseño pendiente de confirmar con el usuario.** El factor legalmente es
> **por período** (acumulado desde enero), no una constante de la empresa. Se eligió el
> default por empresa por simplicidad y porque desbloquea el job; calcularlo automáticamente
> desde las ventas del período es más correcto pero bastante más trabajo. Si se decide
> cambiar de enfoque, esta fase se replantea.

---

## Fase 4 — Follow-ups (a elegir después)

**Estado: ⬜ no comprometida** — ordenados por relación valor/esfuerzo, no se harán todos.

| Item | Nota |
|---|---|
| Consulta automática del estado de los envíos de libro | Hoy `estadoEnvio` es manual por TrackID; el job ya tiene el andamiaje |
| Motivo de fallo por documento en el reenvío masivo | Follow-up de P2-5, mejora la operación real |
| Signo de las NC en los totales del libro | Follow-up de P2-5 |
| Semántica de `RECHAZADO` entre RCOF y libro | Follow-up de P2-5, hoy inconsistente |
| `MedioPago` / `GeoRefEmision` | Campos opcionales del DTE |
| Verificación de la FRMA del CAF | **No accionable**: el SII no publica la clave pública por IDK. Queda como límite conocido documentado |
