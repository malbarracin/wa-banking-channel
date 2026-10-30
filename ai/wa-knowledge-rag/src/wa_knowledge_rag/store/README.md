# Store H8

`connect(Settings(), apply_migrations=True)` abre PostgreSQL, aplica los SQL
versionados y registra el adaptador de pgvector. Requiere `DATABASE_URL` en el
entorno. Para uso normal, `apply_migrations=False` (predeterminado). No se abre
ninguna conexión al importar el módulo.

Las migraciones se incluyen en el paquete, se serializan mediante un advisory
lock transaccional y se registran en `kb_migration`. Un fallo revierte la
transacción; los SQL aplicados no deben editarse, se agrega otra migración.
El usuario de migraciones necesita permisos para crear la extensión `vector`.
El esquema inicial exige embeddings de 768 dimensiones.

`KnowledgeRepository(connection).search(query, embedding, audience=..., version=None,
top_k=5)` devuelve candidatos con procedencia y puntaje RRF bruto. El cálculo de
confianza y la respuesta del contrato HTTP corresponden a `retrieval/`.
La audiencia es obligatoria y exacta: `public` no incluye `internal`.
Se exige estado `approved`, versión del chunk igual a la fuente y la mayor
versión aprobada por título; `version` restringe esa selección y no habilita
versiones históricas. La publicación debe retirar la versión anterior e insertar
la nueva en una misma transacción, responsabilidad de `ingestion/`.

Pruebas sin Docker: `uv run pytest -m 'not integration' -q --cov=src --cov-fail-under=80`.
Prueba SQL real: `uv run pytest -m integration -q`, con Docker disponible y la
imagen `pgvector/pgvector:pg17` (testcontainers crea una base aislada).
