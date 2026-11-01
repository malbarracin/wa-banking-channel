-- SOLO LOCAL: excepción autorizada en TASK-186 hasta implementar TASK-193.
-- Ejecutar después de crear cliente H1 y aceptar términos H3 por API.
-- No montar en /docker-entrypoint-initdb.d: requiere el esquema y el vínculo.
\set ON_ERROR_STOP on
BEGIN;
UPDATE vinculo
SET estado = 'ACTIVO', documento_tipo = 'DNI', documento_numero = :'document',
    fecha_activacion = CURRENT_TIMESTAMP,
    fecha_ultimo_cambio_estado = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE id = :'link_id'::uuid AND numero_whatsapp = :'phone'
  AND numero_whatsapp LIKE '+1555%' AND terminos_aceptados = true
  AND :'document' LIKE 'QA-%'
  AND estado = 'PENDIENTE_VERIFICACION';
COMMIT;
