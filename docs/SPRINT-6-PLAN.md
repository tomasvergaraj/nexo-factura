# Sprint 6 — Integración tributaria real (P0-4/5/6)

> **Estado: IMPLEMENTADO (2026-07-21).** Las cuatro fases + review multi-ángulo
> están completas; el registro verificado (226 unitarios, hallazgos del review y
> el resultado del E2E de certificación) está en [PROGRESS.md](PROGRESS.md), y el
> ROADMAP §2/§10/§11 quedó actualizado. Este documento se conserva como el diseño
> congelado de referencia.

> Diseño con contrato congelado, previo a la implementación. Fecha: 2026-07-20
> (ampliado el 2026-07-21 con el CAF de factura). Insumos: certificado PKCS#12 real
> (`secrets/19726539-6.pfx`, Acepta.com, RSA 2048, vigente 2026-04-24 → 2029-04-24,
> subject `SERIALNUMBER=19726539-6`) y DOS CAF de certificación reales del mismo
> emisor `78397017-1` (ambos IDK 100, clave RSA 512 bits):
> `secrets/caf_39_certificacion.xml` (boleta 39, folios 1–100, no vence) y
> `secrets/caf_factura.xml` (factura 33, folios 1–50, FA 2026-04-28 →
> **vence 2026-10-28**, Res. 58/2017). Todos verificados localmente. Datos de la
> carátula de certificación confirmados en el portal SII: **FchResol 2026-05-14,
> NroResol 0** (ya en `.env` como `APP_SII_FCH_RESOL`).
> La fidelidad SII de este diseño se verificó contra los XSD oficiales descargados
> el 2026-07-20 (`schema_dte.zip`, `schema_envio_bol.zip`), el spec OpenAPI oficial
> de la API de boleta (`www4c.sii.cl/bolcoreinternetui/api/`), el Instructivo Técnico
> de emisión (v28.10.21), el Formato DTE v2.5 (2026-02), el Formato Boleta v4.2 y la
> Res. Ex. 58/2017, contrastados con LibreDTE y niclabs/DTE.

## 1. Objetivo y alcance

Reemplazar los tres stubs/esqueletos que separan a Nexo Factura de la validez
tributaria real, con verificación de extremo a extremo en el **ambiente de
certificación** del SII para las dos familias con CAF disponible:

- **P0-5** — parseo y validación de los CAF reales + firma real del TED (FRMT)
  con la clave privada del CAF.
- **P0-4** — firma XMLDSig real del DTE y del sobre con el PKCS#12, y
  alineamiento del XML al esquema oficial (namespace `SiiDte`).
- **P0-6** — integración real con el SII por ambos canales: **API REST de boleta
  electrónica** para 39/41 y **flujo clásico de DTE** (Maullín en certificación:
  semilla → token → upload del `EnvioDTE` → consulta por TrackID) para 33/34/56/61.

**Gate de éxito del sprint**: una boleta 39 **y** una factura 33 emitidas por la
app, firmadas de verdad, aceptadas por el SII de certificación (TrackID real y
estado final consultado por cada canal).

## 2. Correcciones de fidelidad al ROADMAP (verificadas contra fuentes oficiales)

Igual que en el Sprint 4, la verificación previa contra la realidad del SII
corrigió supuestos del backlog **antes** de implementar:

