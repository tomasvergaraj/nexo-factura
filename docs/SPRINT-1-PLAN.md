# Sprint 1 — Plan de implementación (nivel archivo)

> Derivado de un workflow de planificación (4 diseñadores + tech lead).
> Objetivo: sistema **seguro, real y verificable** sin certificados/CAF externos.

## Contratos compartidos (todos los workstreams los respetan)

1. **TenantGuard único**: un solo `@Component("tenantGuard")` en `cl.nexosoftware.factura.auth`. Firma: `public boolean checkEmpresa(Long empresaId)`. Se invoca como `@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")`. No duplicar en otros paquetes.
2. **Fuente del empresaId del usuario**: el claim `empresaId` del JWT (no re-consultar BD). `JwtAuthenticationFilter` setea como principal un `UsuarioPrincipal(id, email, rol, empresaId, authorities ROLE_<rol>)`. `SecurityUtils.currentEmpresaId()` lo lee. Regla: path no-null, claim no-null e iguales → permitido; cualquier otro caso → `false`.
3. **Forma del 403**: cross-tenant y `@PreAuthorize` denegado → `AccessDeniedException` → HTTP **403** (ya mapeado en `GlobalExceptionHandler`). La fila ajena dentro de la propia empresa (IDOR sobre `{id}`) → **404** (no filtrar existencia).
4. **Defensa en profundidad (2 capas)**: capa 1 = `@PreAuthorize` valida path vs claim; capa 2 = los services scopean por fila con `findByIdAndEmpresaId(id, empresaId)` → 404. Firmas: `ClienteService.actualizar(Long empresaId, Long id, ClienteRequest)` y `ProductoService.actualizar(Long empresaId, Long id, ProductoRequest)`.
5. **estado-sii**: la ruta NO cambia (`/api/empresas/{empresaId}/documentos/{id}/estado-sii`); el verbo pasa de `@GetMapping` a `@PostMapping`. Solo WS3 toca esa línea; WS1 anota a nivel de **clase** (no choca).
6. **Env var frontend**: `VITE_USE_MOCK` (string, comparado contra `"true"`; default `false`). `USE_MOCK = import.meta.env.VITE_USE_MOCK === "true"`. Opcional `VITE_API_BASE_URL` (default `/api`). Tipar en `vite-env.d.ts`. Archivos `.env`, `.env.development`, `.env.example`.
7. **Secret JWT**: `application.yml` deja de tener el default; el default de dev vive SOLO en `application-dev.yml` (perfil activo por defecto) para que dev/test/CI levanten sin variables. En prod, `APP_JWT_SECRET` obligatorio y un `JwtSecretValidator @Profile("prod")` aborta el arranque si falta, es <32 bytes o es el secret de dev conocido.
8. **Compilación -parameters**: el SpEL `#empresaId` resuelve el nombre del `@PathVariable` solo con `-parameters` (default del plugin). Fallback: `@PathVariable("empresaId")` explícito.

## Olas de ejecución

| Ola | Workstreams | Por qué |
|---|---|---|
| 1 | **WS3** (config backend: hardening JWT + estado-sii GET→POST) ∥ **WS2** (frontend) | Directorios disjuntos (`backend/src/main/resources` + 1 edit aislado en `DocumentoController` vs `frontend/`). Fijan 2 contratos (POST de estado-sii, `VITE_USE_MOCK`). |
| 2 | **WS1** (seguridad multi-tenant anti-IDOR) | Productor del contrato de seguridad. Va tras WS3 porque ambos tocan `DocumentoController` (WS3 el verbo, WS1 la anotación de clase). |
| 3 | **WS4** (tests máquina de estados + aislamiento tenant) | Solo `backend/src/test`. Depende del `TenantGuard`/principal de WS1. |

## Pasos ordenados

**WS3 — Correcciones backend**
1. `application.yml`/`application-dev.yml`/`application-prod.yml`: quitar default del secret de `application.yml`; moverlo a `application-dev.yml` como `${APP_JWT_SECRET:<literal-dev>}`; prod sin default.
2. Crear `config/JwtSecretValidator.java` `@Component @Profile("prod")`: `@PostConstruct` valida secret no-blanco, ≥32 bytes UTF-8, ≠ secret de dev; si falla → `IllegalStateException`.
3. `documento/DocumentoController.java`: `@GetMapping("/{id}/estado-sii")` → `@PostMapping`. Solo el verbo.

**WS2 — Frontend a API real**
4. `lib/api.ts`, `lib/auth.ts`, `vite-env.d.ts`, `.env`, `.env.development`, `.env.example`: `USE_MOCK` desde env; tipar `ImportMetaEnv`; helper `empresaIdActual()`; interceptor de respuesta axios 401/403 → `cerrarSesion()` + redirección a login (guardando contra loop si ya está en login).
5. `pages/app/Dashboard.tsx`, `Documentos.tsx`, `NuevaFactura.tsx`, `pages/Login.tsx`, `App.tsx`: reemplazar los 6 literales `empresaId=1` por `empresaIdActual()`; login real contra `POST /api/auth/login` capturando el 401 de credenciales localmente; `RequireAuth` en `/app/*`.

**WS1 — Seguridad multi-tenant**
6. Crear `auth/UsuarioPrincipal.java` + `auth/SecurityUtils.java`; ampliar `auth/JwtService.java` (`extraerEmpresaId/Rol/Uid` con `Number.longValue()`); `auth/JwtAuthenticationFilter.java` setea `UsuarioPrincipal` (mantiene `loadUserByUsername` solo para validar activo; tolera `empresaId` null).
7. Crear `auth/TenantGuard.java` (`checkEmpresa`).
8. `cliente/ProductoRepository` + `ClienteRepository`: `findByIdAndEmpresaId`. Services: firma `actualizar(empresaId, id, req)` + scoping 404. **Revisar callers de `buscar(id)` (DocumentoService) antes de cambiar firmas.**
9. `@PreAuthorize("@tenantGuard.checkEmpresa(#empresaId)")` a nivel de clase en los 5 controllers con `/{empresaId}` + roles en mutaciones sensibles (CAF, emitir/enviar). Ajustar llamadas a `actualizar(empresaId, id, req)`.
10. Verificar scoping por `empresaId` en `DocumentoService`/`CafService`/`DashboardService` (404 si la fila no pertenece).

**WS4 — Tests**
11. `documento/EstadoDteTransicionesTest.java`: unitario puro de transiciones válidas/ inválidas.
12. `documento/DocumentoServiceTransicionesIT.java`: IT (Testcontainers) con `@MockBean` de SII/Firma/TED/XML.
13. `seguridad/AislamientoMultiTenantIT.java`: `@AutoConfigureMockMvc`; empresa A vs B; login real; cross-tenant 403, mismo-tenant 2xx, sin token 401, fila ajena 404; itera los 5 controllers.

## Riesgos y mitigaciones (resumen)
- Doble edit en `DocumentoController` → secuenciar olas; WS1 anota a nivel de clase.
- `TenantGuard` duplicado → un único guard en `auth`.
- Cambio de principal rompe casts a `User` → hoy solo el filtro setea principal; `UsuarioPrincipal` implementa `UserDetails`.
- `ClassCastException` Integer/Long en claim → `Number.longValue()`.
- Cambio de firma `actualizar()` rompe callers → revisar usos antes.
- Stub de dev deja de levantar → el default del secret DEBE quedar en `application-dev.yml`.
- Build solo en Docker (`maven:3.9-eclipse-temurin-21`).
