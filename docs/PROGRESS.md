# Progreso

> Última actualización: 2026-07-22. Sprints 1 a 6 **completados y verificados**, más el **sitio público y la Configuración del emisor** (post-Sprint 5). E2E de certificación cumplido en ambos canales y en **los cinco tipos de DTE soportados: factura 33, boleta 39, factura exenta 34, nota de débito 56 y nota de crédito 61 — todas ACEPTADAS por el SII**.
> Planes: [SPRINT-1-PLAN.md](SPRINT-1-PLAN.md), [SPRINT-2-PLAN.md](SPRINT-2-PLAN.md). Backlog: [ROADMAP.md](ROADMAP.md).

# Sprint 1

## Resumen

Sprint 1 convirtió a Nexo Factura de un **demo con frontend 100% mock e IDOR sistémico** en un sistema **seguro, cableado a la API real y verificado de extremo a extremo**, sin depender de certificados/CAF externos. En el camino se detectó y corrigió un **bug latente que rompía todo el flujo de documentos** contra el backend real.

## Qué se implementó

### Seguridad multi-tenant (cierre de IDOR) — P0-1
- Nuevo principal `UsuarioPrincipal` (id/email/rol/empresaId) poblado desde los claims del JWT en `JwtAuthenticationFilter`; helper `SecurityUtils`.
- `JwtService` expone `extraerEmpresaId/Rol/Uid` (con `Number.longValue()` para evitar `ClassCastException`).
- **`TenantGuard`** único (`@tenantGuard.checkEmpresa(#empresaId)`) aplicado con `@PreAuthorize` a nivel de clase en los 5 controllers scoped por empresa (cliente, producto, documento, folio/CAF, dashboard) y en `EmpresaController`.
- **Roles** (`ADMIN`/`EMISOR`) en mutaciones sensibles: emitir, enviar, cargar CAF; `ADMIN` en gestión de empresa.
- **Fix IDOR por fila**: `ClienteService.actualizar` y `ProductoService.actualizar` pasan a firma `(empresaId, id, req)` y scopean con `findByIdAndEmpresaId(...)` → 404 si la fila no pertenece.
- Contrato de errores: **403** cross-tenant (autenticado sin permiso), **404** fila ajena (no filtra existencia), **401** sin token.

### Frontend cableado a la API real — P0-2
- `USE_MOCK` ahora deriva de `import.meta.env.VITE_USE_MOCK === "true"` (**default `false`** → API real). Mock queda como opt-in.
- Archivos `.env`, `.env.development`, `.env.example`; tipado en `vite-env.d.ts`.
- `empresaId` se toma del usuario logueado (`empresaIdActual()`), ya no del literal `1`.
- Login real contra `POST /api/auth/login` (captura el 401 de credenciales localmente).
- Interceptor de respuesta axios: 401/403 → cierra sesión y redirige a login (con guard anti-loop).
- `RequireAuth` protege `/app/*`.

### Correcciones de corrección — P0-3 / P2-1
- **Hardening del secret JWT**: el default de dev vive solo en `application-dev.yml`; `application.yml` no trae default; `JwtSecretValidator` (`@Profile("prod")`) aborta el arranque si el secret falta, es <32 bytes o es el de dev.
- **`estado-sii` idempotente**: de `@GetMapping` a `@PostMapping` (tenía efectos de escritura siendo GET).

### Tests — P2-2
- `EstadoDteTransicionesTest`: unitario de la máquina de estados (transiciones válidas/inválidas, terminal, no auto-transición).
- `DocumentoServiceTransicionesIT`: integración (Testcontainers) del flujo emitir/enviar con stubs mockeados.
- `AislamientoMultiTenantIT`: integración con MockMvc; cross-tenant 403, mismo-tenant 2xx, sin token 401, fila ajena 404 sobre los 5 controllers.

## Bug latente detectado y corregido (no estaba en el plan)

Al cablear el frontend a la API real surgió un fallo que el modo mock ocultaba: **toda operación por-id de documento devolvía 500** (`obtener`, `emitir`, `enviar`, `estado-sii`, `pdf`).

- **Causa**: `@EntityGraph(attributePaths = {"lineas", "referencias"})` en `DocumentoRepository.findWithDetalleById` hacía *fetch* de **dos colecciones `List` (bags) a la vez** → `MultipleBagFetchException` de Hibernate.
- **Por qué estaba oculto**: el frontend era 100% mock, así que estos endpoints **nunca se ejercitaron** contra el backend real.
- **Fix**: el `EntityGraph` solo hace *fetch* de `lineas`; `referencias` carga de forma perezosa dentro de la transacción del servicio.

Arreglos adicionales del mismo pase:
- **401 real** para peticiones sin token (antes el `Http403ForbiddenEntryPoint` por defecto devolvía 403).
- **405** para método no soportado y **logging del stack** en el handler genérico (antes degradaba a 500 sin trazabilidad — hallazgo del audit).

## Verificación

| Gate | Resultado |
|---|---|
| `docker compose build` (backend Java + frontend `tsc`) | ✅ ambas imágenes |
| Tests unitarios (cálculo + máquina de estados) | ✅ **40/40** |
| Tests con Testcontainers (concurrencia + IT) | ⚠️ compilan; no ejecutables en este host (ver nota) |
| E2E — propia empresa con token | 200 ✅ |
| E2E — cross-tenant (empresa ajena) | **403** ✅ |
| E2E — sin token | **401** ✅ |
| E2E — documento inexistente | **404** ✅ |
| E2E — `estado-sii` GET (verbo viejo) | **405** ✅ |
| E2E — crear → emitir → enviar → estado | BORRADOR → FIRMADO (folio 1) → ENVIADO (trackId) → ACEPTADO ✅ |
| E2E — obtener / pdf | 200 / 200 ✅ |

> **Nota sobre los tests de integración (Testcontainers).** En este host (Windows + Docker Desktop) los tests con Testcontainers **no se pudieron ejecutar dentro del contenedor `maven`**: Testcontainers no detecta un daemon Docker válido al correr anidado (el socket montado de Docker Desktop responde un `/info` degenerado → `BadRequestException`). El fallo ocurre en el arranque del contenedor Postgres (`@BeforeAll`), no en código de la aplicación. Estos tests **compilan** y se ejecutan normalmente en un runner Linux de CI. Mientras tanto, los comportamientos que verifican (seguridad 401/403/404 y la máquina de estados emitir→enviar→estado) están **confirmados en vivo** por la batería E2E contra el stack real (`docker compose up`).

## Cómo correr la verificación

```bash
# Build (gate de compilación de ambos)
docker compose build

# Tests unitarios (sin Docker-in-Docker)
docker run --rm -v "$PWD/backend:/app" -v nexo_m2:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-21 mvn -B test -Dtest='CalculadoraImpuestosTest,EstadoDteTransicionesTest'

# Stack + E2E manual
docker compose up -d
```

