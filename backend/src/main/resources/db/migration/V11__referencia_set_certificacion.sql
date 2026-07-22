-- Referencia al set de pruebas de certificacion del SII: numero de caso
-- (ej: 4965879-1) que el DTE debe referenciar con TpoDocRef=SET para que el
-- revisor asocie el documento al caso. Null = emision normal.

alter table documento_tributario add column set_caso varchar(18);
