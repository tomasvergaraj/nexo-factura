-- =====================================================================
-- Sesion y seguridad (P2-3): refresh tokens revocables.
--  - Se almacena SOLO el hash SHA-256 (hex, 64 chars) del token opaco; el
--    valor crudo (256 bits, base64url) se entrega una vez al cliente y nunca
--    se persiste.
--  - Rotacion en cada /refresh: la fila presentada se marca revocada y se
--    emite una nueva. La deteccion de reuso (token cuyo hash coincide con una
--    fila ya revocada) revoca todas las filas del usuario.
-- =====================================================================
CREATE TABLE refresh_token (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuario (id),
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    expira_en    TIMESTAMPTZ  NOT NULL,
    revocado     BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);