| # | Supuesto del ROADMAP/código | Realidad verificada | Fuente |
|---|---|---|---|
| C1 | P0-4: "C14N, **SHA256withRSA**" | El XSD oficial `xmldsignature_v10.xsd` **fija** con `fixed`/enumeración: C14N inclusive (`REC-xml-c14n-20010315`), `rsa-sha1`, digest `sha1`. SHA-256 ni siquiera pasa la validación de schema. Mismo juego para FRMT (`SHA1withRSA`) y FRMA del CAF. | XSD oficial (schema_dte.zip, vigente 06-02-2026) |
| C2 | P0-6: "integración con **Maullín**: semilla→token→**EnvioDTE**" | Las boletas 39/41 **no** usan el flujo SOAP de Maullín/Palena (ese es el de facturas 33/56/61). Desde 2021 van por la **API REST de boleta**: semilla/token/consultas en `apicert.sii.cl` (cert) / `api.sii.cl` (prod), envío del sobre **EnvioBOLETA** en `pangal.sii.cl` (cert) / `rahue.sii.cl` (prod). Tokens independientes entre ambos flujos. | Spec OpenAPI oficial del SII + Instructivo Boleta 2021 |
| C3 | — (supuesto informal) | **pangal = certificación, rahue = producción** (cita literal del `servers:` del spec oficial). | Spec OpenAPI oficial |
| C4 | El generador de XML sirve para boletas | El schema de boleta es **otro** (`EnvioBOLETA_v11.xsd`, autocontenido): Emisor con `RznSocEmisor`/`GiroEmisor` (no `RznSoc`/`GiroEmis`), **sin** `Acteco` ni `TasaIVA`, `IndServicio` **obligatorio** (3 = venta y servicios), `TmstFirma` obligatorio al cierre del Documento. La boleta actual sería schema-inválida. | XSD oficial |
| C5 | (P1-2) el RCOF acompaña a las boletas | El RVD/ex-RCOF **se eliminó como obligación desde el 01-08-2022** (Res. Ex. 53/2022); en el régimen actual cada boleta se informa individualmente por la API. El RCOF del proyecto queda como reporte interno no-enviable (correcto). | Res. 53/2022, noticias SII |
| C6 | "Los CAF de boleta vencen a los 6 meses" | Es al revés: vencen (6 meses desde `FA`, y el SII **rechaza en recepción**) los CAF de documentos con derecho a crédito fiscal — 33/43/46/56/61. Los de boleta 39/41 **no vencen**. | Res. Ex. 58/2017 |
| C7 | TED: bastaría firmar el DD serializado | El FRMT **no es XMLDSig**: es `base64(SHA1withRSA(bytes ISO-8859-1 del <DD> aplanado))`. La regla de aplanado es oficial (Instructivo A.2.4): sin EOL/blancos/tabs **entre** tags y sin referencias a namespaces; los valores terminales no se tocan. El `<CAF>` va embebido **tal como lo entregó el SII**. El SII verifica re-aplanando lo recibido. | Instructivo A.2.2–A.2.4 |

Al cerrar el sprint se actualiza la redacción de ROADMAP §2 (P0-4/P0-6) y §10.

## 3. Decisiones de diseño (congeladas)

- **D1 — Gateway real con DOS transportes, ruteados por tipo de documento.**
  Con CAF 39 y CAF 33 disponibles, `SiiGatewayProd` rutea: 39/41 → API REST de
  boleta (`SiiTransporteBoleta`); 33/34/56/61 → flujo clásico de DTE
  (`SiiTransporteDte`: semilla `CrSeed.jws` + token `GetTokenFromSeed.jws` en
  `maullin.sii.cl` (cert) / `palena.sii.cl` (prod), upload multipart
  `cgi_dte/UPL/DTEUpload` con sobre `EnvioDTE_v10`, estado por
  `QueryEstUp.jws`). Los dos canales tienen semilla/token **independientes**.
  El detalle fino del canal clásico (formatos SOAP exactos, campos del upload,
  códigos de estado y su mapeo) se pina con una verificación de fidelidad
  puntual al inicio de la Fase D — la investigación de este diseño profundizó el
  canal REST; del clásico confirmó hosts, sobres y separación de tokens.
  Emitir un tipo sin CAF cargado ya falla hoy con el mensaje de folios (sin
  cambios); el E2E de notas 56/61 y factura exenta 34 queda disponible si se
  timbran esos CAF en el mismo portal.
- **D2 — Algoritmos**: XMLDSig = C14N inclusive + `rsa-sha1` + digest `sha1` +
  `KeyInfo(KeyValue, X509Data)` en ese orden (ambos obligatorios), Reference
  enveloped con `URI="#<ID>"` (o `URI=""` para getToken). FRMT = `SHA1withRSA`
  sobre el DD aplanado en ISO-8859-1. Verificado empíricamente en el JDK 21 de
  Docker: firmar funciona; **validar** firmas SHA-1 propias (solo en tests)
  requiere `org.jcp.xml.dsig.secureValidation=false` en el contexto de validación.
- **D3 — Sin dependencias nuevas.** PKCS#12 y XMLDSig con el JDK; la clave PKCS#1
  del CAF se parsea con un decodificador DER mínimo propio (9 INTEGERs →
  `RSAPrivateCrtKeySpec`), ya probado contra el CAF real. HTTP con `RestClient`
  de Spring. PDF417 sigue con zxing.
