-- Marcador de un libro IECV pendiente de envio, resultado de la revision
-- automatica mensual (RevisionLibroJob).
--
-- El job corre desde el dia configurado del mes y, por cada empresa que puede
-- firmar, PREPARA el libro del mes anterior con movimiento y sin envio ya
-- gestionado: lo firma y valida contra el esquema SIN postearlo al SII. El
-- resultado queda aqui como PREPARADO (listo para que el usuario apriete
-- "Enviar") o ERROR (con el motivo), de modo que la UI avise temprano.
--
-- Hay a lo sumo un marcador por (empresa, periodo, operacion): el job hace
-- upsert en cada corrida, asi el estado siempre refleja la ultima revision.
CREATE TABLE libro_pendiente (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id     BIGINT       NOT NULL REFERENCES empresa (id),
    periodo        VARCHAR(7)   NOT NULL,          -- YYYY-MM
    tipo_operacion VARCHAR(10)  NOT NULL,          -- VENTA | COMPRA
    estado         VARCHAR(12)  NOT NULL,          -- PREPARADO | ERROR
    detalle        TEXT,                           -- motivo del ERROR; null si PREPARADO
    tmst_revision  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_libro_pendiente UNIQUE (empresa_id, periodo, tipo_operacion)
);

CREATE INDEX idx_libro_pendiente_empresa ON libro_pendiente (empresa_id);