# Sprint 2

## Resumen
Completitud tributaria y de producto, todo verificable sin certificado/CAF reales: **notas de crédito/débito** con anulación del documento referenciado, **validación de RUT (módulo 11)** en el backend, **timbre PDF417 real** en el PDF, **frontend completo** (CRUD + detalle de DTE + notas) y cierre del **riesgo de arquitectura** (perfil de producción).

## Qué se implementó

### Notas de crédito/débito (56/61) — P1-1
- `DocumentoService.crear` exige referencias para NC/ND y valida coherencia con el documento original (existe, mismo tipo/folio/fecha, no borrador).
- `DocumentoService.emitir`: al emitir una **NOTA_CREDITO con `ANULA_DOCUMENTO`**, transiciona el documento original **ACEPTADO → ANULADO** en la misma transacción (atómico con la reserva de folio).
- `DocumentoResponse` ahora incluye `referencias[]` (`ReferenciaResponse`); `DocumentoRepository.findByEmpresaIdAndTipoDteAndFolio`.

### Validación de RUT (módulo 11) — P1-3
- `common/validation/Rut` (algoritmo módulo 11, espejo de `format.ts`), anotación `@RutValido` + validador; aplicada en `ClienteRequest`/`EmpresaRequest`. RUT con DV inválido → **400** con error de campo.

### Timbre PDF417 real — (parte de P0-5, verificable)
- `Pdf417Generator` (zxing) genera el código de barras del **TED real** (ISO-8859-1); `TedGenerator.aXml` marshalla el `<TED>`; `PdfDteServiceImpl` lo embebe en el PDF (escalado para caber). La firma FRMT del TED sigue siendo placeholder hasta integrar el CAF real.

### Cierre del riesgo de arquitectura — (perfil de producción)
- Perfil de producción estandarizado a **`prod`**; stubs → `@Profile("!prod")`; nuevos `FirmaElectronicaProd`/`SiiGatewayProd` (`@Profile("prod")`) que **fallan fail-fast** (`UnsupportedOperationException`) en vez de faltar. El contexto **levanta** en perfil prod.

### Frontend completo — P1-5
- Pantallas reales de **Clientes**, **Productos** y **Folios** (CRUD + carga de CAF), nueva **vista de detalle de DTE** (`/app/documentos/:id`) con acciones por estado y descarga de PDF, **selector de tipo de DTE** y referencias para NC/ND en NuevaFactura, filas de Documentos navegables. El interceptor axios ya **no cierra sesión ante 403** (permiso/negocio).

## Bug detectado y corregido (PDF417 no se embebía)
El timbre agregaba la imagen como `Chunk` inline con `scalePercent(220%)`; al ser más ancha que la línea, OpenPDF la **descartaba en silencio** (PDF de ~1.8 KB sin imagen). Se corrigió agregando la imagen como elemento de bloque con `scaleToFit`, más **logging** del fallback (antes silencioso). PDF resultante ~6.6 KB con `/Image` embebida.

## Verificación

| Gate | Resultado |
|---|---|
| `docker compose build` (backend + frontend `tsc`) | ✅ |
| Tests unitarios (RUT, cálculo, estados, PDF417) | ✅ **57/57** |
| Tests con Testcontainers (NC/ND, perfil prod, aislamiento) | ⚠️ compilan; no ejecutables en este host (igual que Sprint 1) |
| E2E — RUT inválido / válido | 400 / 201 ✅ |
| E2E — carga de CAF (NOTA_CREDITO) por ADMIN | 201 ✅ |
| E2E — NC anulatoria → factura original | **ANULADO** ✅ |
| E2E — NC sin referencias | 409 ✅ |
| E2E — PDF con PDF417 embebido | 200, ~6.6 KB, `/Image` ✅ |
| E2E — frontend sirve + proxy `/api` | 200 ✅ |
| Smoke — arranque con perfil `prod` | **Started en 5.6s** con beans Prod ✅ |

> Nota: actualizar el seed `V2__seed_dev.sql` (se corrigieron los DV de los RUT demo) cambia su checksum de Flyway. En una BD de dev ya migrada hay que **resetear el volumen** (`docker compose down -v && docker compose up -d`); un clon nuevo aplica las migraciones limpias.

# Sprint 3 (sin activos SII)

## Resumen
La integración tributaria real (firma XMLDSig, FRMT + CAF, SII) sigue **gateada por un certificado y un CAF reales** aún no disponibles. Se completó en cambio **P1-2** — boletas y RCOF — todo verificable sin esos activos: **boletas 39/41** con precio bruto (IVA incluido), **receptor "Consumidor final"** (cliente opcional) y el **RCOF** diario con su endpoint y XML.

## Qué se implementó

### Boletas con monto bruto (39/41) — P1-2
- `TipoDte.preciosBrutos()` (true solo para 39/41). `CalculadoraImpuestos` gana una sobrecarga `calcular(lineas, tasaIva, preciosBrutos)`: cuando los precios son brutos **desglosa el neto del total afecto** (`neto = round(afecto/(1+tasa))`) y el `iva = afecto − neto` (resta, sin segundo redondeo → `neto+iva == bruto` exacto). La sobrecarga de 2 args (facturas/notas) delega sin cambios.
- `DocumentoService.aplicarTotales` pasa `tipoDte.preciosBrutos()`; ningún otro flujo cambia.

### Receptor "Consumidor final" — P1-2
- `clienteId` ahora **opcional**. Una boleta sin cliente toma receptor `66666666-6` / "Consumidor final"; factura/NC/ND sin cliente → **409**. Sin cambios de esquema (las columnas NOT NULL siempre se pueblan).

### RCOF — Reporte de Consumo de Folios — P1-2
- Nuevo paquete `rcof`: `RcofService` agrega por empresa+fecha las boletas foliadas, separando **utilizados** (estado ≠ ANULADO) de **anulados** con sus rangos; los anulados se cuentan pero **no suman monto**. Endpoint `GET /api/empresas/{id}/rcof?fecha=` (+ `/xml`) bajo el `@tenantGuard`.
- `ModeloConsumoFolios` + `RcofXmlGenerator` materializan el XML `ConsumoFolios` (subconjunto, ISO-8859-1). **No se firma ni se envía al SII** (requiere certificado y secuencia de envío real; `secEnvio` es placeholder), igual que el resto del flujo tributario.

### Frontend — P1-2
- `NuevaFactura` ofrece boletas en el selector, con **cliente opcional** (badge "Consumidor final"), precio rotulado **"IVA incl."** y un preview con el mismo desglose bruto que el backend. Nueva página **Consumo de folios (RCOF)** con selector de fecha y tabla por tipo (utilizados/anulados/rangos/montos), enlazada en el sidebar.

