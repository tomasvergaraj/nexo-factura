-- Direccion Regional / Unidad del SII del emisor. El Manual de Muestras Impresas
-- (1.1.4) exige rotularla bajo el recuadro del tipo de documento en la
-- representacion impresa. Nullable: las empresas existentes la completan luego.
ALTER TABLE empresa ADD COLUMN unidad_sii varchar(255);
