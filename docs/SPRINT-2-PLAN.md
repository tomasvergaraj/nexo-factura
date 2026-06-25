# Sprint 2 — Plan de implementación (nivel archivo)

> Derivado de un workflow de planificación (5 diseñadores + tech lead).
> Objetivo: completitud tributaria (NC/ND, módulo 11), timbre PDF417 real, frontend completo y cierre del riesgo de arquitectura (perfil prod) — todo verificable sin certificado/CAF reales.

## Contratos compartidos

1. **Perfil de producción canónico = `prod`.** Todo `@Profile` usa `prod` (no `produccion`). Stubs → `@Profile("!prod")`; beans reales → `@Profile("prod")`. El CD exporta `SPRING_PROFILES_ACTIVE=prod`. No debe quedar `produccion` como literal de `@Profile`.
2. **Tipos DTE (código SII):** FACTURA_AFECTA=33, FACTURA_EXENTA=34, BOLETA_AFECTA=39, BOLETA_EXENTA=41, NOTA_DEBITO=56, NOTA_CREDITO=61. Exigen referencias: **56 y 61**.
3. **TipoReferencia (CodRef):** ANULA_DOCUMENTO=1, CORRIGE_TEXTO=2, CORRIGE_MONTO=3. Solo **NOTA_CREDITO con ANULA_DOCUMENTO** anula el documento original.
4. **Endpoints** bajo `/api/empresas/{empresaId}/...`: clientes/productos [GET ?q=, POST, PUT/{id}], folios [GET → **List plana** (no `data.contenido`), POST], documentos [GET ?estado=, GET/{id}, POST (referencias[] opcional), POST/{id}/emitir|enviar|estado-sii, GET/{id}/pdf]. emitir/enviar/estado-sii y CAF POST exigen rol ADMIN/EMISOR.
5. **Errores:** validación → 400 (`ApiError.detalles`); regla de negocio → 409; permiso → 403. **El interceptor axios NO debe cerrar sesión ante 403 con token válido** (solo ante 401); ocultar botones por `obtenerUsuario().rol`.
6. **RUT:** fuente de verdad `frontend/src/lib/format.ts` `validarRut`; el util backend `Rut.esValido` lo replica bit a bit. Mensaje: "RUT invalido: digito verificador incorrecto". Fixtures/seeds/tests deben usar DV correcto.
7. **TED determinista:** `TSTED` se deriva de `doc.getCreadoEn()`, así regenerar el TED en el flujo del PDF reproduce el mismo string que el del XML. El PDF417 codifica el `<TED>` (ISO-8859-1). La firma FRMT sigue siendo placeholder hasta integrar el CAF.

## Olas

| Ola | Workstreams (paralelos) | Notas |
|---|---|---|
| 1 | **prod-skeletons** (tributario) ∥ **rut-modulo11** (common/validation + DTOs) | Fundacional, directorios disjuntos. **Auditar RUTs de fixtures antes de cerrar.** |
| 2 | **notas-cd** (documento/) ∥ **pdf417** (tributario/ + pom) | Definen contratos de API que el front consume. pom añade zxing (primer build descarga). |
| 3 | **frontend** (orden interno: types→mock→api→ui→pages→App) | Depende de los contratos de la ola 2. |

## Pasos (resumen por workstream)

**prod-skeletons:** stubs `@Profile("!prod")`; crear `FirmaElectronicaProd`/`SiiGatewayProd` `@Profile("prod")` que lanzan `UnsupportedOperationException` (contexto levanta, operación falla fail-fast); `PerfilProdContextoIT` verifica que con perfil prod cargan los beans Prod.

**rut-modulo11:** `common/validation/Rut.java` (módulo 11), `@RutValido` + `RutValidoValidator`; reemplazar `@Pattern` por `@RutValido` en `ClienteRequest`/`EmpresaRequest`; `RutTest`. Auditar RUTs de fixtures/seeds (DV correcto).

**notas-cd:** `DocumentoRepository.findByEmpresaIdAndTipoDteAndFolio`; en `DocumentoService.crear` validar referencias obligatorias y coherentes para 56/61; en `emitir`, si NC con ANULA_DOCUMENTO → transicionar el original **ACEPTADO → ANULADO** en la misma `@Transactional`; `DocumentoDtos` (nuevo `ReferenciaResponse` + `referencias` en `DocumentoResponse`) y `DocumentoMapper` (atómico). No añadir `referencias` al `@EntityGraph`.

**pdf417:** pom + zxing 3.5.3; `ModeloDte.Ted` con `@XmlRootElement`; `TedGenerator.aXml(ted)`; `Pdf417Generator` (`@Component`, PNG en memoria); `PdfDteServiceImpl` inyecta TedGenerator+Pdf417Generator, `timbre(tedXml)` dibuja el PDF417 con fallback a texto; ajustar mock en `DocumentoServiceTransicionesIT`.

**frontend:** `types.ts` (ClienteRequest/ProductoRequest/Caf/CafRequest/TipoReferencia/CODIGO_TIPO_DTE) → `mock.ts` (foliosMock) → `api.ts` (CRUD + folios plano + emitir/enviar/estado/pdf + interceptor 403) → `ui.tsx` (Modal/Checkbox/Textarea/EmptyState) → páginas `Clientes`/`Productos`/`Folios`/`DetalleDocumento` + `NuevaFactura` (selector tipo + referencias) + `Documentos` (filas navegables) → `App.tsx` (rutas reales + `/app/documentos/:id`).

## Riesgos principales
- Firma de `DocumentoResponse` cambia → `DocumentoMapper` en el mismo paso. 
- zxing debe descargarse en el primer build Docker.
- `@Profile("!prod")`: ningún pipeline debe exportar `produccion`.
- Fixtures con RUT de DV inválido → 400 (auditar).
- Anulación solo desde ACEPTADO (la máquina no permite ENVIADO→ANULADO).
- Lazy `referencias`: `toResponse` solo en métodos transaccionales.
- Inyección en `PdfDteServiceImpl` → revisar que ningún test haga `new PdfDteServiceImpl()`.
- Interceptor 403 no debe desloguear; orden de módulos del front para no romper `tsc`.
