# Base de conocimiento RAG (H8)

Base Python 3.12 para contenido aprobado, recuperación con citas y confianza,
y separación de contenido público e interno. Diseño: `specs/conocimiento-rag/arquitectura.md`.
Esta entrega prepara configuración y tests; ingesta, pgvector, retrieval y API
se implementan en las siguientes tareas del hito.

Desde esta carpeta:

```sh
uv sync --frozen
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

Desde la raíz: `python ai/verify.py` verifica todos los componentes de IA.
Los tests base no necesitan Docker ni acceso al modelo. `tests/golden/` queda
reservado para el set dorado de retrieval.

Copiar `.env.example` a `.env` local o exportar las variables del entorno.
`Settings` usa los nombres del contrato H8 sin prefijo adicional: `PORT` (8102),
`DATABASE_URL`, `GOOGLE_API_KEY`, `EMBEDDING_MODEL` (text-embedding-004),
`EMBEDDING_DIMENSIONS` (768), `RAG_TOP_K` (5), `RAG_CONFIDENCE_HIGH_MIN`,
`RAG_CONFIDENCE_MEDIUM_MIN` y `LOG_LEVEL` (INFO).
Los valores vacíos se ignoran. Los secretos no tienen valor por defecto y usan
`SecretStr`; no registrar sus valores ni guardar `.env` en el repositorio.
Los umbrales quedan sin definir hasta calibrarlos; cuando se informan deben
estar entre 0 y 1, con el medio menor que el alto. Los servicios futuros deberán
exigir sus credenciales y umbrales antes de operar.

H8 no opera productos bancarios ni consume Kafka o Twilio. El contenido del
cliente y recuperado se trata como datos, nunca como instrucciones. La ingesta
posterior deberá rechazar PII y contenido no aprobado. No usar datos reales en fixtures.