### Modelo JAXB completo + validación XSD pre-firma — P1-4
- **Bloque `Referencia` en el XML**: `ModeloDte` gana la clase `Referencia` y `XmlDteGenerator` la puebla (entre `Detalle` y `TED`). Antes las notas 56/61 guardaban la referencia en la BD pero **nunca la emitían en el XML**; ahora sí.
- **`DteXmlValidator`**: valida el XML contra un esquema **representativo** (`resources/sii/DTE.xsd`) en `emitir`, **entre generar y firmar**. Si no cumple, lanza `DteInvalidoException` → **422** con el detalle de cada error de esquema, y revierte la reserva de folio (misma `@Transactional`). Endurecido contra XXE; valida vía `StringReader` para no chocar con la declaración ISO-8859-1. Conmutable con `app.dte.validar-xsd` (default `true`). El XSD es representativo (sin namespace `SiiDte`, sin `Signature` real): el alineamiento al oficial es follow-up gated por firma/CAF.
- **Dos bugs detectados por el review adversarial y corregidos** (ambos del tipo "el XSD rechazaría un DTE legítimo"): (1) `QtyItem`/`TasaIVA` se marshallaban desde `double` y JAXB emite notación científica (`1.0E7`) para cantidades ≥1e7, inválida como `xs:decimal` → se agregó `PlainDecimalAdapter` que fuerza la forma decimal plana (`10000000`); (2) `@RutValido` aceptaba RUT con puntos (`76.543.210-9`) pero el XSD los rechaza → `Rut.normalizar` ahora canoniza el RUT en los mappers de Empresa/Cliente (también cierra el duplicado con/sin puntos).

### Robustez del DTE — P2-4
- **Inmutabilidad**: los campos tributarios de `DocumentoTributario` (emisor implícito, receptor, montos, tipo, fecha, observación) pasan a `@Column(updatable=false)` → Hibernate los excluye del `UPDATE`, así que una vez creado el DTE su contenido tributario no puede mutar (folio/estado/xmlDte/trackId/sello siguen mutables por el ciclo de vida).
- **Sello de integridad**: `SelloDte` calcula el SHA-256 (hex) del XML firmado; se fija al emitir, se guarda en la columna `sello` y se expone en `DocumentoResponse` (y en el detalle del front). Permite detectar manipulación posterior del XML almacenado.
- **Duplicados → 409**: `GlobalExceptionHandler` mapea `DataIntegrityViolationException` a **409** (antes caía al genérico 500). Un RUT/código duplicado ahora responde 409 con mensaje claro.
- **Bloqueo optimista**: `@Version` en `Empresa`/`Cliente`/`Producto` (CAF ya lo tenía); una escritura sobre una versión obsoleta lanza `OptimisticLockingFailureException`, también mapeada a **409**. Migración `V3__robustez_version_sello.sql` (aditiva: `version BIGINT NOT NULL DEFAULT 0` + `sello VARCHAR(64)`).

### Sesión y seguridad — P2-3
- **Refresh tokens**: token opaco de 256 bits (`SecureRandom`, base64url); en la BD vive **solo su hash SHA-256** (`RefreshTokenService` + `RefreshToken` + migración `V4`). Login y registro entregan access JWT (ahora corto, **60 min**) + refresh (**14 días**).
- **Rotación + detección de reuso**: cada `/refresh` revoca el token presentado y emite uno nuevo. Si llega un token cuyo hash ya está revocado se asume robo y se **revoca toda la cadena** del usuario. La revocación va en una transacción `REQUIRES_NEW` para que persista aunque la transacción principal aborte al rechazar (401). Orden de validación: existe → no-revocado(reuso) → no-expirado → activo.
- **Revocación**: `POST /api/auth/logout` revoca el refresh (idempotente); `POST /api/auth/refresh` lo rota. Errores de token → **401** con mensaje genérico (anti-enumeración). *(Gap documentado: el access JWT viejo sigue válido hasta su expiración de 60 min; la revocación dura es de refresh tokens.)*
- **Rate limiting**: `RateLimiter` en memoria (thread-safe, `Clock` inyectable, barrido + tope), cuenta intentos fallidos por **email** y por **IP**; al superar el límite → **429** con `Retry-After`. Aplicado a login (reset en éxito) y registro (por IP). Config `app.security.rate-limit.*`.
- **Frontend**: el interceptor axios hace **auto-refresh transparente** en 401 (single-flight, un reintento por petición, nunca sobre rutas `/auth/*`); `cerrarSesión` revoca el refresh en el servidor (best-effort). Login maneja el 429.

## Verificación

| Gate | Resultado |
|---|---|
| `mvn test` (compila main + tests en Docker `maven`) | ✅ 93 fuentes main + 20 test |
| Tests unitarios (cálculo, XSD, marshaller→XSD, RUT, sello, rate limiter) | ✅ **59/59** (`Calculadora` 10, `DteXmlValidator` 12, `XmlDteGeneratorXsd` 3, `Rut` 21, `SelloDte` 4, `RateLimiter` 9) |
| `tsc --noEmit` (frontend) | ✅ sin errores |
| Review adversarial del diff (P1-2/P1-4/P2-4/P2-3) | ✅ P1-2, P2-4 y P2-3 sin defectos; P1-4 corrigió 2 bugs; en P2-3 se detectó y **corrigió en código** que la revocación de cadena (reuso) requería `REQUIRES_NEW` para no perderse al abortar la transacción |
| Tests con Testcontainers (`...IT`: boleta, RCOF, emisión XSD, robustez, **AuthRefreshIT**, **LoginRateLimitIT**) | ⚠️ compilan; no ejecutables en este host (corren en CI) |

> Casos de redondeo clave cubiertos: `11900→10000/1900`, `100→84/16`, `999→839/160`, `9999→8403/1596` (donde el re-redondeo ingenuo daría 159/1597), boleta mixta afecto+exento y la equivalencia de la sobrecarga sin flag con el cálculo neto.

# Sprint 4 (P1-6, sin activos SII)

## Resumen
Se completó **P1-6 — impuestos adicionales y retenciones**, verificable sin certificado/CAF reales: un catálogo representativo de otros impuestos (ILA de bebidas, suntuarios) y la **retención de IVA por cambio de sujeto**, calculados, persistidos, emitidos en el XML como bloques `ImptoReten` (igual que el SII real, **sin** el inexistente `IVARetTotal`) y validados contra el XSD pre-firma. Se usó el método de los sprints anteriores: **workflow de diseño** (3 propuestas + verificación de fidelidad SII + síntesis) → implementación → **review adversarial** → gate de build en Docker → commit.

## Qué se implementó

### Catálogo de otros impuestos — P1-6
- Nuevo enum `TipoImpuesto` (representativo, espejo de `CATALOGO_IMPUESTOS` del front): suntuarios (23, 15%), ILA destilados (24, 31,5%), vinos (25, 20,5%), cervezas (26, 20,5%), analcohólicas (27, 10%), azucaradas (271, 18%) como **adicionales**; e **IVA retenido total / cambio de sujeto** (15, 19%) como **retención**. Documentado como subconjunto curado, no la tabla oficial.

