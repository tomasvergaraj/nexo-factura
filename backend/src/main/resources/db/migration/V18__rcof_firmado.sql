-- Registro de los RCOF (ConsumoFolios) firmados por empresa y dia.
--
-- No es un registro de ENVIOS: la Res. Ex. SII N°53 de 2022 elimino la
-- obligacion de remitir el consumo de folios, y lo que el sistema produce es un
-- archivo firmado que el usuario adjunta al correo de certificacion de boletas.
-- El sistema no puede saber si ese correo se envio, asi que la tabla dice
-- exactamente lo que ocurrio: se genero el archivo N del dia D.
--
-- De aca sale SecEnvio, que el esquema define como 1 la primera vez y +1 en cada
-- correccion del mismo periodo: la secuencia propuesta es la ultima del dia mas
-- uno. Sin UNIQUE sobre (empresa, fecha, sec_envio) a proposito: regenerar el
-- MISMO numero es un caso legitimo —el archivo anterior nunca se presento, o el
-- SII pide uno concreto— y cada fila registra una generacion que de verdad
-- ocurrio, no un numero reservado.
CREATE TABLE rcof_firmado (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id BIGINT      NOT NULL REFERENCES empresa (id),
    fecha      DATE        NOT NULL,          -- dia reportado
    sec_envio  INTEGER     NOT NULL,          -- 1..999 (totalDigits=3 en el XSD)
    tmst_firma TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_rcof_firmado_empresa_fecha
    ON rcof_firmado (empresa_id, fecha, sec_envio DESC);
