-- URL del sitio publico de consulta de boletas electronicas.
--
-- El Formato de Boletas Electronicas del SII (v2.0, pag. 5) exige que el emisor
-- publique las boletas emitidas en un sitio web, disponibles para consulta por
-- los clientes durante TRES MESES desde la emision, y que ese sitio quede
-- "senalado en la representacion impresa como una leyenda bajo el timbre
-- electronico", con la forma "Verifique documento: <url>". El correo de
-- certificacion de boletas lo reitera: el sitio debe existir "previa aprobacion
-- a su certificacion".
--
-- Nullable a proposito: mientras este vacia, el PDF conserva la leyenda
-- generica "www.sii.cl" (correcta para facturas, que si se verifican en el
-- SII) y el endpoint publico de consulta responde como si la boleta no
-- existiera. Configurarla es lo que "enciende" el sitio para la empresa.
ALTER TABLE empresa
    ADD COLUMN url_consulta_boleta VARCHAR(120);

COMMENT ON COLUMN empresa.url_consulta_boleta IS
    'URL publica de consulta de boletas, impresa bajo el timbre; null = sin sitio configurado';