### Cálculo — P1-6
- `CalculadoraImpuestos`: cada línea afecta puede llevar un `codImpAdic`. La base se **agrega por código** (suma del neto de las líneas marcadas) y el monto se redondea **una sola vez por código** (half-up), para casar 1:1 con el bloque `ImptoReten` del XML. `Totales` se extiende con `impuestosAdicionales`, `ivaRetenido` y el desglose `impuestos[]`. **Total = neto + exento + IVA + Σ(adicionales) − Σ(retenido)**. El desglose lo expone un método `static desglosarImpuestos(...)` que consumen la calculadora, el generador de XML y el mapper (**fuente única de verdad**).
- Alcance: solo en documentos de **precios netos y afectos** — factura afecta (33) y notas (56/61). Boletas (39/41), factura exenta (34) y líneas exentas se **rechazan (409)** si traen un código; código desconocido → 409. (La retención de IVA real opera sobre Factura de Compra (45), fuera del enum `TipoDte`; aquí se modela de forma representativa.)

### XML + XSD — P1-6
- `ModeloDte`: bloque repetible `ImptoReten` (`TipoImp`/`TasaImp`/`MontoImp`) en `Totales` **después de `IVA` y antes de `MntTotal`**; `CodImpAdic` en `Detalle` **entre `IndExe` y `MontoItem`** (posiciones del esquema oficial). Tanto adicionales como la retención viajan como `ImptoReten` — el DTE **no tiene `IVARetTotal`** (lo confirmó la verificación SII del workflow). `DTE.xsd` extendido con elementos `minOccurs=0` → el corpus sin impuestos sigue válido.

### Persistencia, PDF y frontend — P1-6
- Migración **V5** aditiva: `linea_detalle.cod_imp_adic` (nullable) y `documento_tributario.impuestos_adicionales`/`iva_retenido` (NOT NULL DEFAULT 0, **inmutables** `updatable=false` como el resto de los totales). Sin tabla de catálogo (es un enum).
- PDF: filas por impuesto adicional (+) y retención (−) entre IVA y TOTAL, para que el total cuadre con lo visible.
- Frontend: selector de impuesto por línea (solo en tipos/líneas afectas; se resetea al cambiar tipo o producto), **cálculo en vivo espejo exacto del backend** (mismo orden de operaciones de redondeo) y desglose en el detalle del DTE.

## Verificación

| Gate | Resultado |
|---|---|
| `mvn test` (compila main + tests en Docker `maven`) | ✅ |
| Tests unitarios | ✅ **108** (`CalculadoraImpuestos` 19, `XmlDteGeneratorXsd` 5, + resto) |
| `tsc --noEmit` + `vite build` (frontend) | ✅ |
| Workflow de diseño (3 propuestas + verificación SII + síntesis) | ✅ — corrigió **3 errores de semántica SII** antes de implementar: `IVARetTotal` no existe en el DTE, `CodImpAdic` va **antes** de `MontoItem`, y los códigos/tasas del catálogo |
| Review adversarial del diff | ✅ — 1 hallazgo confirmado (desfase de $1 en el preview a 20,5% por orden de operaciones del redondeo), **corregido**; se alineó también el orden latente del IVA |
| Tests con Testcontainers (`EmisionXsdIT`: emisión con `ImptoReten`, rechazos de scope) | ⚠️ compilan; no ejecutables en este host (corren en CI) |

> Casos clave cubiertos: adicional suma (ILA 10% sobre 100000 → +10000), agregación de base por código antes de redondear (3333+3334 → 6667 → 667, no 666), retención total resta el IVA (50000 → total 50000), combinado adicional+retención, exento/sin-código fuera de la base, boleta ignora códigos, y orden de `ImptoReten`/`CodImpAdic` en el XML que cumple el XSD.

# Sprint 5 (P2-5, sin activos SII)

## Resumen
Se completó **P2-5 — contingencia, reenvío de rechazados y libros de compra/venta**, verificable sin certificado/CAF reales. Método de los sprints anteriores: diseño (contrato congelado) → implementación → **review de 8 ángulos en paralelo** (línea a línea, comportamiento eliminado, trazado cross-file, reuso, simplificación, eficiencia, altitud, convenciones) → correcciones → gate de build en Docker → commit.

## Qué se implementó

### Contingencia de envío al SII — P2-5
- Nuevo estado **`EN_CONTINGENCIA`**: si el SII no está disponible al enviar (`SiiNoDisponibleException`), el documento NO falla — queda en cola de reintento con traza persistente: `intentos_envio`, `ultimo_envio_en`, `ultimo_error_envio` (migración `V6`). Solo un FIRMADO entra a contingencia; la caída del SII en otras operaciones (consulta de estado) responde **503**.
- **Reintento individual** (`POST /documentos/{id}/reenviar`, ADMIN/EMISOR) y **masivo** (`POST /documentos/reenviar-pendientes`): el masivo procesa cada documento en **su propia transacción** (`TransactionTemplate`) y captura cualquier fallo por documento — un TrackID ya aceptado por el SII jamás se revierte por un fallo posterior del lote (evita duplicar envíos). La consulta trae solo IDs (no materializa N columnas `xml_dte`).
- **Stub del SII configurable en runtime**: `PUT /api/dev/sii-stub` (perfil ≠ prod, **solo ADMIN**: el estado del stub es global al proceso) permite simular la caída (`disponible=false`) y el rechazo (`estadoConsulta=RECHAZADO`) para ejercitar contingencia y reenvío E2E; propiedades `app.sii.stub.*` para el estado inicial. `SiiGatewayProd` documenta que la implementación real debe mapear errores de transporte a `SiiNoDisponibleException`.
- Dashboard: KPI `enContingencia` + banner con **"Reintentar envíos"** (actualiza contadores desde la respuesta, sin recargar todo); filtro `EN_CONTINGENCIA` en la página Documentos; detalle del DTE con aviso, intentos y botón **Reenviar al SII**.

### Reenvío de rechazados — P2-5
- `RECHAZADO → ENVIADO` reenviando el **mismo XML firmado** (el DTE es inmutable y su folio ya fue consumido; ante un rechazo de fondo corresponde emitir un documento nuevo). Se **eliminó `RECHAZADO → BORRADOR`** (violaba la inmutabilidad).
- Un rechazo del SII es una decisión **de fondo, no una caída transitoria**: si el reenvío de un RECHAZADO falla por SII caído, el documento **permanece RECHAZADO** (traza registrada) — no entra a la cola de reintento automático ni re-entra a los libros.
- `POST /estado-sii` ahora exige estado ENVIADO/REPARO (el TrackID puede quedar como traza en estados posteriores; antes un estado obsoleto producía un 409 de "transición inválida" confuso).

