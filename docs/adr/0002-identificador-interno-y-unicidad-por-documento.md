# 2. Identificador interno y unicidad por documento

Fecha: 2026-11-21
Estado: Aceptado

## Contexto

La consulta de usuario (R2) necesita un identificador acordado, y el alta (R5) exige no duplicar clientes por la
regla de unicidad del banco (ej. mismo documento). Otros servicios del programa necesitan referenciar al cliente.

## Decisión

- Cada usuario tiene un **identificador interno `id` (UUID)** generado por el servicio: es el identificador
  canónico para APIs y para que otros servicios lo referencien.
- La unicidad se garantiza con un **índice único sobre `(tipo_documento, numero_documento)`**: no se permiten
  duplicados (R5). Un intento de alta con documento ya registrado → `409 CONFLICT / CONFLICT`.
- Se expone **consulta por id** (`GET /api/v1/users/{id}`) y **consulta por documento**
  (`GET /api/v1/users/by-document`) para no acoplar al consumidor a un único identificador.

## Consecuencias

- El `id` es estable y no expone PII; el documento se muestra enmascarado en las respuestas.
- La unicidad se delega a la base (constraint) además de validarse en el service, evitando duplicados en concurrencia.
- Queda abierto a confirmar cuál identificador es el "acordado" por el programa; mientras tanto se soportan ambos.