- **D4 — El DTE se marshalla sin indentación** (una línea). Elimina de raíz los
  problemas de fidelidad byte-a-byte del TED y de la firma. RCOF/libros conservan
  el formato legible actual (`JaxbXml` gana la variante plana; la política de
  prólogo ISO-8859-1 no cambia).
- **D5 — El TED es un string aplanado, no un árbol JAXB.** `TedGenerator` produce
  el fragmento `<TED version="1.0"><DD>…</DD><FRMT…>…</FRMT></TED>` como string
  (valores XML-escapados, RSR/IT1 truncados a 40, `TSTED yyyy-MM-dd'T'HH:mm:ss`
  hora de Chile, `<CAF>` embebido **verbatim** como substring del XML del SII).
  Ese mismo string es la fuente única para (a) el documento (embebido como DOM
  renombrado al namespace `SiiDte` vía `@XmlAnyElement`, sin `xmlns` textual) y
  (b) el PDF417. La firma FRMT se calcula sobre el string plano sin namespaces —
  coherente con la regla de verificación del SII (C7).
- **D6 — Namespace oficial en todo el paquete `tributario`**: `package-info.java`
  con `@XmlSchema(namespace="http://www.sii.cl/SiiDte", elementFormDefault=QUALIFIED)`.
  DTE/EnvioBOLETA quedan alineados al oficial; RCOF y libros ganan el namespace
  (sus esquemas reales también son `SiiDte`) sin validación nueva.
- **D7 — XSD oficiales como única fuente de validación.** Se vendorean en
  `resources/sii/oficial/`: `SiiTypes_v10.xsd`, `xmldsignature_v10.xsd`,
  `DTE_v10.xsd`, `EnvioBOLETA_v11.xsd` (+ `EnvioDTE_v10.xsd` para el futuro), más
  un wrapper **local** de ~6 líneas (`BoletaDte-local.xsd`) que declara `DTE` de
  tipo `BOLETADefType` como elemento global (en `EnvioBOLETA_v11.xsd` el DTE de
  boleta es inline y no se puede validar suelto). El `DTE.xsd` representativo se
  elimina. Dos `Schema` compilados e independientes (factura / boleta) — nunca
  juntos, porque ambos definen tipos del mismo namespace.
- **D8 — La validación XSD pasa a ser post-firma** (el schema oficial exige la
  `Signature`): `emitir = generar (con TED real) → firmar → validar XSD → sello`,
  todo en la misma transacción — `DteInvalidoException` sigue revirtiendo el folio
  con 422. Para que el perfil dev no se rompa, `FirmaElectronicaStub` pasa a
  emitir una firma **falsa pero con forma schema-válida** (estructura completa,
  valores basura, mismo log de advertencia). El sobre EnvioBOLETA completo se
  valida además en el gateway antes de cada POST (aborta el envío con error claro
  si no cumple; el documento queda FIRMADO, sin pérdida de folio).
- **D9 — El PDF extrae el TED del XML firmado almacenado** (substring
  `<TED…</TED>`), nunca lo regenera (hoy lo regenera y con FRMT real duplicaría
  la firma y podría divergir del documento). Un BORRADOR sin XML usa el fallback
  textual existente ("sin timbre"). PDF417: byte compaction, **ECL nivel 5**,
  quiet zone — se ajusta el generador si hiciera falta (exigencia oficial A.2.5).
- **D10 — Carga de CAF por XML, sin campos manuales.** `CafRequest` pasa a ser
  solo el XML; todo lo demás (tipo, rango, fechas, claves) se deriva del parseo.
  Validaciones al cargar: estructura completa, `D<=H`, módulo RSASK == módulo
  RSAPK (+ firma/verificación de un vector de prueba), `RE == RUT de la empresa`
  (409 si no), TD soportado, duplicado exacto → 409, advertencia si IDK ∉ {100,300}
  y si el IDK no calza con `app.sii.ambiente`. `fechaVencimiento` se calcula
  (FA + 6 meses) **solo** para 33/43/46/56/61 (C6); al asignar folio, un CAF
  vencido se salta (no bloquea la carga de uno nuevo). Verificar la FRMA contra la
  clave pública del SII queda como follow-up (el SII no la publica formalmente).
