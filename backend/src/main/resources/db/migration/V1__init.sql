-- =====================================================================
-- Nexo Factura - Esquema inicial
-- Facturacion electronica (DTE) para el SII de Chile
-- =====================================================================

CREATE TABLE empresa (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rut                  VARCHAR(12)  NOT NULL UNIQUE,
    razon_social         VARCHAR(255) NOT NULL,
    giro                 VARCHAR(255) NOT NULL,
    actividad_economica  INTEGER,
    direccion            VARCHAR(255) NOT NULL,
    comuna               VARCHAR(120) NOT NULL,
    ciudad               VARCHAR(120),
    telefono             VARCHAR(40),
    email                VARCHAR(180),
    creado_en            TIMESTAMPTZ  NOT NULL
);

CREATE TABLE usuario (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre        VARCHAR(180) NOT NULL,
    email         VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol           VARCHAR(20)  NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    empresa_id    BIGINT       REFERENCES empresa (id),
    creado_en     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE cliente (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id   BIGINT       NOT NULL REFERENCES empresa (id),
    rut          VARCHAR(12)  NOT NULL,
    razon_social VARCHAR(255) NOT NULL,
    giro         VARCHAR(255),
    direccion    VARCHAR(255),
    comuna       VARCHAR(120),
    ciudad       VARCHAR(120),
    email        VARCHAR(180),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_cliente_empresa_rut UNIQUE (empresa_id, rut)
);

CREATE TABLE producto (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id  BIGINT       NOT NULL REFERENCES empresa (id),
    codigo      VARCHAR(60),
    nombre      VARCHAR(255) NOT NULL,
    precio_neto BIGINT       NOT NULL,
    unidad      VARCHAR(20)  NOT NULL DEFAULT 'UN',
    afecto      BOOLEAN      NOT NULL DEFAULT TRUE,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_producto_empresa_codigo UNIQUE (empresa_id, codigo)
);

CREATE TABLE caf (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id         BIGINT      NOT NULL REFERENCES empresa (id),
    tipo_dte           VARCHAR(20) NOT NULL,
    folio_desde        BIGINT      NOT NULL,
    folio_hasta        BIGINT      NOT NULL,
    folio_actual       BIGINT      NOT NULL,
    xml_caf            TEXT,
    fecha_autorizacion DATE,
    fecha_vencimiento  DATE,
    agotado            BOOLEAN     NOT NULL DEFAULT FALSE,
    version            BIGINT,
    creado_en          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_caf_empresa_tipo ON caf (empresa_id, tipo_dte, agotado);

CREATE TABLE documento_tributario (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id            BIGINT       NOT NULL REFERENCES empresa (id),
    tipo_dte              VARCHAR(20)  NOT NULL,
    folio                 BIGINT,
    estado                VARCHAR(20)  NOT NULL,
    fecha_emision         DATE         NOT NULL,
    receptor_rut          VARCHAR(12)  NOT NULL,
    receptor_razon_social VARCHAR(255) NOT NULL,
    receptor_giro         VARCHAR(255),
    receptor_direccion    VARCHAR(255),
    receptor_comuna       VARCHAR(120),
    neto                  BIGINT       NOT NULL,
    exento                BIGINT       NOT NULL,
    tasa_iva              DOUBLE PRECISION NOT NULL,
    iva                   BIGINT       NOT NULL,
    total                 BIGINT       NOT NULL,
    xml_dte               TEXT,
    track_id              VARCHAR(60),
    observacion           TEXT,
    creado_en             TIMESTAMPTZ  NOT NULL,
    actualizado_en        TIMESTAMPTZ,
    CONSTRAINT uq_documento_folio UNIQUE (empresa_id, tipo_dte, folio)
);

CREATE INDEX idx_documento_empresa_estado ON documento_tributario (empresa_id, estado);
CREATE INDEX idx_documento_empresa_fecha ON documento_tributario (empresa_id, fecha_emision);

CREATE TABLE linea_detalle (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento_id    BIGINT       NOT NULL REFERENCES documento_tributario (id) ON DELETE CASCADE,
    numero_linea    INTEGER      NOT NULL,
    producto_id     BIGINT,
    nombre          VARCHAR(255) NOT NULL,
    cantidad        DOUBLE PRECISION NOT NULL,
    unidad          VARCHAR(20)  NOT NULL,
    precio_unitario BIGINT       NOT NULL,
    descuento_monto BIGINT       NOT NULL DEFAULT 0,
    afecto          BOOLEAN      NOT NULL DEFAULT TRUE,
    monto_linea     BIGINT       NOT NULL
);

CREATE INDEX idx_linea_documento ON linea_detalle (documento_id);

CREATE TABLE referencia (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento_id       BIGINT       NOT NULL REFERENCES documento_tributario (id) ON DELETE CASCADE,
    tipo_documento_ref INTEGER      NOT NULL,
    folio_ref          BIGINT       NOT NULL,
    fecha_ref          DATE         NOT NULL,
    tipo_referencia    VARCHAR(20)  NOT NULL,
    razon              VARCHAR(255) NOT NULL
);

CREATE INDEX idx_referencia_documento ON referencia (documento_id);
