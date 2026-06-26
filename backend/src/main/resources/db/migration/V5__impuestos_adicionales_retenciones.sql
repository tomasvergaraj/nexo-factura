-- =====================================================================
-- Impuestos adicionales y retenciones (P1-6)
--  - Otros impuestos del DTE: adicionales (ILA, suntuarios...) que SUBEN el
--    total y la retencion de IVA (cambio de sujeto) que lo RESTA. El catalogo de
--    codigos/tasas es un enum representativo en el codigo (TipoImpuesto), no una
--    tabla: por eso esta migracion NO crea tabla de catalogo.
--  - linea_detalle.cod_imp_adic: codigo del otro impuesto de la linea (nullable;
--    null = solo IVA estandar, comportamiento actual). Solo aplica a lineas
--    afectas de documentos de precios netos (facturas/notas).
--  - documento_tributario: dos montos agregados inmutables (como neto/iva/total),
--    NOT NULL DEFAULT 0 para retrocompatibilizar las filas existentes.
-- Migracion ADITIVA: documentos y lineas previos quedan con cod_imp_adic NULL y
-- agregados 0 (sin cambio de comportamiento).
-- =====================================================================

ALTER TABLE linea_detalle ADD COLUMN cod_imp_adic INTEGER;

ALTER TABLE documento_tributario ADD COLUMN impuestos_adicionales BIGINT NOT NULL DEFAULT 0;
ALTER TABLE documento_tributario ADD COLUMN iva_retenido          BIGINT NOT NULL DEFAULT 0;