- **D11 — Selección stub/real sigue siendo por perfil** (`!prod`/`prod`), como
  hasta ahora. El E2E de certificación corre con un override de compose
  (`docker-compose.cert.yml`) que activa `prod` + `CERTIFICACION`. El emisor del
  E2E es una empresa con RUT `78397017-1` (el RE del CAF) creada vía registro.
- **D12 — Config, no columnas.** `FchResol`/`NroResol`/RUT firmante son
  properties (`app.sii.*`); el certificado es único por instalación. Certificado
  y resolución **por empresa** (multi-tenant real) queda como follow-up explícito.
  Sin migraciones de BD en este sprint.

## 4. Contrato congelado

### 4.1 Interfaces del backend

```java
public interface FirmaElectronica {
    /** Firma el DTE: Reference al @ID del Documento, Signature último hijo de <DTE>. */
    String firmar(String xmlDte);
    /** Firma enveloped genérica: refId=null → URI="" (getToken); si no, URI="#refId" (SetDTE). */
    String firmarEnveloped(String xml, String refId);
}

public interface SiiGateway {
    String enviar(EnvioSii envio);                      // → TrackID
    EstadoEnvio consultarEstado(ConsultaSii consulta);
    record EnvioSii(String xmlFirmado, int tipoDte, long folio, String rutEmisor) {}
    record ConsultaSii(String trackId, String rutEmisor) {}
    enum EstadoEnvio { RECIBIDO, ACEPTADO, ACEPTADO_CON_REPARO, RECHAZADO }     // sin cambios
}

// FolioService: la asignación devuelve también el CAF de origen del folio.
public record FolioAsignado(long folio, Caf caf) {}
FolioAsignado siguienteFolio(Long empresaId, TipoDte tipoDte);

// CafService/CafDtos: alta solo con el XML del SII.
public record CafRequest(@NotBlank String xmlCaf) {}    // CafResponse no cambia

// folio.CafParser (nuevo): parseo + validación del AUTORIZACION.
public record CafData(String re, String rs, int td, long desde, long hasta,
                      LocalDate fa, int idk, RSAPublicKey clavePublica,
                      RSAPrivateKey clavePrivada, String cafXmlVerbatim) {}

// tributario.TedGenerator (reescrito): devuelve el TED aplanado listo.
String generar(DocumentoTributario doc, String rutEmisor, CafData caf);
```

`DocumentoService`: `intentarEnvio`/`consultarEstadoSii` cargan la `Empresa` para
poblar `rutEmisor`; `emitir` usa `FolioAsignado` para timbrar con el CAF exacto
del folio y exige `caf.re == emisor.rut` (409 defensivo). El resto del ciclo de
vida (estados, contingencia, reenvío, sello, inmutabilidad) **no cambia**.

### 4.2 Componentes nuevos