### Libros de compra/venta (IECV) — P2-5
- **Libro de ventas** (`GET /api/empresas/{id}/libros/ventas?periodo=YYYY-MM`): agrega los DTE foliados del período con las reglas del IECV — **boletas solo resumidas** (sin detalle por documento), **anulados marcados con montos en cero** (no suman), **rechazados excluidos**; otros impuestos y retención del DTE viajan al resumen. La consulta usa una **proyección** que no carga el `xml_dte` de cada documento, y el orden es por **código SII + folio** (ordenar en SQL por el enum ordenaría por su nombre: 61 antes que 56).
- **Registro de compras** (`/api/empresas/{id}/compras`, CRUD): documentos recibidos (33/34/46/56/61) con unicidad `(empresa, tipo, folio, proveedor)` → 409, RUT validado/normalizado y coherencia **`total = neto + exento + IVA − IVA retenido`** (la retención del comprador —cambio de sujeto, típica de la factura de compra 46— no puede exceder el IVA). Tabla `documento_compra` (V6).
- **Libro de compras** desde ese registro (misma consulta de período que el listado — un `default` del repositorio como punto único de la regla). **XML `LibroCompraVenta`** (EnvioLibro: Carátula, `TotalesPeriodo` por tipo, `Detalle` con `<Anulado>A</Anulado>`) representativo, sin firmar. Marshalling JAXB centralizado en `JaxbXml` (contexto cacheado) compartido por DTE, RCOF y libros.
- Frontend: página **Compras** (período local —no UTC—, formulario con retención, sin parpadeo al refrescar, guard anti-respuesta-obsoleta) y página **Libros (IECV)** (tabs ventas/compras, resumen + detalle, descarga del XML **como Blob** para no re-codificar a UTF-8 un archivo que declara ISO-8859-1).

## Review (8 ángulos) — hallazgos corregidos
Los 8 buscadores en paralelo devolvieron 40+ candidatos; tras deduplicar y verificar se corrigieron, entre otros: (1) el reenvío masivo corría en UNA transacción — un fallo tardío revertía TrackIDs ya aceptados por el SII (→ transacción por documento); (2) `RECHAZADO → EN_CONTINGENCIA` conflaba rechazo de fondo con caída transitoria y re-metía el documento a libros y cola automática (→ eliminado); (3) el KPI de monto del mes excluía los `EN_CONTINGENCIA` (documentos legalmente emitidos); (4) el libro se ordenaba por nombre del enum, no por código SII; (5) la descarga del XML re-codificaba a UTF-8 contradiciendo el prólogo ISO-8859-1 (→ Blob); (6) el endpoint dev del stub —estado global— era mutable por cualquier usuario autenticado (→ solo ADMIN); (7) `MES_ACTUAL` en UTC mostraba el período equivocado por la tarde-noche en Chile (→ helpers de fecha local); (8) el libro de ventas cargaba el `xml_dte` completo de cada DTE (→ proyección); (9) la factura de compra 46 con retención era irrepresentable (→ `iva_retenido` en compras); (10) el filtro de la página Documentos no ofrecía el estado nuevo. Además: marshalling JAXB unificado (3 copias → `JaxbXml` con caché), labels de tipos derivados, mocks del libro derivados de las compras mock, y guards anti-race en Compras.

## Verificación

| Gate | Resultado |
|---|---|
| `mvn test` (compila main + tests en Docker `maven`) | ✅ |
| Tests unitarios | ✅ **149** (`LibroService` 9, `CompraValidacion` 9, `LibroXmlGenerator` 4, transiciones ampliadas, + resto) |
| `tsc --noEmit` (frontend) | ✅ sin errores |
| `docker compose build` (backend + frontend) | ✅ |
| Review de 8 ángulos + correcciones | ✅ (ver arriba) |
| Tests con Testcontainers (`ContingenciaReenvioIT` 8, `LibroCompraVentaIT` 4, + resto) | ⚠️ compilan; no ejecutables en este host (corren en CI) |

> Follow-ups documentados de P2-5: signo de las NC en los totales agregados del libro, unificar la semántica de RECHAZADO entre RCOF y libro, y motivo de fallo por documento en la respuesta del reenvío masivo.

# Sitio público y Configuración del emisor (post-Sprint 5)

## Resumen
Cierre de los **callejones sin salida de la navegación**: el footer y el nav del sitio apuntaban a rutas que no existían, y **Configuración** era el último `Placeholder` ("en construcción") de la app. Commit `e1e834f`. No toca el backend: se apoya en endpoints que ya existían (`GET`/`PUT /api/empresas/{id}`, `/actuator/health`).

## Qué se implementó

### Sitio público
- Páginas nuevas bajo rutas de primer nivel: **Sobre** (`/sobre`), **Contacto** (`/contacto`), **Términos** (`/terminos`), **Privacidad** (`/privacidad`) y **Estado del servicio** (`/estado`).
- **Layout compartido `SitePage`** (nav + cabecera + contenido + footer) con `ProseSection` para los bloques de texto legal. Restaura el scroll al tope —react-router conserva la posición al navegar desde el footer— y fija el `document.title` por página.
- **Footer y nav cableados**: los enlaces dejan de apuntar a rutas inexistentes. La navegación a anclas de la Landing usa `Link` + hash-scroll (SPA) en vez de recargar la página desde una subpágina.
- **Estado del servicio en vivo**: `comprobarSalud()` consulta `/actuator/health` y muestra UP/caído. Va por `axios` **directo, sin el interceptor** de `http` — un 401/403 en un endpoint público jamás debe cerrar la sesión ni redirigir al login. Timeout de 8 s y `catch` → "caído" (nunca propaga el error a la UI).
- Contacto no tiene formulario propio: enlaza por `mailto:` (no hay servicio de correo en el backend, y un formulario que no envía nada sería otro callejón sin salida).

### Configuración del emisor
- `Configuracion.tsx` reemplaza a `Placeholder` en `/app/configuracion`; **`Placeholder.tsx` se eliminó** (era su único uso).
- Carga con `GET /api/empresas/{id}` y guarda con `PUT`, usando `empresaIdActual()` (nunca un id literal). Tipos `Empresa`/`EmpresaRequest` como espejo de `EmpresaResponse` del backend.
- **Validación de RUT en el cliente** (`validarRut`, el mismo módulo 11 del backend) más obligatoriedad de razón social, giro, dirección y comuna, y actividad económica numérica. Los errores de campo del **400 del backend** se mapean sobre los mismos campos vía `erroresDeCampo`, así que la validación local es una mejora de latencia, no la única defensa.
- **Modo lectura para `EMISOR`**: los campos van `disabled` y el botón Guardar no se renderiza, reflejando en la UI el `@PreAuthorize` de ADMIN que ya protegía `EmpresaController`. El backend sigue siendo la autoridad (403 si se intenta igual).
- Guard anti-respuesta-obsoleta (`activo`) en la carga inicial.

