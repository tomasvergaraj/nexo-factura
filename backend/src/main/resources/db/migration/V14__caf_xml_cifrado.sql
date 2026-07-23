-- Cifrado en reposo del XML del CAF.
--
-- El CAF trae la clave privada RSA del rango de folios (<RSASK>), con la que se
-- calcula el FRMT del timbre: en texto plano, un volcado de esta tabla alcanza
-- para emitir DTE timbrados a nombre del contribuyente. Desde aqui la columna
-- guarda 'enc:v1:<base64>' del blob AES-256-GCM de CifradorSecretos (la misma
-- clave maestra de entorno que protege los PKCS#12 de certificado_empresa).
--
-- No hay cambio de tipo: el ciphertext en base64 sigue siendo TEXT y las filas
-- previas en claro se reconocen por la ausencia del prefijo. Convertirlas exige
-- la clave maestra, que SQL no tiene, asi que las migra CafCifradoBackfill al
-- arrancar la aplicacion.
COMMENT ON COLUMN caf.xml_caf IS
    'XML del CAF cifrado en reposo: enc:v1:<base64 de [version][iv][ciphertext+tag] AES-256-GCM>. '
    'Requiere APP_MASTER_KEY para leerse; las filas legacy en claro las migra CafCifradoBackfill.';