| Componente | Rol |
|---|---|
| `tributario/CertificadoDigital` (`@Profile("prod")`) | Carga el PKCS#12 (`app.sii.certificado-path/password`), expone clave/cert/`rutFirmante()` (SERIALNUMBER del subject, fallback `app.sii.rut-firmante`). Valida fail-fast al arrancar: existe, abre, vigente, tiene SERIALNUMBER (patrón `JwtSecretValidator`). |
| `tributario/FirmaElectronicaProd` (reescrito) | XMLDSig real con el JDK según D2. DOM namespace-aware, `setIdAttribute("ID", true)`, serialización ISO-8859-1 sin indentación. |
| `folio/CafParser` | D10. DOM endurecido (XXE), DER PKCS#1 propio, extracción verbatim del `<CAF>`. |
| `tributario/EnvioBoletaGenerator` | Arma el sobre: prólogo + `EnvioBOLETA version="1.0"` (xmlns SiiDte) + `SetDTE ID="SetDoc"` + Carátula (`RutEmisor`, `RutEnvia`=RUT firmante, `RutReceptor=60803000-K`, `FchResol`, `NroResol`, `TmstFirmaEnv=yyyy-MM-dd'T'HH:mm:ss`, `SubTotDTE{39,1}`) + el DTE firmado embebido **verbatim** (sin su declaración XML; conserva su propio xmlns) + firma del SetDTE (`firmarEnveloped(xml, "SetDoc")`). Valida el resultado contra `EnvioBOLETA_v11.xsd` antes de entregarlo. |
| `tributario/SiiAuthClient` | Semilla (GET, parsear `SII:RESPUESTA/SII:RESP_BODY/SEMILLA`, `ESTADO=0`; expira en 2 min) → `getToken` (XML de 2 líneas firmado con `URI=""`, POST `application/xml`) → token cacheado (TTL deslizante ~55 min; el SII lo renueva con cada uso). Ante 401 o cuerpo `NO ESTA AUTENTICADO`: renovar una vez y reintentar una vez. Thread-safe. Nunca loguea token/clave. |
| `tributario/SiiGatewayProd` (reescrito) | D1. Rutea por tipo al transporte que corresponda y unifica el contrato de errores: errores de transporte (conexión/timeout/5xx) → `SiiNoDisponibleException` (contingencia intacta); 4xx de negocio → excepción dura con el detalle. |
| `tributario/SiiTransporteBoleta` | 39/41. `enviar`: sobre EnvioBOLETA → `POST multipart/form-data` (campos `rutSender`/`dvSender` = RUT firmante, `rutCompany`/`dvCompany` = empresa, `archivo` = XML ISO-8859-1) con `Cookie: TOKEN=…`, `User-Agent` configurado y `Accept: application/json`; éxito = HTTP 200 → `trackid`. `consultarEstado`: `GET …/boleta.electronica.envio/{rut}-{dv}-{trackid}`. |
| `tributario/SiiTransporteDte` (Fase D) | 33/34/56/61. Semilla `CrSeed.jws` → token `GetTokenFromSeed.jws` (misma semilla firmada `URI=""` que el canal REST, transporte SOAP) → sobre `EnvioDTE_v10` (misma carátula + `SubTotDTE`; hasta 2000 DTE, aquí 1) → upload `cgi_dte/UPL/DTEUpload` (multipart con cookie `TOKEN`) → estado `QueryEstUp.jws` por TrackID. Formatos exactos y mapeo de estados se pinan con la verificación de fidelidad de la Fase D. |
| `tributario/EnvioDteGenerator` (Fase D) | Sobre `EnvioDTE` para facturas/notas, espejo del `EnvioBoletaGenerator` (validado contra `EnvioDTE_v10.xsd`, ya vendoreado). |

### 4.3 Endpoints del SII (constantes internas, seleccionadas por `app.sii.ambiente`)

| Operación | CERTIFICACION | PRODUCCION |
|---|---|---|
| Semilla | `GET https://apicert.sii.cl/recursos/v1/boleta.electronica.semilla` | `GET https://api.sii.cl/recursos/v1/boleta.electronica.semilla` |
| Token | `POST https://apicert.sii.cl/recursos/v1/boleta.electronica.token` | `POST https://api.sii.cl/recursos/v1/boleta.electronica.token` |
| Envío | `POST https://pangal.sii.cl/recursos/v1/boleta.electronica.envio` | `POST https://rahue.sii.cl/recursos/v1/boleta.electronica.envio` |
| Estado envío | `GET https://apicert.sii.cl/recursos/v1/boleta.electronica.envio/{rut}-{dv}-{trackid}` | ídem en `api.sii.cl` |

Canal clásico de DTE (facturas/notas — hosts confirmados; paths a re-verificar en
la Fase D): semilla/token/estado en `https://maullin.sii.cl/DTEWS/…`
(`CrSeed.jws`, `GetTokenFromSeed.jws`, `QueryEstUp.jws`) y upload en
`https://maullin.sii.cl/cgi_dte/UPL/DTEUpload` para certificación; en producción
los mismos paths sobre `palena.sii.cl`. TrackID clásico: 10 dígitos (el de
boleta: 15).

Mapeo de estados (nuestros envíos llevan **1 boleta**, así que `estadistica`
decide 1:1): `REC/SOK/CRT/FOK/PRD` → `RECIBIDO`; `EPR` → `ACEPTADO` si
`rechazados=0 ∧ reparos=0`, `ACEPTADO_CON_REPARO` si `reparos>0`, `RECHAZADO` si
`rechazados>0`; `RPR` → `ACEPTADO_CON_REPARO`; `RSC/RCH/RCO/RPT/RFR/VOF/RCT` →
`RECHAZADO`. El detalle por folio (`detalle_rep_rech`, sección/código de error)
se registra en el log y en `ultimoErrorEnvio` cuando aplica.

