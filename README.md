<div align="center">

<img src="frontend/public/logo.png" alt="Nexo Factura" width="150" />

# Nexo Factura

**Facturación electrónica (DTE) para Chile, conforme al modelo del SII.**

Backend en Java 21 / Spring Boot 3 · Frontend en React 18 / TypeScript · PostgreSQL

![Java](https://img.shields.io/badge/Java-21-e76f00)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6db33f)
![React](https://img.shields.io/badge/React-18-61dafb)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178c6)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Licencia](https://img.shields.io/badge/licencia-propietaria-lightgrey)

</div>

---

## Descripción

**Nexo Factura** es una plataforma de facturación electrónica que emite Documentos
Tributarios Electrónicos (DTE) según el modelo del **Servicio de Impuestos Internos
(SII)** de Chile: facturas afectas y exentas, boletas, notas de crédito y débito.
Cubre el ciclo completo **emisión → cálculo de IVA → armado del XML → timbre (TED)
→ firma → envío al SII**, junto con la gestión de folios (CAF), libros de compra/venta
(IECV), el reporte de consumo de folios (RCOF) y un panel de operación.

Es un **proyecto de portafolio** construido para demostrar arquitectura de backend
tributario con concurrencia real, seguridad multi-empresa y un frontend de producto
cuidado. La integración tributaria crítica (firma XMLDSig con certificado y diálogo
real con el SII) está **aislada tras interfaces y simulada** para que el flujo completo
sea ejecutable sin trámites externos — ver [Qué está simulado](#qué-está-simulado).

> **Para quién es esto.** Está pensado como una demostración técnica evaluable de
> extremo a extremo, no como un producto en producción ante el SII.

---

## Funcionalidades

### Emisión y tributación
- **Ciclo de vida completo del DTE** con máquina de estados validada:
  `BORRADOR → FIRMADO → ENVIADO → ACEPTADO / RECHAZADO / REPARO → ANULADO`.
- **Tipos de documento**: factura afecta (33) y exenta (34), boleta afecta (39) y
  exenta (41), nota de débito (56) y crédito (61).
- **Cálculo tributario** en CLP entero (IVA 19 % con redondeo half-up), montos afectos
  y exentos, y **boletas con precio bruto** (IVA incluido) desglosando el neto.
- **Impuestos adicionales y retenciones**: ILA de bebidas, artículos suntuarios y
  **retención de IVA** por cambio de sujeto, emitidos como bloques `ImptoReten` del DTE.
- **Notas de crédito/débito** con bloque `Referencia` obligatorio y anulación del
  documento referenciado.
- **Folios (CAF)** con asignación segura del siguiente folio bajo concurrencia
  (bloqueo pesimista `SELECT … FOR UPDATE` + versión optimista).
- **XML del DTE con JAXB** (Encabezado, Detalle, Referencias, TED) y **validación XSD
  pre-firma**: una emisión cuyo XML no cumple el esquema falla con **422** y revierte
  el folio.
- **Representación impresa en PDF** (recuadro rojo RUT/folio, detalle, timbre PDF417).

### Operación
- **Contingencia del SII**: si el SII no está disponible al enviar, el DTE queda
  `EN_CONTINGENCIA` con traza (intentos, último envío, último error) en vez de fallar;
  reintento **individual** y **masivo** (una transacción por documento).
- **Reenvío de rechazados** con el mismo XML firmado.
- **Libros de compra/venta (IECV)**: libro de ventas desde los DTE emitidos y libro
  de compras desde el registro manual de documentos recibidos; salida en JSON y XML
  `LibroCompraVenta`.
- **RCOF** (Reporte de Consumo de Folios) diario para boletas, con su XML `ConsumoFolios`.
- **Panel** con indicadores de emisión del período y estado ante el SII.

### Seguridad y plataforma
- **Autenticación JWT stateless** (HMAC-SHA256) con **refresh tokens rotatorios**,
  detección de reuso, revocación en logout y access token corto (60 min).
- **Rate limiting** de login/registro por email e IP (→ **429** con `Retry-After`).
- **Aislamiento multi-empresa**: cada recurso cuelga de `/api/empresas/{empresaId}/…`
  y un *tenant guard* valida el `empresaId` de la ruta contra el claim del token
  (**403** si no coincide, **404** ante filas de otra empresa).
- **Inmutabilidad del DTE**: campos tributarios congelados (`updatable=false`) + **sello
  de integridad** SHA-256 del XML firmado; duplicados → **409**.
- **Roles** `ADMIN` / `EMISOR` con autorización por método.

---

## Tecnologías

**Backend** — Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Actuator),
PostgreSQL 16, Flyway, MapStruct, JAXB, OpenPDF, ZXing (PDF417), JJWT, springdoc-openapi,
Lombok, Testcontainers.

**Frontend** — React 18, Vite 6, TypeScript 5, Tailwind CSS v4, React Router 6, Axios,
lucide-react.

**Infraestructura** — Docker Compose (PostgreSQL + backend + frontend con Nginx).

---

## Arquitectura

Monorepo con dos aplicaciones desplegables de forma independiente:

```
nexo-factura/
├── backend/                 Java 21 · Spring Boot 3.3 · PostgreSQL
│   └── src/main/java/cl/nexosoftware/factura/
│       ├── auth/            Usuarios, login, JWT, refresh tokens, rate limiting
│       ├── empresa/         Emisor (razón social, giro, RUT)
│       ├── cliente/         Receptores
│       ├── producto/        Catálogo de ítems
│       ├── folio/           CAF y asignación de folios (concurrencia)
│       ├── documento/       DTE: cabecera, líneas, referencias, estados, contingencia
│       ├── compra/          Documentos recibidos (libro de compras)
│       ├── libro/           Libros IECV (compra/venta)
│       ├── rcof/            Reporte de consumo de folios
│       ├── tributario/      IVA, impuestos, XML (JAXB), TED, firma, SII, PDF, XSD
│       ├── dashboard/       Indicadores de emisión
│       ├── config/          Seguridad, propiedades, OpenAPI
│       └── common/          Errores y paginación
└── frontend/                React 18 · Vite · TypeScript · Tailwind
    └── src/
        ├── pages/           Sitio público (Landing, Sobre, Contacto, Legal, Estado)
        │   └── app/         Panel (Resumen, Documentos, Nueva factura, Clientes,
        │                    Productos, Folios, RCOF, Compras, Libros, Configuración)
        ├── components/      UI base, AppShell, sitio, vista de factura
        └── lib/             Tipos, API, formato (CLP / RUT), datos demo
```

### Ciclo de vida de un documento

```
BORRADOR ──emitir──▶ FIRMADO ──enviar──▶ ENVIADO ──▶ ACEPTADO
                                             │           │
                                             │           └──▶ (reenviar) si RECHAZADO
                                             ▼
                                      EN_CONTINGENCIA ──(reenviar)──▶ ENVIADO
```

Al **emitir**, dentro de una misma transacción se reserva el folio del CAF, se genera
el TED, se arma el XML, se valida contra el XSD y se firma; el documento queda `FIRMADO`
con su sello de integridad. Luego se **envía al SII** y se consulta su estado; si el SII
no responde, queda `EN_CONTINGENCIA` para reintento.

---

## Instalación y ejecución

### Requisitos previos

- **Docker** y Docker Compose (opción recomendada), **o**
- **Java 21** + **Maven** y **PostgreSQL 16** + **Node 20+** (opción manual).

### Opción A — Docker (todo junto)

```bash
docker compose up --build
```

| Servicio          | URL                                            |
|-------------------|------------------------------------------------|
| Frontend          | http://localhost:8081                          |
| API + Swagger UI  | http://localhost:8082/swagger-ui.html          |
| Health            | http://localhost:8082/actuator/health          |
| PostgreSQL        | localhost:5432 (`nexo` / `nexo`)               |

> El backend se publica en el host **8082** (el puerto interno del contenedor sigue
> siendo 8080, porque el 8080 del host lo ocupa otra aplicación). Si tienes el 8080
> libre, puedes volver a `"8080:8080"` en `docker-compose.yml`.

Para reconstruir solo el frontend sin tocar la base de datos ni el backend:

```bash
docker compose up -d --no-deps --build frontend
```

### Opción B — Manual

**Backend** (Java 21 + PostgreSQL con base `nexo_factura`, usuario `nexo` / `nexo`):

```bash
cd backend
mvn spring-boot:run        # http://localhost:8080
```

Flyway aplica las migraciones (`V1`–`V6`) al arrancar, incluida la semilla de desarrollo.

**Frontend** (Node 20+):

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173
```

> Por defecto el frontend **consume la API real** (`VITE_USE_MOCK=false`). Para revisar
> la interfaz de forma autónoma con datos de demostración, sin levantar el backend,
> define `VITE_USE_MOCK=true` en `frontend/.env`.

---

## Uso

### Credenciales de demostración

```
Correo:      admin@nexofactura.cl
Contraseña:  nexo1234
```

### Autenticación

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@nexofactura.cl","password":"nexo1234"}'
```

```jsonc
{
  "token": "eyJhbGciOiJIUzI1NiI…",   // access JWT (Bearer, 60 min)
  "refreshToken": "…",                // opaco, rotado en cada /refresh
  "usuario": { "id": 1, "rol": "ADMIN", "empresaId": 1, … }
}
```

Usa el `token` en las llamadas siguientes: `Authorization: Bearer <token>`.

### Endpoints principales

Todos cuelgan del emisor: `/api/empresas/{empresaId}/…`.

| Recurso        | Endpoints                                                                             |
|----------------|---------------------------------------------------------------------------------------|
| Auth           | `POST /api/auth/login` · `/refresh` · `/logout`                                       |
| Empresa        | `GET`/`PUT /api/empresas/{id}`                                                         |
| Clientes       | `GET`/`POST`/`PUT …/clientes`                                                          |
| Productos      | `GET`/`POST`/`PUT …/productos`                                                         |
| Folios (CAF)   | `GET`/`POST …/folios`                                                                  |
| Documentos     | `GET`/`POST …/documentos` · `POST …/{id}/emitir` · `/enviar` · `/reenviar` · `/estado-sii` · `GET …/{id}/pdf` |
| Contingencia   | `POST …/documentos/reenviar-pendientes`                                                |
| Compras        | `GET`/`POST`/`DELETE …/compras`                                                        |
| Libros (IECV)  | `GET …/libros/ventas` · `/libros/compras` (+ `/xml`)                                   |
| RCOF           | `GET …/rcof?fecha=YYYY-MM-DD`                                                           |
| Dashboard      | `GET …/dashboard`                                                                      |

La referencia completa e interactiva está en **Swagger UI** (`/swagger-ui.html`).

---

## Pruebas

```bash
cd backend
mvn test                   # unitarias + integración (Testcontainers levanta PostgreSQL)
```

```bash
cd frontend
npm run build              # type-check (tsc) + build de producción (Vite)
```

Destacan la **prueba de concurrencia de folios** (`FolioServiceConcurrencyTest`: 50
emisiones simultáneas sobre PostgreSQL real, verifica que no haya folios duplicados ni
perdidos), la del **cálculo tributario** (`CalculadoraImpuestosTest`) y las de
**contingencia/reenvío** (`ContingenciaReenvioIT`).

> La integración de tests requiere **Docker en ejecución** (Testcontainers).

---

## Configuración

El backend se configura por variables de entorno (perfiles `dev` / `prod`):

| Variable                        | Descripción                                             | Default (dev)            |
|---------------------------------|---------------------------------------------------------|--------------------------|
| `SPRING_PROFILES_ACTIVE`        | Perfil activo (`dev` / `prod`)                          | `dev`                    |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | Conexión a PostgreSQL                                 | `localhost` / `5432` / `nexo_factura` |
| `DB_USER` / `DB_PASSWORD`       | Credenciales de la base                                 | `nexo` / `nexo`          |
| `APP_JWT_SECRET`                | Secreto HMAC (≥ 32 bytes). **Obligatorio en prod**      | *(default solo dev)*     |
| `APP_JWT_EXPIRATION_MINUTES`    | Vigencia del access token                               | `60`                     |
| `APP_JWT_REFRESH_EXPIRATION_DAYS` | Vigencia del refresh token                            | `14`                     |
| `APP_CORS_ORIGINS`              | Orígenes permitidos (CSV)                               | `http://localhost:5173,…`|
| `APP_SII_AMBIENTE`              | `CERTIFICACION` (Maullín) / `PRODUCCION` (Palena)       | `CERTIFICACION`          |
| `APP_SII_CERT_PATH` / `_PASSWORD` | Certificado PKCS#12 del representante legal            | *(vacío)*                |
| `APP_SII_FIRMA_MODO`            | `GLOBAL` (un certificado por ambiente) / `POR_EMPRESA`  | `GLOBAL`                 |
| `APP_MASTER_KEY`                | Clave maestra AES-256 (32 bytes en base64) de los secretos en reposo | *(default solo dev)* |
| `APP_DTE_VALIDAR_XSD`           | Validar el XML contra el XSD antes de firmar            | `true`                   |
| `APP_RATE_LIMIT_ENABLED`        | Rate limiting de autenticación                          | `true`                   |

En **producción** el arranque **falla si falta `APP_JWT_SECRET`** (no hay default fuera
de desarrollo).

### Secretos en reposo (`APP_MASTER_KEY`)

Se cifran con AES-256-GCM antes de tocar la base (`CifradorSecretos`): el **XML del
CAF** —que lleva la clave privada RSA con la que se timbra el TED— y, en modo
`POR_EMPRESA`, el **PKCS#12 de cada empresa y su clave**. La clave maestra vive solo
en el entorno, nunca en la BD ni en los logs.

- Sin `APP_MASTER_KEY` no se pueden cargar CAF (la API responde 422 en vez de guardar
  el CAF en claro). Fuera de `dev` no hay default.
- Es **persistente**: perderla o cambiarla deja ilegibles los CAF y certificados ya
  guardados (habría que volver a subir los XML del SII). Respaldarla aparte de la BD.
- Las filas anteriores al cifrado se migran solas al arrancar (`CafCifradoBackfill`);
  no compartas una misma base entre entornos con claves maestras distintas.

---

## Perfiles: real en `prod`, simulado en `dev`

La validez tributaria completa está **implementada de verdad** en el perfil `prod`
(Sprint 6, con certificado PKCS#12 y CAF reales):

- **Firma electrónica** (`FirmaElectronicaProd`): XMLDSig con el JDK y los algoritmos
  que el XSD oficial del SII **fija por schema** (C14N inclusive, `rsa-sha1`, digest
  `sha1`), sobre el certificado PKCS#12 del firmante autorizado.
- **Timbre (TED) real**: el CAF del SII se parsea y valida al cargarlo (clave PKCS#1
  incluida) y el `FRMT` se firma con su clave privada (`SHA1withRSA` sobre el `DD`
  aplanado según la regla oficial); el PDF417 lo imprime tal cual.
- **Comunicación con el SII** por sus dos canales reales: **API REST de boleta
  electrónica** (39/41: semilla → token → envío a pangal/rahue → estado) y **canal
  clásico de DTE** (33/34/56/61: SOAP maullin/palena, upload `EnvioDTE` y
  `QueryEstUp`). Todo XML se valida contra los **XSD oficiales vendoreados** antes
  de salir.

Para desarrollar sin certificados ni CAF reales, el perfil `dev` (default) conserva
los dobles: `FirmaElectronicaStub` (firma con forma schema-válida pero valores
simbólicos) y `SiiGatewayStub` (TrackID sintético, configurable en runtime para
simular caídas o rechazos E2E). El ambiente de **certificación** del SII se activa
con `docker-compose.cert.yml` (perfil `prod` + `APP_SII_AMBIENTE=CERTIFICACION`).

---

## Roadmap

El backlog priorizado (P0/P1/P2) está **completo**, incluida la integración
tributaria real (P0-4/5/6, Sprint 6). Quedan follow-ups documentados: E2E de
notas 56/61 y factura exenta 34 (falta timbrar sus CAF), certificado y resolución
por empresa (multi-tenant), verificación de la FRMA del CAF y el trámite formal de
certificación → producción ante el SII.

El detalle, con la línea base de la auditoría y el registro por sprint, vive en
[`docs/ROADMAP.md`](docs/ROADMAP.md). El progreso verificado está en
[`docs/PROGRESS.md`](docs/PROGRESS.md).

---

## Estado del proyecto

En desarrollo activo. Los sprints 1–6 están completos (auth y seguridad, completitud
tributaria, notas y boletas, impuestos adicionales, contingencia y libros IECV, y la
**integración tributaria real**: firma XMLDSig, TED con FRMT real e integración con
el SII por ambos canales — con una **factura 33 ACEPTADA por el SII de certificación**
como gate de cierre; el detalle está en [`docs/PROGRESS.md`](docs/PROGRESS.md)).

## Autor

**Nexo Software SpA** — Quillota, Chile · [contacto@nexosoftware.cl](mailto:contacto@nexosoftware.cl)

## Licencia

Proyecto propietario. © Nexo Software SpA. Todos los derechos reservados; no se concede
licencia para su redistribución o uso comercial.
