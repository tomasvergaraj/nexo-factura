# Roadmap de Nexo Factura

> Documento de ingeniería derivado de una auditoría del código (no del README).
> Distingue lo **real** de lo **simulado** y prioriza el trabajo pendiente.
> Última actualización: 2026-07-21.

> **Cómo leer este documento.** Las secciones **1 y 3 son la foto de la auditoría inicial
> (pre-Sprint 1) y se conservan sin cambios** como línea base: es el punto de partida contra
> el que se priorizó el backlog, **no** el estado de hoy. Lo que efectivamente está hecho está
> en la §2 (marcas ✅) y en el registro por sprint de las §§4-9 y 11; el estado verificado vive
> en [PROGRESS.md](PROGRESS.md). Todo lo que la §1 marca en rojo y la §3 lista como riesgo
> ya se cerró — ver §10 para el saldo.

## 1. Estado en la auditoría inicial (pre-Sprint 1 — línea base histórica)

### ✅ Real y funcional (backend)
- **Auth/JWT**: registro BCrypt, login vía `AuthenticationManager`, emisión/validación HMAC-SHA256 con claims `uid/rol/empresaId`, filtro Bearer por request.
- **CRUD de dominio**: Empresa, Cliente, Producto sobre JPA con MapStruct, paginación Spring Data y búsqueda `LIKE`.
- **Concurrencia de folios**: `FolioService` asigna el siguiente folio del CAF con lock pesimista `SELECT…FOR UPDATE` + `@Version` + propagación `MANDATORY`. **Cubierto por test** (`FolioServiceConcurrencyTest`: 50 emisiones concurrentes con Testcontainers).
- **Cálculo tributario**: `CalculadoraImpuestos` (neto/exento/IVA 19% half-up en CLP entero). **Cubierto por test** (`CalculadoraImpuestosTest`).
- **Máquina de estados DTE**: BORRADOR→FIRMADO→ENVIADO→ACEPTADO/RECHAZADO/REPARO/ANULADO con transiciones validadas.
- **XML/TED/PDF**: estructura real con JAXB (subconjunto del esquema SII), bloque `DD` del TED, PDF con OpenPDF.
- **Dashboard, manejo de errores centralizado y esquema Flyway** coherentes.

### 🟡 Simulado (la validez tributaria)
El flujo emitir→firmar→enviar→consultar corre completo en perfil `dev`, pero:
- **Firma XMLDSig** → `FirmaElectronicaStub` inserta un nodo literal; no hay bean de producción.
- **Firma del TED (FRMT)** → `TedGenerator.firmarDd` devuelve un placeholder Base64.
- **SII** → `SiiGatewayStub` da TrackID aleatorio y **siempre ACEPTADO**.
- **PDF417** → se imprime texto, no un código de barras.
- **CAF** → el XML se guarda pero nunca se parsea ni valida.

### 🔵 Frontend: solo mock
- `USE_MOCK = true` **hardcodeado** en `frontend/src/lib/api.ts`: nada golpea el backend.
- `empresaId` hardcodeado a `1`; pantallas Clientes/Productos/Folios/Configuración son `Placeholder`; no hay vista de detalle de DTE; solo emite `FACTURA_AFECTA`.

## 2. Backlog priorizado

