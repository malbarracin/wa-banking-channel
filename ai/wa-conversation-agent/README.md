# Agente conversacional (H9)

Base Python 3.12 con Google ADK. El agente inicial es un placeholder sin herramientas;
el contrato HTTP ofrece una respuesta determinista. H7, H8, Runner y memoria se integran en tareas siguientes.

Desde esta carpeta:

```sh
uv sync --frozen
uv run python -c "import wa_conversation_agent"
uv run python -m wa_conversation_agent.server
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest -q --cov=src --cov-fail-under=80
```

Desde la raíz: `python ai/verify.py` detecta todos los componentes Python.
Las pruebas unitarias no llaman a servicios ni al modelo real.

Las variables están en `.env.example`. Exportarlas en el entorno; el paquete no carga
archivos `.env` automáticamente. `GOOGLE_API_KEY` lo consume el SDK al invocar el modelo;
no hace falta para importar el paquete. Los secretos no se incluyen en las instrucciones.
El puerto H9 es 8103; configurar `H9_BASE_URL` en H0.2 para apuntar a este puerto.

Para explorar el placeholder con ADK, ejecutar `uv run adk web src`.
El Dockerfile inicia el servidor HTTP en `AGENT_PORT` (8103 por defecto).

`POST /api/v1/agent/turn` requiere `Authorization: Bearer <AGENT_SERVICE_TOKEN>`
y los headers `X-Correlation-Id`, `X-Conversation-Id`, `X-Message-Id`, `X-Idempotency-Key`.
El body sigue los DTO de H0.2: `correlationId`, `conversationId`, `messageId`,
`customerAddress`, `body`, `context.technicalContext` y `context.history`.
La respuesta conserva los tres identificadores y agrega `reply.body`, `escalate` y `toolsUsed`.
Devuelve 400 ante datos inválidos, 401 ante autenticación inválida y 503 ante indisponibilidad
del procesador. Los errores no incluyen datos de entrada ni detalles técnicos.