### Infra del frontend
- Proxy de Vite corregido a **`localhost:8082`** (el backend en Docker está mapeado ahí porque el 8080 del host lo ocupa otra app) y añadido `/actuator/health`.
- `nginx.conf`: `location = /actuator/health` proxyeado al backend. Es un **match exacto**, no un prefijo: solo el health queda público, el resto de actuator no se expone.

## Verificación

| Gate | Resultado |
|---|---|
| `tsc --noEmit` + `vite build` (frontend) | ✅ |
| `docker compose build` | ✅ |
| Rutas del footer/nav sin destino roto | ✅ |
| `/actuator/health` público (en `PUBLIC_PATHS` de `SecurityConfig`, expuesto en `application.yml`) | ✅ |
| Configuración: carga, guarda y refleja errores de campo del backend | ✅ |

# Sprint 6 (P0-4/5/6: integración tributaria real)

## Resumen
Con el **certificado PKCS#12 real** y **dos CAF de certificación** (boleta 39 y factura 33) por fin disponibles, se implementaron los tres P0 que separaban a la app de la validez tributaria: **P0-5** (parseo/validación del CAF + firma real del TED), **P0-4** (XMLDSig real + alineamiento al esquema oficial `SiiDte`) y **P0-6** (integración real con el SII por sus DOS canales: API REST de boleta y flujo clásico SOAP de facturas). Diseño con contrato congelado y correcciones de fidelidad previas en [SPRINT-6-PLAN.md](SPRINT-6-PLAN.md) — la más importante: el SII fija **RSA-SHA1** por schema (no SHA-256), y las boletas **no** van por Maullín sino por la API REST (pangal=cert, rahue=prod). Método de siempre: diseño → implementación por fases → review multi-ángulo → correcciones → build en Docker → **E2E contra el ambiente de certificación real**.

## Qué se implementó

### P0-5 — CAF real y timbre (TED) real
- **`folio/CafParser`**: parsea y valida el `<AUTORIZACION>` del SII — estructura completa, `D<=H`, coherencia RSASK↔RSAPK (módulos iguales + firma/verificación de un vector de prueba), clave privada **PKCS#1 decodificada con un lector DER propio** (9 INTEGERs → `RSAPrivateCrtKeySpec`, sin dependencias nuevas) y extracción **verbatim** del `<CAF>` (substring, jamás re-serializado: su firma FRMA debe sobrevivir byte a byte).
- **Alta de CAF por XML** (D10): `CafRequest` es solo el XML; tipo/rango/fechas se derivan del parseo. Validaciones al cargar: `RE == RUT de la empresa` (409), TD soportado, duplicado exacto → 409; `fechaVencimiento = FA + 6 meses` **solo** para tipos con crédito fiscal (33/43/46/56/61, Res. 58/2017) — los CAF de boleta no vencen. Al asignar folio se saltan CAF vencidos **y CAF legacy sin XML** (sin él no se puede timbrar; migración `V7` los marca agotados).
- **`TedGenerator` real** (D5): produce el TED como **string aplanado** (regla oficial A.2.4 aplicada al DD COMPLETO: sin EOL/blancos entre tags — el `<CAF>` embebido también se aplana, conservando byte a byte sus valores terminales; el SII re-aplana lo recibido antes de verificar), valores escapados solo con `& < >` (escapar comillas rompería la fidelidad byte del FRMT a través de JAXB), RSR/IT1 truncados a 40 y **FRMT = base64(SHA1withRSA(bytes ISO-8859-1 del DD aplanado))** con la clave privada del CAF — verificado en tests con la clave pública del mismo CAF. Ese string único alimenta el documento (subárbol DOM renombrado al namespace, sin `xmlns` textual) y el PDF417.
- **`FolioAsignado`**: la asignación devuelve folio + CAF de origen, y `emitir` timbra con el CAF exacto del folio (más guard `caf.re == emisor.rut`, 409 defensivo).
- **El PDF extrae el TED del XML firmado almacenado** (substring), nunca lo regenera — con FRMT real, regenerarlo duplicaría la firma y podría divergir del documento enviado.

### P0-4 — Firma XMLDSig real y esquema oficial
- **`tributario/CertificadoDigital`** (`@Profile("prod")`): carga el PKCS#12 (`app.sii.certificado-path/password`), fail-fast al arrancar (existe, abre, vigente) y expone `rutFirmante()` (SERIALNUMBER del subject, fallback `app.sii.rut-firmante`).
- **`FirmaElectronicaProd`**: XMLDSig con el JDK según el schema oficial `xmldsignature_v10.xsd`, que **fija por `fixed`/enumeración**: C14N inclusive, **`rsa-sha1`**, digest `sha1`, `KeyInfo(KeyValue, X509Data)`. Reference enveloped `URI="#<ID>"` (DTE/SetDTE) o `URI=""` (getToken). DOM namespace-aware con `setIdAttribute`, serialización ISO-8859-1 sin indentación (D4: el DTE va en una línea — elimina de raíz la deriva byte-a-byte del TED y de la firma).
- **Namespace oficial `SiiDte` en todo el paquete** (`package-info.java` con `elementFormDefault=QUALIFIED`) y **rama boleta del generador** (`ModeloBoleta`, C4: el schema de boleta es otro — `RznSocEmisor`/`GiroEmisor`, sin `Acteco` ni `TasaIVA`, `IndServicio=3` obligatorio, `TmstFirma` al cierre). Facturas ganan `Acteco` y `TmstFirma`, y se exige `GiroRecep`/`DirRecep`/`CmnaRecep` (obligatorios del Formato) al emitir 33/56/61.
- **XSD oficiales como única validación** (D7): vendoreados en `resources/sii/oficial/` (`DTE_v10`, `EnvioBOLETA_v11`, `EnvioDTE_v10`, `SiiTypes`, `xmldsignature` + wrapper local de 6 líneas para validar la boleta suelta). El `DTE.xsd` representativo **se eliminó**. La validación pasa a ser **post-firma** (el schema exige la `Signature`): `generar → firmar → validar XSD → sello` en la misma transacción; `FirmaElectronicaStub` (dev) ahora emite una firma falsa pero con **forma schema-válida**.

