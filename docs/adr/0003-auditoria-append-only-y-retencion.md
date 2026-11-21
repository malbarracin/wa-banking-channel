# 3. Auditoría append-only y retención

Fecha: 2026-11-21
Estado: Aceptado

## Contexto

La regla 5 de la fuente exige que toda alta y cambio de estado quede registrada con fecha, acción y resultado (R6).
La modificación de datos (R3) también debe auditarse. La auditoría es un requisito de cumplimiento del banco.

## Decisión

- Tabla `audit_events` **solo-append**: la API nunca actualiza ni borra eventos de auditoría.
- Cada evento incluye `user_id`, `action` (ALTA/ACTUALIZACION/CAMBIO_ESTADO), `resultado` (EXITOSO/RECHAZADO),
  `detalle` (resumen sin PII sensible), `correlation_id`, `actor` y `created_at`.
- El evento se inserta en la **misma transacción** que la operación auditada, para que no haya cambios sin registro.
- Los eventos **no se eliminan** (retención indefinida); cualquier política de purga es decisión del banco, fuera de
  este servicio.

## Consecuencias

- Trazabilidad completa y a prueba de manipulación desde la API.
- `audit_events` crece de forma monótona; se indexa por usuario y fecha para el historial resumido (soporte).
- El detalle evita PII completa (solo claves/valores enmascarados) para no duplicar datos sensibles.