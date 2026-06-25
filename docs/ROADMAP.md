# Roadmap de Nexo Factura

> Documento de ingeniería derivado de una auditoría del código (no del README).
> Distingue lo **real** de lo **simulado** y prioriza el trabajo pendiente.
> Última actualización: 2026-06-25.

## 1. Estado actual del sistema

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
| # | Funcionalidad | Capa | Sprint |
|---|---|---|---|
| P0-1 | **Seguridad multi-tenant**: validar `empresaId` del path contra el claim del JWT (cerrar IDOR) + `@PreAuthorize` por rol + cerrar IDOR en `actualizar()` de Cliente/Producto | backend | **1** |
| P0-2 | **Cablear frontend a API real**: `VITE_USE_MOCK` (default false), `empresaId` desde el usuario logueado, interceptor 401/403 | frontend | **1** |
| P0-3 | **Hardening del secret JWT**: exigir `APP_JWT_SECRET` en prod (fallar arranque si falta) | backend | **1** |
| P0-4 | **Firma XMLDSig real** con certificado PKCS#12 (perfil producción, C14N, SHA256withRSA) | backend | 2 |
| P0-5 | **Firma real del TED (FRMT)** + parseo/validación del CAF + **PDF417 real** | backend | 2 |
| P0-6 | **Integración SII real**: semilla→token→EnvioDTE→consulta por TrackID | backend | 2 |

### P1 — Completitud tributaria y producto
- ✅ **P1-1** Notas de crédito/débito (56/61) con referencias obligatorias y anulación del documento referenciado. *(Sprint 2)*
- P1-2 Boletas (39/41): monto bruto (IVA incluido) y RCOF diario.
- ✅ **P1-3** Validación de dígito verificador (módulo 11) en el backend. *(Sprint 2)*
- P1-4 Completar el modelo JAXB al XSD oficial y validar antes de firmar.
- ✅ **P1-5** CRUD real en el front (Clientes/Productos/Folios) + pantalla de detalle de DTE. *(Sprint 2)*
- P1-6 Impuestos adicionales y retenciones.

### P2 — Robustez, calidad y operación
- P2-1 `estado-sii`: pasar de GET (con efectos de escritura) a POST idempotente. **Sprint 1.**
- P2-2 Tests: extender a máquina de estados y aislamiento multi-tenant. **Sprint 1.**
- P2-3 Sesión: refresh/revocación de token, rate limiting en login.
- P2-4 Auditoría/inmutabilidad del DTE, manejo de duplicados → 409, `@Version` en datos maestros.
- P2-5 Contingencia, reenvío de rechazados, libros de compra/venta.

## 3. Notas de arquitectura / riesgos
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

## 5. Hecho en el Sprint 2

Completado y verificado (ver [PROGRESS.md](PROGRESS.md)): **P1-1** (notas de crédito/débito con anulación), **P1-3** (módulo 11), **P1-5** (frontend completo: CRUD + detalle de DTE + notas), el **timbre PDF417 real** (parte de P0-5) y el **cierre del riesgo de arquitectura** con los esqueletos de perfil `prod` (firma/SII fallan fail-fast en vez de faltar). Queda pendiente para el **Sprint 3** la integración tributaria real (firma XMLDSig con PKCS#12, FRMT + CAF real, SII real), gateada por activos externos.
