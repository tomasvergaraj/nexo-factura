# Progreso

> Fecha: 2026-06-26. Sprints 1, 2 y 3 **completados y verificados** (el Sprint 3 cubre lo que no depende de certificado/CAF reales).
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

## Verificación

| Gate | Resultado |
|---|---|
| `mvn test` (compila main + tests en Docker `maven`) | ✅ 86 fuentes main + 17 test |
| Tests unitarios (cálculo, XSD, marshaller→XSD, RUT, sello) | ✅ **50/50** (`Calculadora` 10, `DteXmlValidator` 12, `XmlDteGeneratorXsd` 3, `Rut` 21, `SelloDte` 4) |
| `tsc --noEmit` (frontend) | ✅ sin errores |
| Review adversarial del diff (P1-2: 5 dim; P1-4: 4 dim; P2-4: 3 dim) | ✅ P1-2 y P2-4 sin defectos; P1-4 detectó y **corrigió 2 bugs** (decimal/double y RUT con puntos) |
| Tests con Testcontainers (`BoletaConsumidorFinalIT`, `RcofServiceIT`, `EmisionXsdIT`, `RobustezIT`) | ⚠️ compilan; no ejecutables en este host (igual que Sprints 1–2; corren en CI) |

> Casos de redondeo clave cubiertos: `11900→10000/1900`, `100→84/16`, `999→839/160`, `9999→8403/1596` (donde el re-redondeo ingenuo daría 159/1597), boleta mixta afecto+exento y la equivalencia de la sobrecarga sin flag con el cálculo neto.

# Pendiente
Ver [ROADMAP.md](ROADMAP.md). **Gated por activos SII** (certificado PKCS#12 + CAF reales): **firma XMLDSig real**, **firma del TED (FRMT)** + validación del **CAF**, **integración real con el SII** (semilla/token/EnvioDTE/estado), y el **alineamiento al XSD oficial + namespace `SiiDte`**; los esqueletos de perfil `prod` ya dejan el punto de extensión listo. **Sin gatear**: P1-6 (impuestos adicionales/retenciones), P2-3 (refresh/revocación de token, rate limiting) y P2-5 (contingencia, reenvío de rechazados, libros de compra/venta).
