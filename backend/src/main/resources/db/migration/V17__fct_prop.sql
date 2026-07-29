-- Factor de proporcionalidad del IVA de uso comun (FctProp del libro de compras).
--
-- Sin este factor, LibroXmlGenerator rechaza cualquier libro de compras con IVA
-- de uso comun, asi que hasta ahora esos periodos quedaban en ERROR de forma
-- permanente: el job de revision pasaba fctProp = null a pelo, y la UI ni
-- siquiera ofrecia como informarlo (solo se podia por la API).
--
-- POR QUE VIVE EN empresa Y NO SE CALCULA
-- Legalmente el factor es por periodo, acumulado desde enero (ventas afectas
-- sobre ventas totales). Calcularlo aqui exigiria la historia completa de ventas
-- del ano, y el sistema solo conoce los DTE que emitio el: si la empresa adopto
-- nexo-factura a mitad de ano o vende por otro canal, el acumulado esta
-- incompleto y el factor saldria mal EN SILENCIO, dentro de una declaracion
-- tributaria. Se guarda entonces el valor que el contribuyente declara, y la UI
-- ofrece un factor SUGERIDO calculado con las ventas que si estan en el sistema,
-- como pista y nunca como valor automatico.
--
-- DOUBLE PRECISION y no NUMERIC(3,2): el factor viaja como Double de punta a
-- punta (parametro de la API, LibroResponse, generador) y Hibernate valida el
-- esquema al arrancar, asi que un NUMERIC tumba el contexto entero con
-- "wrong column type ... found [numeric], but expecting [float(53)]".
-- El CHECK cubre lo que de verdad importa —que sea una proporcion— y es mejor
-- garantia que la escala: NUMERIC(3,2) habria REDONDEADO un 0.605 en silencio en
-- vez de rechazarlo. Los dos decimales que exige el validador del SII los pone el
-- generador al formatear ("%.2f"; el SII rechaza "0.6").
ALTER TABLE empresa
    ADD COLUMN fct_prop DOUBLE PRECISION
        CONSTRAINT ck_empresa_fct_prop CHECK (fct_prop IS NULL OR (fct_prop >= 0 AND fct_prop <= 1));

COMMENT ON COLUMN empresa.fct_prop IS
    'Factor de proporcionalidad del IVA de uso comun [0,1]; null = no configurado';

-- El factor EFECTIVAMENTE declarado en cada envio, que no es necesariamente el
-- que hoy tiene la empresa: el de arriba es editable y el envio manual puede
-- pasar un override por periodo. Sin esta columna, despues de editar el valor no
-- habria forma de saber que se declaro en un envio ya hecho.
ALTER TABLE envio_libro
    ADD COLUMN fct_prop DOUBLE PRECISION
        CONSTRAINT ck_envio_libro_fct_prop CHECK (fct_prop IS NULL OR (fct_prop >= 0 AND fct_prop <= 1));

COMMENT ON COLUMN envio_libro.fct_prop IS
    'Factor declarado en ESTE envio; null si el libro no tenia IVA de uso comun';
