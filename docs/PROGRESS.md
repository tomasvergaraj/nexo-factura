# Progreso — Sprint 1

> Fecha: 2026-06-25. Estado: **completado y verificado**.
> Ver el plan en [SPRINT-1-PLAN.md](SPRINT-1-PLAN.md) y el backlog en [ROADMAP.md](ROADMAP.md).

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

## Pendiente (Sprint 2)
Ver [ROADMAP.md](ROADMAP.md): firma XMLDSig real (PKCS#12), firma del TED + PDF417 con CAF real, integración real con el SII. Requieren un certificado y un CAF reales para implementarse y verificarse.
