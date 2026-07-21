-- Desde el Sprint 6 el timbrado (TED) exige el XML del CAF: un CAF sin xml_caf
-- puede asignar folios pero no timbrar, dejando la emision bloqueada a mitad de
-- camino. Se marcan agotados para que la UI los muestre inutilizables y el
-- selector de folios (que ademas filtra xml_caf IS NOT NULL) los salte.
-- Afecta a los CAF de demostracion del seed dev y a cargas previas al Sprint 6;
-- un CAF real se recarga desde Folios con el XML descargado del SII.
UPDATE caf SET agotado = TRUE WHERE xml_caf IS NULL;
