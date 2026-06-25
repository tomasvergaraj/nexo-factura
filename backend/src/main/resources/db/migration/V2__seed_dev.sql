-- =====================================================================
-- Datos de demostracion (solo ambiente dev)
-- Usuario:   admin@nexofactura.cl
-- Password:  nexo1234
-- =====================================================================

INSERT INTO empresa (rut, razon_social, giro, actividad_economica, direccion, comuna, ciudad, telefono, email, creado_en)
VALUES ('76543210-9', 'Nexo Software SpA', 'Desarrollo de software y servicios informaticos',
        620200, 'Calle Ejemplo 123', 'Quillota', 'Quillota', '+56 9 8196 4119',
        'contacto@nexosoftware.cl', now());

INSERT INTO usuario (nombre, email, password_hash, rol, activo, empresa_id, creado_en)
VALUES ('Administrador Demo', 'admin@nexofactura.cl', '$2b$10$6wjmO1b1zQOZIvLGW5BPqeYRAzDel69BC8ofnXtPhqUhg8wCS3PMK', 'ADMIN', TRUE, 1, now());

INSERT INTO cliente (empresa_id, rut, razon_social, giro, direccion, comuna, ciudad, email, activo, creado_en) VALUES
 (1, '77111222-3', 'Comercial Las Palmas Ltda', 'Venta al por menor', 'Av. Valparaiso 456', 'Vina del Mar', 'Vina del Mar', 'pagos@laspalmas.cl', TRUE, now()),
 (1, '78222333-4', 'Constructora Andes SpA', 'Construccion de obras', 'Los Carrera 789', 'Quillota', 'Quillota', 'finanzas@andes.cl', TRUE, now()),
 (1, '79333444-5', 'Restaurant El Fogon EIRL', 'Servicios de comida', 'Freire 321', 'La Calera', 'La Calera', 'admin@elfogon.cl', TRUE, now());

INSERT INTO producto (empresa_id, codigo, nombre, precio_neto, unidad, afecto, activo) VALUES
 (1, 'SRV-001', 'Desarrollo de landing page', 450000, 'UN', TRUE, TRUE),
 (1, 'SRV-002', 'Plan de soporte mensual', 120000, 'MES', TRUE, TRUE),
 (1, 'SRV-003', 'Hora de desarrollo', 25000, 'HRA', TRUE, TRUE),
 (1, 'SRV-004', 'Consultoria tecnica (exenta)', 80000, 'UN', FALSE, TRUE);

-- CAF de demostracion: rango de folios para facturas afectas (tipo 33)
INSERT INTO caf (empresa_id, tipo_dte, folio_desde, folio_hasta, folio_actual, fecha_autorizacion, fecha_vencimiento, agotado, version, creado_en)
VALUES (1, 'FACTURA_AFECTA', 1, 500, 0, current_date, current_date + INTERVAL '6 months', FALSE, 0, now());

INSERT INTO caf (empresa_id, tipo_dte, folio_desde, folio_hasta, folio_actual, fecha_autorizacion, fecha_vencimiento, agotado, version, creado_en)
VALUES (1, 'BOLETA_AFECTA', 1, 1000, 0, current_date, current_date + INTERVAL '6 months', FALSE, 0, now());