### P0 — Bloqueantes
| # | Estado | Funcionalidad | Capa | Sprint |
|---|---|---|---|---|
| P0-1 | ✅ | **Seguridad multi-tenant**: validar `empresaId` del path contra el claim del JWT (cerrar IDOR) + `@PreAuthorize` por rol + cerrar IDOR en `actualizar()` de Cliente/Producto | backend | 1 |
| P0-2 | ✅ | **Cablear frontend a API real**: `VITE_USE_MOCK` (default false), `empresaId` desde el usuario logueado, interceptor 401/403 | frontend | 1 |
| P0-3 | ✅ | **Hardening del secret JWT**: exigir `APP_JWT_SECRET` en prod (fallar arranque si falta) | backend | 1 |
| P0-4 | ✅ | **Firma XMLDSig real** con certificado PKCS#12 (perfil producción, C14N inclusive, **`rsa-sha1`** — el XSD oficial lo fija por schema; el "SHA256withRSA" original era un supuesto erróneo, ver corrección C1 del [plan](SPRINT-6-PLAN.md)) | backend | 6 |
| P0-5 | ✅ | **Firma real del TED (FRMT)** con la clave del CAF + parseo/validación del CAF (el **PDF417 real** ya está hecho, Sprint 2) | backend | 6 |
| P0-6 | ✅ | **Integración SII real** por sus DOS canales: API REST de boleta (39/41, pangal/apicert) y flujo clásico SOAP (33/34/56/61, maullin: semilla→token→EnvioDTE→QueryEstUp) | backend | 6 |

