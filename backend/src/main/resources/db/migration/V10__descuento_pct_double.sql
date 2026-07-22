-- Hibernate valida los campos Double como double precision; V8 los creo como
-- numeric(5,2) y el arranque fallaba en schema-validation. El rango/escala del
-- porcentaje ya lo garantiza la aplicacion (normalizarPct: 2 decimales, 0-100).

alter table linea_detalle alter column descuento_pct type double precision;

alter table documento_tributario alter column descuento_global_pct type double precision;