### P0-6 — SII real por los dos canales
- **`SiiGatewayProd`** rutea por tipo (D1): 39/41 → **API REST de boleta**; 33/34/56/61 → **canal clásico SOAP**. Contrato de errores unificado: transporte/5xx → `SiiNoDisponibleException` (la contingencia del Sprint 5 queda intacta); 4xx/rechazo de negocio → error duro con detalle. Token inválido → renovar y reintentar **una** vez (`TokenInvalidoSii` + `SiiTokenAuth`).
- **Canal boleta** (`SiiAuthClient` + `EnvioBoletaGenerator` + `SiiTransporteBoleta`): semilla → `getToken` firmado `URI=""` → token cacheado (`TokenCache` compartido, renovación serializada bajo lock); sobre `EnvioBOLETA_v11` (carátula con `RutReceptor=60803000-K`, `FchResol`/`NroResol` de config, `RutEnvia` del certificado, DTE embebido **verbatim** sin su declaración XML) **validado contra el XSD antes de cada POST**; envío multipart a pangal/rahue con `Cookie: TOKEN` y estado por `{rut}-{dv}-{trackid}` en apicert/api. Mapeo de estados con la regla central **EPR ≠ aceptado** (decide la `estadistica`).
- **Canal clásico** (`SiiDteAuthClient` + `EnvioDteGenerator` + `SiiTransporteDte` + `SiiSoap`): semilla `CrSeed.jws` y token `GetTokenFromSeed.jws` (SOAP rpc/encoded armado a mano — los WSDL de cert referencian hosts internos inaccesibles), sobre `EnvioDTE_v10` (misma carátula; generador unificado con el de boleta en `EnvioGenerator`, FchResol fail-fast al arrancar), upload `cgi_dte/UPL/DTEUpload` en maullin/palena, estado por `QueryEstUp.jws` (glosas de STATUS 1-99 mapeadas; estados 001-003 = token inválido). Las respuestas declaran utf-8 pero vienen en ISO-8859-1: se decodifican explícito.
- **Config**: `app.sii.fch-resol/nro-resol/rut-firmante/user-agent` (el WAF del SII bloquea los User-Agent de librerías); `docker-compose.cert.yml` (override: perfil `prod` + `CERTIFICACION`); compose base intacto (dev 100% stub).
- **Frontend Folios**: alta de CAF subiendo/pegando el XML (decodificado ISO-8859-1 explícito), con vista previa derivada (`lib/caf.ts`, espejo ligero del parseo del backend compartido con el mock).

## Review multi-ángulo — hallazgos corregidos
Cuatro ángulos en paralelo sobre el diff completo (reuso/altitud, seguridad/regresiones, fidelidad SII contra los XSD vendoreados, línea a línea crypto/transportes). Corregido tras la primera tanda: (1) **CAFs legacy sin `xml_caf` bloqueaban la emisión** a mitad de camino — el selector de folios ahora los filtra y `V7` los marca agotados; (2) generadores de sobre unificados (`EnvioGenerator`) con la validación de FchResol **fail-fast consistente** (antes: constructor en boleta, lazy en factura); (3) cache de token y helpers de transporte deduplicados (`TokenCache`, `SiiTokenAuth`, `TokenInvalidoSii`, `Rut`, `SiiXml`, base `SiiTransporteBase` con el multipart común); (4) limpieza de código muerto (imports, `@Slf4j` sin uso, `"EPR"` inalcanzable en `EN_PROCESO`); (5) test espejo `SiiTransporteDteTest` (24 casos, mapeo `getEstUp` extraído como en el canal de boleta); (6) parseo de preview del CAF compartido en el frontend.

Segunda tanda — **fidelidad contra los XSD** (verificada elemento a elemento, no de memoria): (7) `PrcItem` se emitía siempre, pero `Dec12_6Type` exige mínimo 0.000001 — una **línea de regalo (precio 0)**, práctica normal, moría con 422 al emitir; ahora el elemento se omite (es opcional) y el precio negativo se rechaza en el DTO; (8) `QtyItem` sin acotar — se normaliza a **6 decimales half-up al crear** (lo almacenado = lo emitido) con mínimo/máximo del esquema; (9) cotas estructurales chequeadas **al crear** y no como 422 de schema al emitir: máx **60 líneas** por factura (1000 en boleta), **40 referencias**, `Acteco` positivo de ≤6 dígitos, fechas dentro del rango de `FechaType` (2000–2050, atrapa el typo de año). **Línea a línea crypto/transportes**: (10) un **rechazo de negocio durante el reenvío masivo** dejaba el documento EN_CONTINGENCIA para siempre, golpeando al SII indefinidamente — ahora transiciona `EN_CONTINGENCIA → RECHAZADO` (transición nueva) con el motivo; (11) **caracteres fuera de ISO-8859-1** (em-dash, comillas de Word) se degradaban EN SILENCIO a `?` en el documento legal, el TED firmado y el PDF417 — ahora el timbre y el generador los rechazan con 422 listando los caracteres (en todos los perfiles, para que dev reproduzca a prod); (12) doble token inválido consecutivo escapaba como excepción sin mensaje (500 opaco / contingencia "sin error") — ahora se traduce con diagnóstico de habilitación del certificado; (13) `invalidar()` incondicional podía pisar el token que otro hilo acababa de renovar — ahora invalida **solo el token que falló**; (14) `STATUS=9` del upload clásico (sistema SII bloqueado, transitorio) se trataba como rechazo duro — ahora es contingencia; y un 401/403 HTTP del upload ahora renueva el token como en el canal de boleta; (15) el lector DER del CAF aceptaba largos sin cota (un CAF malicioso podía pedir ~2 GB → `OutOfMemoryError`, o producir una clave corrupta rellenada con ceros) — largos acotados al buffer.

**Límite conocido (documentado, no resuelto en este sprint)**: si el envío se corta DESPUÉS de que el SII recibió el sobre (timeout leyendo la respuesta), el documento queda EN_CONTINGENCIA sin TrackID y el reintento **sube el mismo sobre otra vez**: en el canal clásico el SII responde `STATUS=99` (duplicado; la glosa ahora instruye conciliar el TrackID en el portal antes de reenviar) y en boleta el segundo envío será rechazado por folio repetido aunque el primero haya sido aceptado. La solución de fondo es **reconciliación por folio** (consulta de estado por documento) antes de reenviar — follow-up documentado. La FRMA del CAF tampoco se verifica (el SII no publica su clave pública por IDK) — un CAF con el DA editado a mano se detecta recién al primer rechazo; también documentado en el javadoc de `CafParser`.

