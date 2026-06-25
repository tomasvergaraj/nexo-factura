# Nexo Factura

Sistema de **facturación electrónica (DTE)** para Chile, conforme al modelo del **SII**. Es un proyecto de portafolio construido para demostrar arquitectura backend en **Java / Spring Boot** junto a un frontend moderno en **React + TypeScript**.

El foco está en las partes que realmente importan en un sistema tributario chileno: gestión de **folios (CAF)** con asignación segura bajo concurrencia, el flujo completo de **emisión → timbre → firma → envío al SII**, y el cálculo correcto de **IVA 19%**, montos afectos y exentos.

---

## Qué demuestra este proyecto

- **Spring Boot 3 + Java 21** con una arquitectura por capas y por dominio (auth, empresa, cliente, producto, folio, documento, tributario, dashboard).
- **Concurrencia real en la asignación de folios**: cada emisión reserva el siguiente folio del CAF con bloqueo pesimista (`SELECT ... FOR UPDATE`) más *optimistic locking* por versión. Incluye un test de integración con **Testcontainers** que dispara 50 emisiones simultáneas y verifica que no haya folios duplicados ni perdidos.
- **Seguridad con JWT** (stateless), `BCrypt` y autorización por método.
- **Persistencia con JPA** y migraciones versionadas con **Flyway**.
- **Generación de XML del DTE con JAXB** según la estructura del SII (Encabezado, Detalle, TED) y **PDF** con la representación impresa (recuadro rojo RUT/folio, detalle, timbre).
- **Mapeo con MapStruct**, manejo centralizado de errores y documentación **OpenAPI / Swagger**.
- **Frontend React 18 + Vite + TypeScript** con un sistema de diseño propio (sobrio, registro fintech), validación de **RUT (módulo 11)** y formato **CLP** en el cliente.

---

## Arquitectura

```
nexo-factura/
├── backend/                 Java 21 · Spring Boot 3 · PostgreSQL
│   └── src/main/java/cl/nexosoftware/factura/
│       ├── auth/            Usuarios, login, JWT
│       ├── empresa/         Emisor (razón social, giro, RUT)
│       ├── cliente/         Receptores
│       ├── producto/        Catálogo de ítems
│       ├── folio/           CAF y asignación de folios (concurrencia)
│       ├── documento/       DTE: cabecera, líneas, referencias, estados
│       ├── tributario/      IVA, XML (JAXB), TED, firma, SII, PDF
│       ├── dashboard/       Indicadores de emisión
│       ├── config/          Seguridad, propiedades, OpenAPI
│       └── common/          Errores y paginación
└── frontend/                React 18 · Vite · TypeScript · Tailwind
    └── src/
        ├── pages/           Landing, Login, panel (Resumen, Documentos, Nueva factura)
        ├── components/      UI base, AppShell, vista de factura
        └── lib/             Tipos, API, formato (CLP / RUT), datos demo
```

### Ciclo de vida de un documento

```
BORRADOR → FIRMADO → ENVIADO → ACEPTADO / RECHAZADO / CON REPARO
                                   ↓
                                ANULADO
```

Al **emitir**, dentro de una misma transacción: se reserva el folio del CAF, se genera el TED, se arma el XML, se firma y el documento queda `FIRMADO`. Luego se **envía al SII** y se consulta su estado.

---

## Cómo ejecutarlo

### Opción A — Docker (todo junto)

```bash
docker compose up --build
```

- Frontend: http://localhost:8081
- API + Swagger: http://localhost:8080/swagger-ui.html
- PostgreSQL: localhost:5432

### Opción B — Manual

**Backend** (requiere Java 21 y un PostgreSQL local con base `nexo_factura`, usuario `nexo` / `nexo`):

```bash
cd backend
mvn spring-boot:run
```

**Frontend** (requiere Node 20+):

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
```

> El frontend trae `USE_MOCK = true` en `src/lib/api.ts`, así que **funciona de forma autónoma con datos de demostración** sin necesidad del backend. Cámbialo a `false` para consumir la API real.

### Credenciales de demostración

```
Correo:      admin@nexofactura.cl
Contraseña:  nexo1234
```

---

## Qué está implementado como *stub*

Para que el flujo completo sea ejecutable sin trámites externos, tres piezas son simuladas y están aisladas detrás de interfaces, listas para reemplazar por una implementación real:

- **Firma electrónica** (`FirmaElectronica`): inserta un nodo de firma simbólico. La versión real usa el **certificado digital PKCS#12** del representante legal y XMLDSig.
- **Comunicación con el SII** (`SiiGateway`): devuelve un TrackID sintético. La versión real obtiene semilla y token, arma el sobre `EnvioDTE` y consulta estado en el ambiente de certificación o producción.
- **Timbre PDF417**: el TED se genera, pero el código de barras se dibuja como marcador. Se completa al integrar un CAF real.

Estas decisiones están comentadas en el código donde corresponde.

---

## Stack

**Backend:** Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, PostgreSQL, Flyway, MapStruct, JAXB, OpenPDF, JJWT, springdoc-openapi, Testcontainers.

**Frontend:** React 18, Vite 6, TypeScript, Tailwind CSS v4, React Router, Axios, lucide-react.

---

© Nexo Software SpA · Quillota, Chile