### 4.4 XML del DTE (cambios de forma)

- **Boleta 39/41** (rama nueva del `XmlDteGenerator`, espejo del schema oficial):
  `IdDoc{TipoDTE,Folio,FchEmis,IndServicio=3}`;
  `Emisor{RUTEmisor,RznSocEmisor,GiroEmisor,DirOrigen?,CmnaOrigen?}` (sin Acteco);
  `Receptor{RUTRecep,RznSocRecep?,…}` (66666666-6 para consumidor final);
  `Totales{MntNeto?,MntExe?,IVA?,MntTotal}` — **sin `TasaIVA`**; en la 39 afecta
  van `MntNeto+IVA` (obligatorios por Formato), en la 41 solo `MntExe+MntTotal`;
  `Detalle{NroLinDet,NmbItem,QtyItem?,UnmdItem?,PrcItem?,DescuentoMonto?,IndExe?,MontoItem}`;
  `Referencia` de boleta (opcional, sin `FchRef`); `TED`; **`TmstFirma`** al cierre.
- **Factura/notas 33/56/61** (alineamiento al oficial): se agregan
  **`Acteco`** (de `empresa.actividadEconomica`; si falta → 409 con mensaje) y
  **`TmstFirma`**; se exige por regla de negocio lo que el Formato marca
  obligatorio y hoy puede venir nulo del cliente: `GiroRecep`, `DirRecep`,
  `CmnaRecep` (409 al emitir 33/56/61 si faltan). `TasaIVA`/`ImptoReten`/
  `Referencia` quedan como están (ya eran fieles).
- RUT siempre normalizado `NNNNNNNN-DV` con K mayúscula (patrón del schema).

### 4.5 Configuración

```yaml
app:
  sii:
    ambiente: CERTIFICACION | PRODUCCION          # existente
    certificado-path / certificado-password        # existentes (compose ya los inyecta)
    rut-firmante: ${APP_SII_RUT_FIRMANTE:}         # fallback si el subject no trae SERIALNUMBER
    fch-resol:    ${APP_SII_FCH_RESOL:}            # fecha de resolución; en cert: la de "Datos de la Empresa"
    nro-resol:    ${APP_SII_NRO_RESOL:0}           # 0 fijo en certificación
    user-agent:   ${APP_SII_USER_AGENT:Mozilla/4.0 (compatible; PROG 1.0)}
```

`docker-compose.cert.yml` (nuevo, override): `SPRING_PROFILES_ACTIVE=prod`,
`APP_SII_AMBIENTE=CERTIFICACION`, `APP_JWT_SECRET` real y `APP_SII_FCH_RESOL`
desde `.env`. Sin cambios en el compose base (dev sigue 100% stub).

### 4.6 Frontend

- **Folios**: el alta de CAF pasa a subir/pegar el **XML** (un campo); tipo,
  rango y fechas se muestran del response, ya no se digitan. Tipos
  `CafRequest`/`api.ts` espejados al backend.
- Sin más cambios de pantalla: emitir/enviar/estado/PDF ya operan por estado del
  documento y la contingencia existente cubre la caída del SII real.

## 5. Plan de verificación

