-- =====================================================================
-- Robustez (P2-4)
--  - Bloqueo optimista (@Version) en datos maestros: empresa, cliente, producto.
--    (caf ya tenia version desde V1). DEFAULT 0 + NOT NULL retrocompatibiliza
--    las filas existentes.
--  - Sello de integridad del DTE: SHA-256 (hex, 64 chars) del XML firmado, fijado
--    al emitir. Permite detectar manipulacion posterior del XML almacenado.
-- =====================================================================

ALTER TABLE empresa  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cliente  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE producto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE documento_tributario ADD COLUMN sello VARCHAR(64);
