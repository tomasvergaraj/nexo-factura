-- =====================================================================
-- P2-5: contingencia de envio al SII + libro de compras
-- =====================================================================

-- Traza del envio al SII: intentos, timestamp del ultimo intento y motivo
-- del ultimo fallo (contingencia). Aditiva: los documentos existentes quedan
-- con 0 intentos registrados.
ALTER TABLE documento_tributario
    ADD COLUMN intentos_envio     INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN ultimo_envio_en    TIMESTAMPTZ,
    ADD COLUMN ultimo_error_envio TEXT;

-- Documentos tributarios RECIBIDOS (compras), registrados manualmente para
-- construir el libro de compras (IECV). Los montos son enteros CLP.
CREATE TABLE documento_compra (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id    BIGINT       NOT NULL REFERENCES empresa (id),
    tipo_dte      INTEGER      NOT NULL,
    folio         BIGINT       NOT NULL,
    rut_proveedor VARCHAR(12)  NOT NULL,
    razon_social  VARCHAR(255) NOT NULL,
    fecha_emision DATE         NOT NULL,
    neto          BIGINT       NOT NULL,
    exento        BIGINT       NOT NULL,
    iva           BIGINT       NOT NULL,
    -- IVA retenido por el comprador (cambio de sujeto, tipico de la factura de
    -- compra 46): se declara aparte y RESTA del total pagado al proveedor.
    iva_retenido  BIGINT       NOT NULL DEFAULT 0,
    total         BIGINT       NOT NULL,
    observacion   TEXT,
    creado_en     TIMESTAMPTZ  NOT NULL,
    -- Identidad tributaria del documento recibido: un proveedor no puede
    -- aparecer dos veces con el mismo tipo y folio (duplicado -> 409).
    CONSTRAINT uq_compra_identidad UNIQUE (empresa_id, tipo_dte, folio, rut_proveedor)
);

CREATE INDEX idx_compra_empresa_fecha ON documento_compra (empresa_id, fecha_emision);
