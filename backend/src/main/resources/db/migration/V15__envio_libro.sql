-- Registro de los envios del libro IECV al SII.
--
-- Cada fila es un intento de envio del libro de un periodo (POST del canal
-- clasico): el SII responde con un TrackID, y el estado real (RECIBIDO /
-- ACEPTADO / ACEPTADO_CON_REPARO / RECHAZADO) se resuelve despues por QueryEstUp.
-- Por eso 'estado' arranca nulo (aun sin consultar) y se actualiza bajo demanda.
--
-- tipo_libro y folio_notificacion existen porque el envio del set de pruebas
-- viaja como ESPECIAL con el numero de atencion; el envio mensual normal va como
-- MENSUAL sin folio.
CREATE TABLE envio_libro (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id         BIGINT       NOT NULL REFERENCES empresa (id),
    periodo            VARCHAR(7)   NOT NULL,          -- YYYY-MM
    tipo_operacion     VARCHAR(10)  NOT NULL,          -- VENTA | COMPRA
    track_id           VARCHAR(40)  NOT NULL,
    estado             VARCHAR(24),                    -- EstadoEnvio; null hasta la 1a consulta
    tipo_libro         VARCHAR(12)  NOT NULL,          -- MENSUAL | ESPECIAL
    folio_notificacion BIGINT,
    tmst_envio         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_envio_libro_empresa_periodo_tipo
    ON envio_libro (empresa_id, periodo, tipo_operacion, tmst_envio DESC);
