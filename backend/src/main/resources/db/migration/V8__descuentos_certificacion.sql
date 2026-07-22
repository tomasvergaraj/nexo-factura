-- Set de pruebas de certificacion del SII: descuento porcentual por linea
-- (DescuentoPct, casos basico-2/-6) y descuento global sobre afectos
-- (DscRcgGlobal, caso basico-4). Nulos = sin descuento porcentual.

alter table linea_detalle add column descuento_pct numeric(5,2);

alter table documento_tributario add column descuento_global_pct numeric(5,2);
