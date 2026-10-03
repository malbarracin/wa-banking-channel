# Source traceability

| Campo | Valor |
|-------|-------|
| Proyecto | wa-users-service |
| Fuente | Notion |
| URL | https://app.notion.com/p/36875bb08ca78197a853e85505bf49a5 |
| Título Notion | 👤 H1 — Servicio de Usuarios |
| Actualizado | 2026-10-03 |

## Requirement mapping

| Requirement | Domain | Source ID / URL |
|-------------|--------|-----------------|
| Alta de usuario bancario | usuarios | Notion §3 Alcance funcional + §6 U1 + §7 AC |
| Consulta de usuario por identificador | usuarios | Notion §3 + §6 U2 |
| Actualización de datos permitidos | usuarios | Notion §3 + §6 U3 |
| Cambio de estado operativo del usuario | usuarios | Notion §5 RN3 + §6 U4 + §7 AC |
| Validación de unicidad de documento | usuarios | Notion §5 RN2 + §6 U1 + §7 AC |
| Auditoría de eventos del ciclo de vida del usuario | usuarios | Notion §5 RN5 + §7 AC |

## Decisions & Resolutions

| ID | Pregunta | Resolución |
|----|----------|------------|
| OQ-1 | Campos mínimos obligatorios de alta | Pendiente — ver `## Open Questions` en spec.md |
| OQ-2 | Clave de unicidad (tipo+número documento) | Pendiente — asumido tipo+número como clave compuesta |
| OQ-3 | Campos actualizables | Pendiente — asumido: nombre tratamiento, contacto, preferencias no sensibles |
| OQ-4 | Campos expuestos en consulta por actor | Pendiente — asumido todos los campos del alta excepto datos de verificación del canal |
| OQ-5 | Transiciones de estado y baja lógica terminal | Pendiente — asumido baja lógica como estado terminal |
