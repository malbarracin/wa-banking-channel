-- Datos de prueba locales para wa-cards-service (H5). No usar en productivo.
-- Aplicar sobre el esquema creado por el perfil dev/docker (ddl-auto=update):
--   psql "postgresql://localhost:5437/wa_cards" -f delivery/sql/001-datos-qa.sql
-- El documento del titular debe coincidir con el que devuelve H2 al validar la credencial.
-- IDs fijos para que coincidan con los ejemplos de Swagger, api.md, Postman y Bruno.

INSERT INTO tarjetas (id, numero_tarjeta_hash, ultimos_cuatro, marca, tipo,
                      titular_documento_tipo, titular_documento_numero, estado, activa,
                      created_at, updated_at)
VALUES
    ('11111111-1111-4111-8111-111111111111', 'hash-demo-0001', '1234', 'VISA', 'CREDITO',
     'DNI', '30111222', 'ACTIVA', true, now(), now()),
    ('22222222-2222-4222-8222-222222222222', 'hash-demo-0002', '5678', 'MASTERCARD', 'DEBITO',
     'DNI', '30111222', 'ACTIVA', true, now(), now()),
    ('33333333-3333-4333-8333-333333333333', 'hash-demo-0003', '9012', 'AMEX', 'CREDITO',
     'DNI', '30111222', 'BLOQUEO_TEMPORAL', true, now(), now()),
    ('44444444-4444-4444-8444-444444444444', 'hash-demo-0004', '3456', 'VISA', 'DEBITO',
     'DNI', '30111222', 'BLOQUEO_DEFINITIVO', false, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO consumos (id, tarjeta_id, fecha, comercio, monto, moneda, descripcion, referencia)
VALUES
    ('51111111-1111-4111-8111-111111111111', '11111111-1111-4111-8111-111111111111',
     now() - interval '1 day', 'Comercio de prueba', 1250.50, 'ARS', 'Compra', 'COMPRA-001'),
    ('52222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111',
     now() - interval '2 day', 'Supermercado', 8400.00, 'ARS', 'Compra', 'COMPRA-002'),
    ('53333333-3333-4333-8333-333333333333', '11111111-1111-4111-8111-111111111111',
     now() - interval '3 day', 'Estación de servicio', 3200.75, 'ARS', 'Combustible', 'COMPRA-003')
ON CONFLICT (id) DO NOTHING;