| Gate | Contenido |
|---|---|
| Unitarios nuevos (~30) | `CafParser` (CAF sintético propio: estructura, DER, coherencia de claves, verbatim, RE/TD/rango, vencimiento por tipo); `TedGenerator` (aplanado, escaping, truncado 40, TSTED, **FRMT verificado con la clave pública del CAF**); `FirmaElectronicaProd` (firma→validación con secure-validation off; algoritmos/orden KeyInfo/URI afirmados sobre el DOM); `EnvioBoletaGenerator` (carátula + sobre completo **válido contra EnvioBOLETA_v11.xsd**); `SiiAuthClient`/`SiiGatewayProd` (parseo semilla/token, mapeo de estados y errores → contingencia) con servidor HTTP mockeado; `XmlDteGeneratorXsd` migrado al XSD oficial (boleta 39/41 y factura 33 con Acteco/TmstFirma válidas). |
| Fixtures | CAF **sintético** generado por nosotros y P12 **dummy** (`keytool`, clave "test") committeados. Los activos reales de `secrets/` jamás entran al repo ni a los tests del CI; si están presentes localmente, un test opcional (`@EnabledIf`) verifica el CAF real. |
| Suite existente | 149 unitarios siguen verdes (los de XSD/generador se adaptan al nuevo orden post-firma y al namespace). ITs (Testcontainers) compilan; corren en CI como siempre. |
| Build | `docker compose build` (backend + frontend `tsc`). |
| **E2E certificación** (gate real del sprint) | Runbook: `docker compose -f docker-compose.yml -f docker-compose.cert.yml up -d` → registrar cuenta/empresa RUT `78397017-1` → cargar `secrets/caf_39_certificacion.xml` y `secrets/caf_factura.xml` → **boleta 39**: emitir → enviar (TrackID real de pangal) → estado hasta `ACEPTADO`; **factura 33** (cliente con giro/dirección/comuna): emitir → enviar (TrackID de maullin) → estado hasta `ACEPTADO`/`REPARO` (diagnóstico con el detalle del SII en ambos canales). Presupuesto de folios: 100 de boleta y 50 de factura — apuntar a ≤10 por sesión; el CAF 33 vence el 2026-10-28. `APP_SII_FCH_RESOL=2026-05-14` ya confirmada en el portal (Res. 0). |

## 6. Riesgos y mitigaciones

- **Rechazos iniciales en cert (RSC/RFR/RCT)**: el diseño valida schema y firma
  localmente antes de enviar (D7/D8), y el `detalle_rep_rech` del SII da
  sección/línea/código exactos para iterar. Presupuesto de folios controlado.
- **Deriva byte-a-byte del TED**: eliminada por construcción (D4/D5: una sola
  fuente aplanada; FRMT verificable localmente con la clave pública del CAF).
- **Empresa no habilitada en el ambiente cert**: si el envío devuelve
  `NO ESTA AUTENTICADO`/`RCT` persistente pese a token fresco, el bloqueo es
  administrativo (habilitación/`FchResol` incorrecta), no de código — el runbook
  lo distingue.
- **JDK futuro endureciendo SHA-1**: solo afectaría la *validación* local en
  tests (ya aislada tras un flag); la firma no está restringida y el SII fija
  SHA-1 por schema.

## 7. Fuera de alcance (follow-ups documentados)

1. E2E de notas 56/61 y factura exenta 34 — el transporte clásico las cubre;
   solo falta timbrar sus CAF de certificación en el portal (mismo trámite del 33).
2. Certificado digital y resolución **por empresa** (multi-tenant real).
3. Verificación de la FRMA del CAF contra la clave pública del SII (IDK 100/300).
4. Set de pruebas formal de certificación → autorización de producción (proceso
   administrativo con muestras impresas y declaración; excede al software).
5. `MedioPago` en boletas > 135 UF y `GeoRefEmision` (opcionales del schema).

## 8. Fases de implementación (método de los sprints anteriores)

1. **Fase A (P0-5)**: `CafParser` + alta de CAF por XML + `TedGenerator` real +
   `FolioAsignado` + PDF con TED extraído. Verificable offline (FRMT contra la
   clave pública del CAF real).
2. **Fase B (P0-4)**: namespace SiiDte + rama boleta del generador + Acteco/
   TmstFirma en factura + XSD oficiales + `CertificadoDigital` +
   `FirmaElectronicaProd` real + stub con forma válida + orden post-firma.
   Verificable offline (XSD oficial + validación local de la firma).
3. **Fase C (P0-6 boleta)**: `SiiAuthClient` + `EnvioBoletaGenerator` +
   `SiiGatewayProd`/`SiiTransporteBoleta` + config/compose cert + frontend de
   Folios. Verificable online (E2E cert de boleta).
4. **Fase D (P0-6 factura)**: verificación de fidelidad puntual del canal clásico
   (SOAP semilla/token, campos del `DTEUpload`, códigos de `QueryEstUp` y su
   mapeo a `EstadoEnvio`) → `SiiTransporteDte` + `EnvioDteGenerator`.
   Verificable online (E2E cert de factura 33, antes del 2026-10-28).
5. **Review multi-ángulo en paralelo** del diff completo (línea a línea, fidelidad
   SII contra los XSD/spec vendoreados, seguridad de secretos, comportamiento
   eliminado, trazado cross-file, reuso/altitud) → correcciones → gate de build
   en Docker → commit.
