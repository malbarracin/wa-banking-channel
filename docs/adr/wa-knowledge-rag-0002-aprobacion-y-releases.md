# ADR wa-knowledge-rag-0002 — Flujo de aprobación configurable y releases del conocimiento

- Estado: aceptada
- Fecha: 2026-11-27
- Componente: `ai/wa-knowledge-rag`

## Contexto

Solo puede servirse contenido aprobado. El banco no definió todavía quién aprueba cada tipo de contenido. Cada
consulta tiene que informar qué versión del conocimiento respondió, y tiene que poder volverse atrás sin reiniciar.
En desarrollo y pruebas las aprobaciones las registra el equipo y quedan marcadas como de prueba.

## Decisión

- El texto vive en archivos de `content/` (historial de redacción en git). Estado, aprobaciones, parámetros y
  publicaciones viven en la base.
- Máquina de estados `propuesta → borrador → en_revision → aprobada → publicada`, más `retirada`. Las aprobaciones
  requeridas por tipo salen de `config/approval-policy.yaml` (por defecto: área dueña + compliance).
- Cada aprobación guarda rol, fecha, versión y si es de prueba. Con `KB_ENVIRONMENT=real` se rechaza publicar lo que
  solo tiene aprobaciones o parámetros de prueba.
- Publicar, retirar, vencer o volver atrás crea una **release** nueva (`kv-N`) que lista la versión servida de cada
  pieza; una sola release activa. La consulta filtra siempre por la release activa y la vigencia.
- Los valores del banco son parámetros versionados y aprobados; el texto se renderiza al publicar.

## Consecuencias

- Vuelta atrás = release nueva con contenido anterior; el historial nunca se borra.
- Reindexar solo lo cambiado (hash del texto renderizado).
- Cuando el banco defina su proceso, se cambia la política de aprobación sin tocar código.