## Hallazgos del E2E — bugs que ningún test local podía ver
El gate real (SII de certificación) cazó **seis bugs invisibles para la suite**, cada uno corregido y — donde aplica — con guardarraíl permanente:
1. **XSD inaccesibles dentro del fat jar**: en el jar de Boot los recursos viven bajo el protocolo `nested:`, que la allowlist `ACCESS_EXTERNAL_SCHEMA="file,jar"` de JAXP no reconoce — el backend ni siquiera arrancaba en perfil prod (los tests corren con classpath de filesystem y jamás lo verían). Fix: acceso externo cerrado del todo (`""`) + `LSResourceResolver` que sirve los import/include desde el classpath.
2. **Doble constructor sin `@Autowired`**: `EnvioBoletaGenerator`/`EnvioDteGenerator` tienen un segundo constructor package-private (Clock para tests) y Spring no elige entre dos — el contexto prod no levantaba (el IT que lo cubre corre solo en CI).
3. **`Content-Length` obligatorio**: el SII rechaza con 400 los POST `Transfer-Encoding: chunked`, y el multipart en streaming del RestClient no emite Content-Length. Fix: multipart materializado a mano como `byte[]` (largo conocido → header fijo).
4. **`standalone="no"` en la declaración XML**: el Transformer del JDK lo agrega al serializar el sobre firmado y el detector de esquemas del SII (literal) responde `SCH-00001: Invalid Schema Name` en ambos canales. Fix: declaración escrita a mano (prólogo canónico), `OMIT_XML_DECLARATION` en el serializador.
5. **Rechazo 505 "Firma DTE Incorrecta" por C14N inclusive**: el SII verifica la firma del DTE **extrayéndolo del sobre**, y el serializador de la firma eliminaba las declaraciones de namespace redundantes del `<DTE>` interno — extraído, el `Documento` pierde el contexto con que se canonizó al firmar y el digest no calza. Fix doble: `xmlns:xsi` declarado en la raíz del DTE al generarlo (contexto idéntico dentro y fuera del sobre) + re-inyección de las declaraciones en el `<DTE>` interno tras firmar el sobre (inocua para la firma del SetDTE: una re-declaración idéntica a la heredada no se rinde en C14N). Guardarraíl: `FirmaDentroDelSobreTest` verifica la firma del DTE en los tres modos (standalone, en contexto del sobre y extraído) con el pipeline prod completo.
6. **Reparo 510 "Firma Timbre Electrónico Incorrecta"**: la regla de aplanado A.2.4 aplica al **DD completo** — el SII re-aplana lo recibido (incluido el bloque CAF) antes de verificar el FRMT, así que el CAF embebido con sus saltos de línea originales producía bytes distintos. Fix: el CAF se aplana dentro del DD (solo whitespace ENTRE tags; los valores terminales quedan byte-idénticos). Con esto el reparo desapareció (folio 4).

Bugs de fidelidad cazados por los tests durante la implementación (guardarraíles para no regresar): `DescuentoMonto=0` es inválido en factura (`MntImpType` exige mínimo 1 → el elemento se omite); el TED se parsea con `StringReader` (es un fragmento sin encoding declarado — parsearlo por bytes rompía los acentos); el TED escapa **solo** `& < >`.

La ronda de cierre del E2E (34/56/61, 2026-07-22) cazó dos más:

7. **Documento exento con IVA declarado**: `ModeloDte.Totales` emitía siempre `MntNeto`/`TasaIVA`/`IVA` (primitivos) — schema-válido, pero el SII rechaza a nivel de documento una factura exenta 34 que declare IVA (folio 1, sin glosa en el QueryEstUp agregado). Fix: campos nulables y omisión cuando el documento no tiene monto afecto (espejo de lo que la rama boleta ya hacía); guardarraíl en `XmlDteGeneratorXsdTest`. Validado con el folio 2 ACEPTADO.
8. **Conexión cortada LEYENDO la respuesta del SII → 500 en vez de contingencia**: maullin cerró el socket a mitad de la respuesta del upload y la excepción salió como `RestClientException` plano (no `ResourceAccessException`, que solo cubre el fallo al conectar) — escapaba como error 500 al usuario. Fix: catch de `RestClientException` residual → `SiiNoDisponibleException` (contingencia) en los cuatro puntos de transporte (upload clásico, SOAP, envío y estado de boleta).

## Verificación

| Gate | Resultado |
|---|---|
| `mvn test` (compila main + tests en Docker `maven`) | ✅ |
| Tests unitarios | ✅ **231** (Sprint 6: `CafParser` 9, `TedGenerator` 7, `FirmaElectronicaProd` 8, `FirmaDentroDelSobre` 2, `DteXmlValidator` 14, `EnvioBoletaGenerator` 4, `EnvioDteGenerator` 2, `SiiTransporteBoleta` 19, `SiiTransporteDte` 24, `XmlDteGeneratorXsd` 10 — todos contra los XSD oficiales; + resto de la suite) |
| Fixtures committeables | ✅ CAF **sintético** propio (RE `76543210-9`) + P12 dummy (`test123`); los activos reales de `secrets/` jamás entran al repo |
| `docker compose build` (backend + frontend `tsc`) | ✅ |
| Review multi-ángulo + correcciones | ✅ (ver arriba) |
| ITs (Testcontainers) migrados al contrato nuevo | ⚠️ compilan; no ejecutables en este host (corren en CI) |
| **E2E certificación — factura 33** (canal clásico maullin): emitir → TED real → firma real → EnvioDTE → upload → QueryEstUp | ✅ **ACEPTADA por el SII** (folio 4, TrackID `0253238320`; el folio 3 quedó REPARO por el hallazgo 6, ya corregido) |
| **E2E certificación — boleta 39** (API REST pangal/apicert): semilla → token → EnvioBOLETA → envío → estado | ✅ **ACEPTADA por el SII** (folio 106, TrackID `30435211`). Los folios 1-4 habían rechazado con **601 "Folio DTE Anulado"** — el CAF 39 original (1-100) estaba superseded en el portal (estado administrativo, no código); con un CAF nuevo timbrado (folios 106-155) y el viejo marcado agotado en la BD, el primer envío fue aceptado sin reparos. |
| **E2E certificación — nota de crédito 61** (canal clásico, referencia CORRIGE_MONTO a la factura folio 4) | ✅ **ACEPTADA por el SII** (folio 1, TrackID `0253261690`) |
| **E2E certificación — nota de débito 56** (canal clásico, referencia ANULA_DOCUMENTO a la NC folio 1) | ✅ **ACEPTADA por el SII** (folio 1, TrackID `0253261842`) |
| **E2E certificación — factura exenta 34** (canal clásico) | ✅ **ACEPTADA por el SII** (folio 2, TrackID `0253261856`). El folio 1 rechazó por el hallazgo 7 (abajo), corregido y validado en el mismo E2E. |

# Pendiente
Ver [ROADMAP.md](ROADMAP.md). Con P0-4/5/6 implementados y el E2E de certificación aceptado en los cinco tipos, el saldo son los **follow-ups documentados** en [SPRINT-6-PLAN.md §7](SPRINT-6-PLAN.md) y del review: certificado y resolución **por empresa** (multi-tenant real), verificación de la FRMA del CAF, **reconciliación por folio antes de reenviar** (cierra el caso timeout-tras-recepción que hoy puede duplicar un envío, ver el límite conocido del Sprint 6), el set de pruebas formal de certificación → autorización de producción (trámite administrativo), y `MedioPago`/`GeoRefEmision`. *Follow-ups de P1-6:* impuesto por defecto en el producto, retención parcial (`IVANoRet`) y habilitar adicionales en boletas (exige el desglose IVA+ILA dentro del bruto y extender el RCOF) — y, para la retención de cambio de sujeto fiel, incorporar el tipo Factura de Compra (45). *Follow-ups de P2-5:* signo de las NC en los totales del libro, semántica de RECHAZADO entre RCOF y libro, y motivo de fallo por documento en el reenvío masivo.
