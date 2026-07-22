-- Set de pruebas de certificacion del SII, libro de compras: IVA uso comun
-- (factor de proporcionalidad) e IVA no recuperable (p.ej. entrega gratuita,
-- codigo 4). Nulos/false = compra normal con derecho a credito.

alter table documento_compra add column iva_uso_comun boolean not null default false;

alter table documento_compra add column cod_iva_no_rec integer;
