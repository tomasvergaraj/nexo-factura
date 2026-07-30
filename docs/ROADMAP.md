# Roadmap de Nexo Factura

> Documento de ingeniería derivado de una auditoría del código (no del README).
> Distingue lo **real** de lo **simulado** y prioriza el trabajo pendiente.
> Última actualización: 2026-07-29.

> **Cómo leer este documento.** Las secciones **1 y 3 son la foto de la auditoría inicial
> (pre-Sprint 1) y se conservan sin cambios** como línea base: es el punto de partida contra
> el que se priorizó el backlog, **no** el estado de hoy. Lo que efectivamente está hecho está
> en la §2 (marcas ✅) y en el registro por sprint de las §§4-9, 11 y 12; el estado verificado vive
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

**Saldo**: los cuatro riesgos de la §3 están cerrados y el backlog priorizado (P0/P1/P2) está **completo**. Con el Sprint 7 (§12) el sistema además **opera en producción ante el SII**, y la infraestructura de tests y CI (§13) y el factor de proporcionalidad (§14) quedaron cerrados en el Sprint 8.

La §15 —el RCOF sin firmar— **dejó de ser una brecha crítica**: el SII eliminó su envío en 2022 y lo que queda es material de certificación de boletas, que todavía no se inicia. El saldo real son los follow-ups documentados del §11, del §12 y de [SPRINT-6-PLAN.md §7](SPRINT-6-PLAN.md).

## 11. Hecho en el Sprint 6 (P0-4/5/6: integración tributaria real)

