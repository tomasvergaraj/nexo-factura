-- Multi-tenant del certificado digital y de la resolucion SII.
--
-- 1) Resolucion por empresa: numero y fecha de la resolucion que autoriza al
--    contribuyente como emisor electronico (caratulas de EnvioDTE/EnvioBOLETA,
--    libro IECV y leyenda de la representacion impresa). Nullable: mientras
--    esten vacios rige el fallback de entorno (APP_SII_FCH_RESOL/NRO_RESOL),
--    que es como opera el ambiente de certificacion.
ALTER TABLE empresa ADD COLUMN fch_resol DATE;
ALTER TABLE empresa ADD COLUMN nro_resol INTEGER;

-- 2) Certificado digital por empresa (modo firma POR_EMPRESA). El PKCS#12 y su
--    clave se guardan CIFRADOS con AES-256-GCM por CifradorSecretos (clave
--    maestra de entorno, jamas en BD): formato [version][iv][ciphertext+tag].
--    Historial 1-N con UN solo activo por empresa (indice parcial): renovar un
--    certificado desactiva el anterior sin borrarlo (auditoria).
CREATE TABLE certificado_empresa (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id       BIGINT       NOT NULL REFERENCES empresa (id),
    nombre_archivo   VARCHAR(255) NOT NULL,
    p12_cifrado      BYTEA        NOT NULL,
    password_cifrada BYTEA        NOT NULL,
    -- RUN del firmante autorizado (RutEnvia/rutSender), extraido del
    -- SERIALNUMBER del subject al subirlo (u override manual).
    rut_firmante     VARCHAR(12)  NOT NULL,
    subject          VARCHAR(500),
    valido_desde     TIMESTAMPTZ  NOT NULL,
    valido_hasta     TIMESTAMPTZ  NOT NULL,
    huella_sha256    VARCHAR(64)  NOT NULL,
    key_version      INTEGER      NOT NULL DEFAULT 1,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por       VARCHAR(180),
    creado_en        TIMESTAMPTZ  NOT NULL,
    actualizado_en   TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_certificado_empresa_activo
    ON certificado_empresa (empresa_id) WHERE activo;