> 🔒 = **gateado por activos externos** (certificado PKCS#12 + CAF reales). Ese gate se abrió al llegar los activos (certificado Acepta + CAF de certificación de boleta 39 y factura 33) y los tres P0 se implementaron en el Sprint 6.

### P1 — Completitud tributaria y producto
- ✅ **P1-1** Notas de crédito/débito (56/61) con referencias obligatorias y anulación del documento referenciado. *(Sprint 2)*
- ✅ **P1-2** Boletas (39/41): monto bruto (IVA incluido) con desglose del neto, receptor "Consumidor final" (cliente opcional) y RCOF diario (reporte + XML `ConsumoFolios` sin firmar). *(Sprint 3)*
- ✅ **P1-3** Validación de dígito verificador (módulo 11) en el backend. *(Sprint 2)*
- ✅ **P1-4** Modelo JAXB completado (bloque `Referencia` en el XML) y **validación XSD pre-firma** contra un esquema representativo (`sii/DTE.xsd`). *(Sprint 3)* El follow-up (alineamiento al XSD oficial + namespace `SiiDte`) se cerró en el **Sprint 6**: XSD oficiales vendoreados, validación post-firma y el esquema representativo eliminado.
- ✅ **P1-5** CRUD real en el front (Clientes/Productos/Folios) + pantalla de detalle de DTE. *(Sprint 2)*
- ✅ **P1-6** Impuestos adicionales (ILA bebidas, suntuarios) y **retención de IVA** (cambio de sujeto), modelados como bloques `ImptoReten` del DTE; catálogo representativo (`TipoImpuesto`), cálculo con agregación por código, validación XSD y solo en documentos de precios netos afectos (33/56/61). *(Sprint 4)*

### P2 — Robustez, calidad y operación
- ✅ **P2-1** `estado-sii`: pasar de GET (con efectos de escritura) a POST idempotente. *(Sprint 1)*
- ✅ **P2-2** Tests: extender a máquina de estados y aislamiento multi-tenant. *(Sprint 1)*
- ✅ **P2-3** Sesión: **refresh tokens** rotatorios con detección de reuso, **revocación** (logout), access token corto (60 min) y **rate limiting** en login/registro (por email e IP → 429). *(Sprint 3)*
- ✅ **P2-4** Inmutabilidad del DTE (campos tributarios congelados con `updatable=false` + **sello de integridad** SHA-256 del XML firmado), manejo de **duplicados → 409** y **`@Version`** (bloqueo optimista) en datos maestros. *(Sprint 3)*. Un log de auditoría completo (quién/cuándo) queda como mejora opcional.
- ✅ **P2-5** Contingencia de envío al SII (estado `EN_CONTINGENCIA` + reintento individual y masivo), **reenvío de rechazados** (mismo XML firmado) y **libros de compra/venta (IECV)** con registro manual de compras. *(Sprint 5)*

## 3. Notas de arquitectura / riesgos (de la auditoría inicial — ver §10 para el saldo actual)
- Toda la integración tributaria crítica está tras `@Profile("!produccion")` **sin contraparte de producción**: activar el perfil `produccion` hoy rompería el contexto.
- **IDOR/multi-tenant sistémico**: el único aislamiento es el filtro por `empresaId` en queries; el path no se valida contra el JWT.
- Frontend desacoplado de la realidad por un flag global hardcodeado.
- Consistencia de encoding/canonicalización del XML sin resolver (bloquea firma real).

## 4. Alcance del Sprint 1 (este entregable)
**Objetivo: sistema seguro, real y verificable de extremo a extremo, sin depender de certificados/CAF externos.**
- P0-1 Seguridad multi-tenant + roles.
- P0-2 Frontend cableado a la API real (mock como opt-in).
- P0-3 Hardening del secret JWT.
- P2-1 `estado-sii` idempotente.
- P2-2 Tests de máquina de estados y aislamiento tenant.
- Documentación del progreso y de los esqueletos de perfil producción para el Sprint 2.

El Sprint 2 (P0-4/5/6) queda **diseñado y documentado**; requiere un certificado PKCS#12 y un CAF reales para implementarse y verificarse.

> **Nota posterior.** Ese plan no se cumplió: los activos no llegaron, así que el Sprint 2 real fue P1-1/P1-3/P1-5 + PDF417 + perfil `prod` (§5) y los P0-4/5/6 siguen gateados. Los sprints 3-5 aplicaron el mismo criterio — avanzar solo en lo verificable sin activos externos.

## 5. Hecho en el Sprint 2

Completado y verificado (ver [PROGRESS.md](PROGRESS.md)): **P1-1** (notas de crédito/débito con anulación), **P1-3** (módulo 11), **P1-5** (frontend completo: CRUD + detalle de DTE + notas), el **timbre PDF417 real** (parte de P0-5) y el **cierre del riesgo de arquitectura** con los esqueletos de perfil `prod` (firma/SII fallan fail-fast en vez de faltar).

## 6. Hecho en el Sprint 3 (sin activos SII)

La integración tributaria real (P0-4/5/6: firma XMLDSig con PKCS#12, FRMT + CAF real, SII real) sigue **gateada por un certificado y un CAF reales** que aún no están disponibles. Mientras tanto se completaron, verificables sin esos activos (ver [PROGRESS.md](PROGRESS.md)):
- **P1-2** — **boletas 39/41** con precio bruto (IVA incluido) y desglose del neto, **receptor "Consumidor final"** (cliente opcional, solo en boletas) y el **RCOF** (Reporte de Consumo de Folios) diario con su endpoint y XML `ConsumoFolios` (sin firmar/enviar).
- **P1-4** — **bloque `Referencia`** agregado al XML del DTE (antes las notas 56/61 no lo emitían) y **validación XSD pre-firma** (`DteXmlValidator`) contra un esquema representativo *(reemplazado en el Sprint 6 por los XSD oficiales, con validación post-firma)*; una emisión cuyo XML no cumple el esquema falla con **422** y revierte el folio.
- **P2-4** — **inmutabilidad del DTE** (`updatable=false` en los campos tributarios + **sello de integridad** SHA-256 fijado al emitir), **duplicados → 409** (`DataIntegrityViolationException`) y **`@Version`** en Empresa/Cliente/Producto (conflicto → 409). Migración `V3`.
- **P2-3** — **sesión y seguridad**: refresh tokens opacos (solo el hash SHA-256 se guarda) rotados en cada `/refresh` con detección de reuso (revoca toda la cadena), `/logout` revoca, access token corto (60 min) y **rate limiting** en memoria por email + IP (login y registro → 429 con `Retry-After`). Frontend con auto-refresh transparente. Migración `V4`.

Pendiente para cuando lleguen los activos: P0-4/5/6 (y el alineamiento al XSD oficial + namespace `SiiDte`). Sin gatear: P2-5 (contingencia, reenvío de rechazados, libros de compra/venta).

## 7. Hecho en el Sprint 4 (sin activos SII)

Completado y verificado (ver [PROGRESS.md](PROGRESS.md)):
- **P1-6** — **impuestos adicionales y retenciones**. Catálogo representativo (`TipoImpuesto`: ILA de bebidas alcohólicas/analcohólicas, azucaradas, suntuarios y la retención de IVA por cambio de sujeto), cálculo con base agregada por código y redondeo half-up único por código, total = neto + exento + IVA + Σ(adicionales) − Σ(retenido), emisión en el XML como bloques `ImptoReten` (después de `IVA`, antes de `MntTotal`) y `CodImpAdic` en el detalle (antes de `MontoItem`), validados contra el XSD pre-firma. Solo en documentos de precios netos afectos (33/56/61); boletas/exentos/código desconocido → 409. Migración `V5` aditiva. La verificación de fidelidad SII del workflow de diseño corrigió tres errores antes de implementar (no existe `IVARetTotal` en el DTE; `CodImpAdic` precede a `MontoItem`; códigos/tasas del catálogo).

Follow-ups de P1-6: impuesto por defecto en el producto, retención parcial (`IVANoRet`) y adicionales en boletas (requiere el desglose IVA+ILA dentro del bruto y extender el RCOF); la retención de cambio de sujeto fiel requiere incorporar el tipo Factura de Compra (45).

## 8. Hecho en el Sprint 5 (P2-5, sin activos SII)

Completado y verificado (ver [PROGRESS.md](PROGRESS.md)):
- **Contingencia de envío**: nuevo estado `EN_CONTINGENCIA` — si el SII no está disponible al enviar, el DTE queda en cola con traza (`intentosEnvio`/`ultimoEnvioEn`/`ultimoErrorEnvio`) en vez de fallar; reintento individual (`POST /{id}/reenviar`) y masivo (`POST /reenviar-pendientes`, una transacción POR documento para no revertir TrackIDs ya aceptados). Stub del SII configurable en runtime (`PUT /api/dev/sii-stub`, solo ADMIN, perfil ≠ prod) para simular caída/rechazo E2E.
- **Reenvío de rechazados**: `RECHAZADO → ENVIADO` con el mismo XML firmado (DTE inmutable, folio consumido); se eliminó `RECHAZADO → BORRADOR`. Un rechazo es de fondo: el documento NO entra a la cola de contingencia aunque el reenvío falle.
- **Libros de compra/venta (IECV)**: libro de ventas desde los DTE emitidos del período (boletas solo resumidas, anulados marcados sin sumar, rechazados excluidos; proyección sin `xml_dte`); libro de compras desde el registro manual de documentos recibidos (`documento_compra`, CRUD con unicidad y coherencia `total = neto + exento + IVA − IVA retenido`, retención del 46 soportada). JSON + XML `LibroCompraVenta` representativo sin firmar. Migración `V6`.

Follow-ups de P2-5: signo de las notas de crédito en los totales agregados del libro (hoy positivas, como las filas del IECV), unificar la semántica de RECHAZADO entre RCOF (cuenta el folio y su monto) y libro (lo excluye), y exponer el motivo de fallo por documento en la respuesta del reenvío masivo.

## 9. Hecho tras el Sprint 5: sitio público y Configuración del emisor

Commit `e1e834f`, solo frontend (ver [PROGRESS.md](PROGRESS.md)). Cierra los **callejones sin salida de la navegación**, que eran el último resto visible del estado descrito en la §1:
- **Sitio público**: páginas Sobre, Contacto, Términos, Privacidad y **Estado del servicio** (consulta `/actuator/health` en vivo, sin interceptor de auth); layout compartido `SitePage`; footer y nav cableados a rutas que ahora existen, con navegación SPA a las anclas de la Landing.
- **Configuración del emisor**: `/app/configuracion` pasa de `Placeholder` a pantalla real sobre `GET`/`PUT /api/empresas/{id}`, con validación de RUT (módulo 11) y **modo lectura para el rol `EMISOR`** (espejo en la UI del `@PreAuthorize` de ADMIN). Con esto **desaparece el último `Placeholder`** de la aplicación.
- **Infra del frontend**: proxy de Vite al `8082` del host (donde está mapeado el backend en Docker) y `location = /actuator/health` en nginx — match exacto, el resto de actuator no se expone.

## 10. Saldo actual de los riesgos de la §3

| Riesgo de la auditoría | Estado |
|---|---|
| Integración tributaria tras `@Profile` sin contraparte de producción (el perfil rompía el contexto) | ✅ **Cerrado** en el Sprint 2: perfil estandarizado a `prod` con beans `FirmaElectronicaProd`/`SiiGatewayProd` que fallan fail-fast; el contexto levanta. |
| IDOR/multi-tenant sistémico (el path no se validaba contra el JWT) | ✅ **Cerrado** en el Sprint 1: `TenantGuard` + `@PreAuthorize` en los controllers scoped, y scope por fila en `actualizar()`. |
| Frontend desacoplado por un flag global hardcodeado | ✅ **Cerrado** en el Sprint 1 (`VITE_USE_MOCK`, default `false`) y completado en el Sprint 2 y en la §9: ya no queda ninguna pantalla mock ni `Placeholder`. |
| Encoding/canonicalización del XML sin resolver | ✅ **Cerrado** en el Sprint 6: C14N inclusive (la que fija el XSD oficial de la firma), DTE marshallado **sin indentación** (una línea — elimina la deriva byte-a-byte del TED y de la firma), prólogo ISO-8859-1 coherente extremo a extremo y TED como string aplanado de fuente única. |

**Saldo**: los cuatro riesgos de la §3 están cerrados y el backlog priorizado (P0/P1/P2) está **completo**. Lo que queda son los follow-ups documentados del §11 y de [SPRINT-6-PLAN.md §7](SPRINT-6-PLAN.md).

## 11. Hecho en el Sprint 6 (P0-4/5/6: integración tributaria real)

Con el certificado PKCS#12 y dos CAF de certificación reales disponibles, se implementó todo lo gateado (ver [PROGRESS.md](PROGRESS.md) y el diseño en [SPRINT-6-PLAN.md](SPRINT-6-PLAN.md)):
- **P0-5** — `CafParser` (DER PKCS#1 propio, coherencia de claves, `<CAF>` verbatim), alta de CAF **por XML**, `TedGenerator` real (DD aplanado según la regla oficial, **FRMT `SHA1withRSA`** verificado contra la clave pública del CAF) y PDF que extrae el TED del XML almacenado.
- **P0-4** — `CertificadoDigital` + `FirmaElectronicaProd` (XMLDSig del JDK con los algoritmos que **fija** el XSD oficial: C14N inclusive, `rsa-sha1`, digest `sha1`), namespace `SiiDte` en todo el paquete, rama boleta del generador (su schema es distinto), XSD oficiales vendoreados como única validación (**post-firma**, revirtiendo folio con 422).
- **P0-6** — `SiiGatewayProd` ruteando por tipo a dos transportes con token independiente: **boleta 39/41 por la API REST** (semilla/token/envío multipart/estado; pangal=cert, rahue=prod) y **facturas/notas 33/34/56/61 por el canal clásico** (SOAP `CrSeed`/`GetTokenFromSeed`, upload `DTEUpload`, estado `QueryEstUp` en maullin/palena). Errores de transporte → contingencia (Sprint 5 intacto); rechazo de negocio → error duro; token inválido → renovar y reintentar una vez.
- **Operación**: `docker-compose.cert.yml` (perfil `prod` + ambiente `CERTIFICACION`), carga de CAF por XML en el frontend, config `app.sii.*` (FchResol/NroResol/user-agent). Suite en **231 unitarios** (todos los generadores validados contra los XSD oficiales).
- **E2E contra el SII de certificación**: **factura 33 ACEPTADA** (TrackID real de maullin) y **boleta 39 ACEPTADA** (API REST pangal, folio 106, TrackID 30435211; los primeros folios rechazaban con 601 porque el CAF original estaba superseded — se cerró timbrando un CAF nuevo). El E2E cazó además **6 bugs invisibles para la suite** (detalle en [PROGRESS.md](PROGRESS.md)).