Con el certificado PKCS#12 y dos CAF de certificación reales disponibles, se implementó todo lo gateado (ver [PROGRESS.md](PROGRESS.md) y el diseño en [SPRINT-6-PLAN.md](SPRINT-6-PLAN.md)):
- **P0-5** — `CafParser` (DER PKCS#1 propio, coherencia de claves, `<CAF>` verbatim), alta de CAF **por XML**, `TedGenerator` real (DD aplanado según la regla oficial, **FRMT `SHA1withRSA`** verificado contra la clave pública del CAF) y PDF que extrae el TED del XML almacenado.
- **P0-4** — `CertificadoDigital` + `FirmaElectronicaProd` (XMLDSig del JDK con los algoritmos que **fija** el XSD oficial: C14N inclusive, `rsa-sha1`, digest `sha1`), namespace `SiiDte` en todo el paquete, rama boleta del generador (su schema es distinto), XSD oficiales vendoreados como única validación (**post-firma**, revirtiendo folio con 422).
- **P0-6** — `SiiGatewayProd` ruteando por tipo a dos transportes con token independiente: **boleta 39/41 por la API REST** (semilla/token/envío multipart/estado; pangal=cert, rahue=prod) y **facturas/notas 33/34/56/61 por el canal clásico** (SOAP `CrSeed`/`GetTokenFromSeed`, upload `DTEUpload`, estado `QueryEstUp` en maullin/palena). Errores de transporte → contingencia (Sprint 5 intacto); rechazo de negocio → error duro; token inválido → renovar y reintentar una vez.
- **Operación**: `docker-compose.cert.yml` (perfil `prod` + ambiente `CERTIFICACION`), carga de CAF por XML en el frontend, config `app.sii.*` (FchResol/NroResol/user-agent). Suite en **231 unitarios** (todos los generadores validados contra los XSD oficiales).
- **E2E contra el SII de certificación — los cinco tipos ACEPTADOS**: **factura 33** (canal clásico maullin), **boleta 39** (API REST pangal, folio 106; los primeros folios rechazaban con 601 porque el CAF original estaba superseded — se cerró timbrando un CAF nuevo), **nota de crédito 61**, **nota de débito 56** (anulando la NC) y **factura exenta 34**. El E2E cazó además **8 bugs invisibles para la suite** (los dos últimos: exenta que declaraba IVA en Totales y conexión cortada leyendo la respuesta que salía como 500 en vez de contingencia; detalle en [PROGRESS.md](PROGRESS.md)).

## 12. Hecho en el Sprint 7 (multi-tenant real y salida a producción)

El sistema pasa de **un emisor con activos de ambiente** a plataforma **multi-empresa**, y de certificación a **producción ante el SII** (ver [PROGRESS.md](PROGRESS.md)):

- **Certificado y resolución por empresa** — `app.sii.firma-modo` (`GLOBAL` | `POR_EMPRESA`) como *property*, no perfil (certificación corre perfil `prod` y no podría distinguirse). `CertificadoDigital` se parte en `CertificadoFirma` + `CertificadoResolver`; PKCS#12 y clave **cifrados AES-256-GCM** en `certificado_empresa` (V13), token del SII cacheado **por huella del certificado**. `FchResol`/`NroResol` bajan de config a `Empresa` con `ResolucionResolver`.
- **Cifrado en reposo del XML del CAF** (V14) — el CAF trae la clave privada del timbre: en claro, un volcado de la tabla bastaba para emitir DTE a nombre del contribuyente. `AttributeConverter` sobre la misma columna con formato `enc:v1:` y backfill por JDBC crudo al arrancar.
- **Producción (palena)** — `docker-compose.prod.yml` en paralelo al de certificación, con DB aislada; [RUNBOOK-produccion.md](../RUNBOOK-produccion.md). **Fix de seguridad**: el seed de demostración (`V2__seed_dev.sql`) corría en producción; movido a `db/seed-dev/`, solo perfil `dev`.
- **Libros IECV completos** — firma, validación y **envío al SII** desde la UI con registro del TrackID (V15), más un job diario que **prepara** el libro del mes anterior y avisa de los pendientes (V16).
- **Gate de cierre**: dry-run `POR_EMPRESA` contra maullín **ACEPTADO** (TrackID `0253303236`) y **emisión de humo en producción verificada**.

Follow-ups del Sprint 7: el **factor de proporcionalidad del IVA de uso común** ya está resuelto (§14); queda la consulta automática del estado de los envíos de libro (hoy es manual por TrackID).

## 13. Infraestructura de tests y CI (hecha)

Detalle en [PLAN-CONTINUIDAD.md](PLAN-CONTINUIDAD.md). En una frase: los ITs que todos los sprints daban por «corren en CI» **nunca se ejecutaron en ninguna parte** (faltaba `maven-failsafe-plugin`), y al hacerlos correr aparecieron cuatro defectos reales de infraestructura de test más uno de código de producción (`SiiStubController` acoplado a una clase concreta).

Cerrado: **la suite completa en verde**, y un workflow de GitHub Actions que la ejecuta en cada push y cada PR — **validado con un push real**, no supuesto. Queda como límite conocido que el `mvn verify` local exige el montaje del socket de Docker y `TESTCONTAINERS_HOST_OVERRIDE`, porque Maven corre en contenedor; en CI no hace falta.

## 14. Factor de proporcionalidad del IVA de uso común (hecha)

Detalle en [PLAN-CONTINUIDAD.md](PLAN-CONTINUIDAD.md) §Fase 2. El libro de compras con IVA de uso común exige `FctProp` y nadie lo informaba: el job pasaba `null` y **la UI ni siquiera ofrecía dónde ponerlo**, así que esos períodos quedaban en `ERROR` permanente y no se podían enviar en absoluto desde la aplicación.

Se resolvió con un factor **por empresa** (`V17`, editable en Configuración) que actúa de default, dejando intacto el override por período de la API. La decisión de no calcularlo automáticamente desde las ventas es deliberada: la fórmula legal necesita el acumulado del año completo y el sistema sólo conoce los DTE que emitió él, así que un cálculo automático sería *equivocado con más confianza* dentro de una declaración tributaria. En su lugar se ofrece un **factor sugerido** junto al campo, acompañado de cuántos documentos lo respaldan y desde qué fecha, para que quien decide pueda juzgar si el acumulado está completo. Cada envío guarda además el factor que declaró, porque el de la empresa es editable.

## 15. El RCOF: la brecha que no era (hecha — y con dos premisas falsas de por medio)

*Detectado el 2026-07-29, fuera de toda fase.* Hoy [`RcofController`](../backend/src/main/java/cl/nexosoftware/factura/rcof/RcofController.java) sólo expone dos GET —el reporte en JSON y el XML **sin firmar**— y `SiiGateway` no tiene ningún método para el RCOF: tiene `enviar`, `enviarLibro` y `enviarLote`.

No es un descuido oculto: este documento siempre dijo «sin firmar/enviar» (§P1-2). Lo que cambió es el **motivo**. El código justificaba el diferimiento en que *«requiere certificado real, igual que la firma del DTE»*, y ese bloqueo desapareció en el Sprint 7: hay certificado y resolución por empresa. La razón documentada ya no se sostiene.

> **Corrección del 2026-07-29 (misma fecha, más tarde).** Este apartado nació afirmando que «el sistema emite boletas electrónicas en producción» y que por eso había una **obligación incumplida**. Eso era **falso**, y lo detectó el usuario. Verificado: producción tiene cuatro CAF —33, 34, 61 y 56, [RUNBOOK §5](../RUNBOOK-produccion.md)— y **ninguno de boleta**; la BD de producción contiene exactamente dos documentos (la factura 33 de humo, anulada, y su nota de crédito); y la emisión de humo del [RUNBOOK §6](../RUNBOOK-produccion.md) fue una factura 33. **Nunca se emitió una boleta en producción.** La boleta 39 aceptada (folio 106, TrackID `30435211`) fue en **certificación**, por pangal/apicert. El origen del error está en [README.md](../README.md), que enumera en una sola frase los cinco tipos aceptados *en certificación* y la emisión de humo *en producción*: dos hechos distintos que se leyeron como uno.
>
> Lo que cambia: el RCOF **no** es una obligación que se esté incumpliendo.
>
> **Segunda corrección, del mismo día — y ésta anula la premisa que quedaba.** Al ir a buscar el endpoint del envío apareció que **el SII eliminó la obligación de enviar el RCOF**. La [Resolución Ex. SII N°53 de 2022](https://www.sii.cl/noticias/2022/160622noti01rp.htm) suprimió el envío del *Resumen de Ventas Diarias* (nombre nuevo del ex Reporte de Consumo de Folios) **a partir del 1 de agosto de 2022**, para usuarios del sistema del SII y de otros softwares por igual; desde entonces el registro de ventas del contribuyente se alimenta del XML de las boletas que llegan al Servicio. Lo confirman el [comunicado del SII](https://www.sii.cl/noticias/2022/040822noti01rp.htm) y su [pregunta frecuente](https://www.sii.cl/preguntas_frecuentes/factura_electronica/001_003_6272.htm). Sólo sobreviven los períodos **anteriores al 31/07/2022** que hubieran quedado sin enviar — no es el caso: acá no se emitieron boletas nunca.
>
> Queda entonces **un solo uso real: la certificación**. La [guía de certificación de boletas del SII](https://www.sii.cl/factura_electronica/guia_emitir_boleta_servicio.htm) sigue publicando el formato *Consumo de Folios* junto al de boletas y libro de boletas, y exige **5 envíos a la casilla `SII_BE_Certificacion@sii.cl`** — por **correo**, no por upload. La guía no enumera cuáles son los 5, así que *cuál exactamente y en qué formato se sabe al solicitar el set de pruebas*. Los instructivos de terceros que describen «enviar el RCOF por upload y luego diariamente» son **anteriores a la Res. 53/2022**.

**Hecho el 2026-07-29**, con el objetivo corregido: el destinatario no es un endpoint sino un **archivo firmado que se adjunta al correo de certificación**.

- **XSD oficial vendoreado.** `ConsumoFolio_v10.xsd` (de [sii.cl](https://www.sii.cl/factura_electronica/factura_mercado/ConsumoFolio_v10.xsd)) entra a `resources/sii/oficial/`, y con él el RCOF deja de ser el **único XML tributario del sistema sin esquema que lo respaldara**. Al validarlo apareció que el modelo no estaba incompleto sino **estructuralmente mal**: `Caratula` y `Resumen` colgaban de la raíz, sin el envoltorio `DocumentoConsumoFolios` ni su atributo `ID`; faltaban `RutEnvia`, `FchResol`, `NroResol` y `TmstFirmaEnv`; y la `Signature`, que el esquema exige, no existía. Ese XML no lo habría aceptado nadie.
- **Firma y descarga.** [`RcofFirmaService`](../backend/src/main/java/cl/nexosoftware/factura/rcof/RcofFirmaService.java): construir → firmar enveloped con el certificado de la empresa (Reference al `ID`) → validar contra el XSD → registrar. `POST /api/empresas/{id}/rcof/xml-firmado` y botón en la pantalla RCOF. Un día sin boletas se rechaza: el esquema exige al menos un `Resumen`, y no hay consumo que declarar.
- **Secuencia real.** `SEC_ENVIO_PLACEHOLDER` eliminado. Sale de `rcof_firmado` (`V18`): 1 la primera vez del día, +1 al rehacerlo, con override para regenerar un número ya usado. La tabla registra **generaciones**, no envíos — el sistema no puede saber si el correo salió, y no lo afirma.
- **Canal de envío: deliberadamente NO se construyó.** La obligación que lo justificaba no existe y el upload puede haberse retirado con la Res. 53/2022; sería código que nadie puede probar. Sin upload tampoco hay TrackID que seguir.

Verificado local: suite completa en verde, incluidos 4 tests de esquema (uno prueba que **sin firma el XML no valida**) y 6 ITs de secuencia y rechazos.

**Decidido con el usuario (2026-07-29):** disparo **manual**, **sin job automático**; nada que regularizar hacia atrás. La pregunta de la periodicidad **quedó respondida por la Res. 53/2022: no hay periodicidad, no se envía**.

**Lo que queda abierto, y no se decidió sin el usuario:**
- **El tipo 61 en el consumo de folios.** El XSD acepta `39`, `41` y **`61`** (notas de crédito electrónicas); hoy sólo se reportan 39 y 41. El set de pruebas recibido no tiene ningún caso de NC, así que no bloquea; incluirlas o no cambia lo que se declara y es semántica tributaria que decide el usuario.

## 16. Certificación de boletas: lo que pide el set real (parcialmente hecha)

*El usuario aportó el set el 2026-07-29 (`secrets/set_certificacion/boletas/`), y eso cerró la pregunta abierta de la §15 — y corrigió una conclusión de ahí.*

**Corrección a la §15:** ahí se dijo que no correspondía construir canal de envío. Vale para producción, **no para certificación**: el correo del SII instruye «enviar al SII el Set de Boletas generado y el **Reporte de Consumo de Folios (RCOF)** asociado, utilizando para ello la opción de **UPLOAD, Web o automatizado**, en ambiente certificación». El RCOF sí se sube. El archivo firmado que ya se descarga cubre la opción *Web*; automatizar la subida sigue sin hacerse, y es opcional.

El set son **5 casos** (5 folios) y exige, además de las boletas: referenciar el caso en cada XML, un solo sobre, y un plazo de **24 horas desde que se bajan los folios** — el correo dice que el plazo existe «puesto que se pretende verificar la capacidad de generación del RCOF».

**Hecho:**
- **Referencia al caso en la boleta.** El set la pide en su propia forma —`<CodRef>SET</CodRef>`, `<RazonRef>CASO-1</RazonRef>`—, distinta de la del canal clásico (`TpoDocRef=SET` + `FolioRef`): en boleta `CodRef` es texto y **no existe `FchRef`**. `ModeloBoleta` no tenía bloque `Referencia` **en absoluto**, así que `setCaso` —que sí funcionaba en facturas desde el Sprint 6— **se ignoraba en silencio en las boletas**. `setCaso` ahora admite `N` además de `<atencion>-<caso>`, porque el set de boletas numera 1..N sin atención.
- **Las 5 boletas en un solo sobre.** «El envío del Set de Boletas debe ser en solo un archivo (sobre)». El armado multi-documento ya existía y era común a los dos canales (`EnvioGenerator.generarLote`), pero el canal de boleta no lo exponía: `SiiTransporteBoleta` no sobreescribía `enviarLote` y el default lanzaba `UnsupportedOperationException`. Se usa por `POST /api/empresas/{id}/documentos/enviar-lote` (vía Swagger, como el libro ESPECIAL).
- **Guarda de lote mixto.** Al habilitar el segundo canal, un lote que mezcle boletas con facturas se iría por el canal del primer documento. Se rechaza antes de enviar.

- **Libro de Boletas Electrónicas.** ~~Aplica: el instructivo lo pide sin condición~~ — **corrección del 2026-07-30, leyendo por fin el correo fuente completo: este trámite NO lo pide.** El correo del SII (`certificacion_boletas_dte@sii.cl`, 2026-07-29) enumera **cuatro pasos** —CAF de 5 folios, generar el set, enviar set + RCOF, y **solicitar la revisión informando el Track ID en el apartado de Boletas electrónicas del sitio web**— y ni menciona el Libro, ni las muestras en PDF, ni la casilla `SII_BE_Certificacion@sii.cl`. Todo eso venía del **instructivo web genérico**, no del trámite concreto. La conclusión anterior («aplica sin condición, no hacía falta preguntarlo») leía el instructivo como si fuera el trámite: el error contra el que ya advertía la regla de pedir el documento fuente. Consecuencia práctica: no existe «número de atención» para este set —los casos van numerados 1..5 a secas— y el `FolioNotificacion` obligatorio del esquema no tiene qué responder, lo que cuadra con que el Libro sólo existe como respuesta a una notificación del SII que aquí nunca hubo. La capacidad queda implementada e íntegra por si una revisión futura la exige: XSD oficial `LibroBOLETA_v10.xsd` (del `schema_libro_bol.zip` del SII), [`LibroBoletaXmlGenerator`](../backend/src/main/java/cl/nexosoftware/factura/tributario/LibroBoletaXmlGenerator.java) + [`LibroBoletaService`](../backend/src/main/java/cl/nexosoftware/factura/libro/LibroBoletaService.java), `GET /api/empresas/{id}/libro-boletas/xml-firmado?periodo=&folioNotificacion=`.

  Tres cosas que el esquema impone y no eran evidentes: **`TipoLibro` sólo admite `ESPECIAL`** y **`FolioNotificacion` es obligatorio** (este libro existe únicamente como respuesta a una notificación del SII — en el set, el número de atención); **`TotMntNeto` y `TotMntIVA` son obligatorios en el resumen del período** y no existen en el del segmento; y **`TotDoc` es `positiveInteger`**, así que un tipo sin boletas no puede informarse en cero.

  **Y un defecto del propio XSD**, que quedó documentado en el validador: a diferencia de `LibroCV_v10`, que importa `xmldsignature_v10.xsd` y usa `ref="ds:Signature"`, `LibroBOLETA_v10` declara **su propio** `Signature` en el namespace `SiiDte`. Una firma XMLDSig real vive obligatoriamente en el namespace de XMLDSig, así que el documento correcto nunca cumpliría ese esquema al pie de la letra. Se valida una copia con la declaración de namespace de la firma quitada —hereda el default `SiiDte`, que es lo que el XSD espera— y **lo que se firma y se entrega conserva el namespace correcto**.

**Ejecutado el 2026-07-30 (dentro del plazo de 24 h, que vencía ~19:24):**
- **Set enviado y ACEPTADO**: 5 boletas, folios 156-160, un solo sobre — TrackID `30500869`, las 5 consultadas ACEPTADO por API. La BD de dev/cert resultó recreada (volumen del 24-07), así que la empresa del E2E se volvió a crear como **id 2**.
- **RCOF subido y validado**: número de envío `0253507092`, estado **REPARO con 0 errores** y un único reparo —cod 250, «Envío de RVD no es obligatorio desde agosto 2022»— que es la Res. 53/2022 de la §15 dicha por el propio validador: informativo, no un defecto. El primer intento rebotó con `SCH-00001: Invalid Schema Name` porque el RCOF salía **sin `xsi:schemaLocation`** — la misma piedra del primer IECV, corregida en `RcofXmlGenerator` (y preventivamente en `LibroBoletaXmlGenerator`).
- Salida completa en `secrets/set_certificacion/boletas/salida-2026-07-30/`.

**Falta:**
- **Solicitar la revisión del set** informando el TrackID `30500869` — paso 4 del correo, manual, en el apartado de **Boletas electrónicas de ventas y servicios** del sitio del SII (menú propio, distinto del de Factura electrónica). Tras el V°B° sigue la **Declaración de Cumplimiento** (paso 5); si hay reparos, se corrige y se repite.
- **El sitio web público de consulta de la boleta** — el software ya lo trae (2026-07-30); falta **desplegarlo en internet**, que es infraestructura, no código. Lo que exige el Formato de Boletas Electrónicas del SII (v2.0, pág. 5, verificado contra el PDF oficial): las boletas consultables por los clientes durante **tres meses** desde la emisión, y la URL **impresa bajo el timbre** como «Verifique documento: <url>». Implementado: `GET /api/public/boletas/pdf` (sin autenticación, entrega el PDF solo con la coincidencia **exacta** de los cinco datos impresos —RUT emisor, tipo, folio, fecha, monto—, 404 uniforme que no revela cuál campo falló, rate-limit por IP reutilizando el de login, ventana de 3 meses), página pública `/consulta-boleta` en el frontend, campo `urlConsultaBoleta` por empresa (V19, editable en Configuración) que actúa de interruptor: sin URL configurada no se expone nada y el PDF conserva la leyenda genérica `www.sii.cl`; con URL, la leyenda la imprime y la consulta se habilita. Verificado contra la boleta real del set (folio 156): PDF con la leyenda correcta y 404 con monto equivocado. **Pendiente (decisión del usuario): dónde hospedarlo** — producción corre en la máquina local y el sitio debe ser accesible públicamente bajo el dominio del emisor; recién entonces se configura la URL real y se regeneran los PDF.
- **Subida automatizada** del set y del RCOF (opción *automatizado*). Se resolvió con el canal API para el sobre y el UPLOAD web para el RCOF.
- ~~Las 10 muestras en PDF~~ — eran del envío 5 del **instructivo web**; el correo del trámite no las pide. Si la revisión las exigiera, son 10 emisiones reales en otro día que el set (el RCOF agrega por día).

**Decisión semántica que conviene confirmar:** en el libro, un folio cuyo documento no es válido (anulado o rechazado por el SII) va con `Anulado=A`, **se cuenta** en `TotDoc` y en `TotAnulado`, y **no suma montos**. Se eligió así para que el libro y el RCOF del mismo período no se contradigan —`TotDoc` calza con FoliosEmitidos y `TotAnulado` con FoliosAnulados—, y la regla vive en un solo lugar (`EstadoDte.folioSinDocumentoValido`). El set de pruebas no tiene anulados, así que no afecta la certificación.
