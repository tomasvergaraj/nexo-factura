# Runbook — Puesta en producción (NEXO SOFTWARE SPA)

Emisor: **Nexo Software SpA**, RUT **78397017-1**.
Autorización SII: **Resolución Ex. N°80 del 22/08/2014**. Servidor: **palena.sii.cl**.

Ambiente de producción: `docker-compose.prod.yml` (perfil `prod`, `APP_SII_AMBIENTE=PRODUCCION`,
`APP_SII_FIRMA_MODO=POR_EMPRESA`, DB propia `nexo_factura_prod`). Corre en paralelo al de
certificación: backend host **:8083**, frontend **:8084**, DB aislada (sin puerto al host).

> La DB de producción arranca **limpia**: el seed de demostración (`V2__seed_dev.sql`) vive en
> `classpath:db/seed-dev` y solo se carga en perfil `dev`. En prod no hay empresa/usuario/CAF de prueba.

---

## 0. Prerrequisitos (una sola vez)

`.env.prod` (gitignoreado) con los tres secretos rellenos:

```
APP_MASTER_KEY=<openssl rand -base64 32>   # AES-256, base64 de 32 bytes. NO la de dev.
APP_JWT_SECRET=<openssl rand -base64 48>   # >=32 bytes, distinto del de dev.
DB_PASSWORD=<openssl rand -hex 24>         # hex, seguro para JDBC.
```

> ⚠️ **Respaldar `APP_MASTER_KEY` fuera del repo** (gestor de secretos). Cifra los CAF y el
> PKCS#12 en la BD; si se pierde, quedan **indescifrables sin recuperación**.

---

## 1. Levantar el stack

```bash
docker compose -p nexo-prod --env-file .env.prod -f docker-compose.prod.yml up -d --build
# esperar salud del backend (health público de actuator vía el front):
curl -fsS http://localhost:8084/actuator/health   # {"status":"UP"}
```

---

## 2. Sembrar la empresa Nexo Software SpA (con su resolución)

La empresa se inserta por SQL (datos públicos del emisor + resolución 80 / 2014-08-22). En la
BD fresca toma **id 1**.

```bash
docker exec -i nexo-factura-db-prod psql -U nexo -d nexo_factura_prod <<'SQL'
INSERT INTO empresa (rut, razon_social, giro, actividad_economica, direccion, comuna, ciudad,
                     telefono, email, unidad_sii, fch_resol, nro_resol, creado_en)
VALUES ('78397017-1', 'Nexo Software SpA', 'Desarrollo de software y servicios informaticos',
        620200, 'Calle Ejemplo 123', 'Quillota', 'Quillota', '+56 9 8196 4119',
        'contacto@nexosoftware.cl', 'S.I.I. - VALPARAISO',
        DATE '2014-08-22', 80, now());
SELECT id, rut, razon_social, nro_resol, fch_resol FROM empresa;
SQL
```

> Revisar `direccion`/`comuna`/`ciudad`/`unidad_sii`/`giro`/`actividad_economica` con los datos
> reales del emisor antes de emitir (van en la carátula y la representación impresa). Se pueden
> ajustar luego con `PUT /api/empresas/1`.

---

## 3. Crear el primer usuario admin y vincularlo a la empresa

El endpoint `registro` hace el bcrypt nativo del backend (hash compatible), pero crea el admin
**sin empresa**; se vincula por SQL. Después hay que **volver a loguear** para que el JWT lleve
el claim `empresaId`.

```bash
# 3a. Registrar el admin (bcrypt lo hace el backend)
curl -fsS -X POST http://localhost:8083/api/auth/registro \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Administrador","email":"contacto@nexosoftware.cl","password":"<PASSWORD_ADMIN>"}'

# 3b. Vincular el usuario a la empresa 1
docker exec -i nexo-factura-db-prod psql -U nexo -d nexo_factura_prod \
  -c "UPDATE usuario SET empresa_id = 1 WHERE email = 'contacto@nexosoftware.cl';"

# 3c. Login (ya con empresa) -> guardar el token
TOKEN=$(curl -fsS -X POST http://localhost:8083/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"contacto@nexosoftware.cl","password":"<PASSWORD_ADMIN>"}' | jq -r .token)
echo "$TOKEN"   # debe traer empresaId=1 en el claim
```

---

## 4. Subir el PKCS#12 de producción de la empresa

```bash
curl -fsS -X POST http://localhost:8083/api/empresas/1/certificado \
  -H "Authorization: Bearer $TOKEN" \
  -F "archivo=@secrets/<certificado_prod>.pfx" \
  -F "password=<CLAVE_DEL_PFX>"
# -> 201 con metadata (firmante, vigencia, huella). El material queda cifrado en BD.
```

---

## 5. Cargar los 4 CAF de producción

CAF timbrados en `secrets/set_prod/` (33: 4-19, 34: 1-16, 61: 1-3, 56: 1-3).

```bash
for f in caf_factura_afecta_prod caf_factura_exenta_prod caf_nota_credito_prod caf_nota_debito_prod; do
  jq -Rs '{xmlCaf: .}' "secrets/set_prod/$f.xml" | \
  curl -fsS -X POST http://localhost:8083/api/empresas/1/folios \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @-
  echo " <- $f"
done
curl -fsS http://localhost:8083/api/empresas/1/folios -H "Authorization: Bearer $TOKEN" | jq .
```

---

## 6. Emisión de humo y verificación

1. Emitir un DTE real de monto bajo (factura 33) a un cliente real (o a la propia empresa).
2. Confirmar `estado-sii` = ACEPTADO (canal palena).
3. Verificar en el portal: palena.sii.cl → *Consulta estado de un envío* / *Consultar validez de un documento*.

---

## Notas operativas

- **Backups**: volumen `nexo_db_prod_data` + `.env.prod` (sobre todo `APP_MASTER_KEY`).
- **Libros IECV**: obligación mensual del SII (último día del mes, período anterior). No enviarlos
  puede restringir la descarga de nuevos CAF.
- **Logs**: `docker logs -f nexo-factura-backend-prod`.
- **Bajar el stack** (sin borrar datos): `docker compose -p nexo-prod -f docker-compose.prod.yml down`.
  Con `-v` **borra la DB** — no usar en producción.
- Convive con cert: cert = proyecto `nexo-factura` (:8082), prod = proyecto `nexo-prod` (:8083/:8084).
