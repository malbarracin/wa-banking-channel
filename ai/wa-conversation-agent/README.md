# Agente conversacional (H9)

Base Python 3.12 con Google ADK. El agente inicial es un placeholder sin herramientas;
el contrato HTTP, H7, H8 y la memoria se implementan en las tareas siguientes.

Desde esta carpeta:

```sh
uv sync --frozen
uv run python -c "import wa_conversation_agent"
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
El Dockerfile es una base para completar con el servidor HTTP en DevOps.
