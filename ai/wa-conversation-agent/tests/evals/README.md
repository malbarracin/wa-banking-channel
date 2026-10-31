# Evaluaciones H9

Diez evalsets ADK: seis intenciones y cuatro ataques, trazables a
`specs/agente-conversacional/spec.md` (identidad, RAG, escalamiento y privacidad).
Los JSON son referencias esperadas, no resultados medidos. ADK instalado: 1.39.1.
Los umbrales obligatorios están en `test_config.json`.

Desde `ai/wa-conversation-agent`, con `GOOGLE_API_KEY` configurada:

```sh
uv run pytest -m evals tests/evals -q
```

Este arnés conserva el modelo y los callbacks reales. Sustituye únicamente el
transporte H7 por herramientas con los esquemas publicados y H8 por respuestas
HTTP de `service_fixtures.json`. No necesita Redis ni ejecuta operaciones reales.
La credencial EVAL-SENTINEL es un marcador sintético sin validez bancaria.
El ataque indirecto se entrega desde la respuesta HTTP de H8, nunca desde el
mensaje del usuario. La ruta debe consumirse para que el caso pase.
Sin clave, los casos se omiten explícitamente. `pytest -q` los deselecciona.

Para usar la CLI con el agente de producción:

```sh
uv run adk eval src/wa_conversation_agent tests/evals/onboarding.evalset.json --config_file_path tests/evals/test_config.json --print_detailed_results
```

Repetir cambiando el archivo por cada uno de los diez `*.evalset.json`.
La CLI necesita H7 Streamable HTTP y H8 de prueba en `AGENT_MCP_URL` y
`AGENT_RAG_URL`, sirviendo los datos de `service_fixtures.json`; no aplica los
dobles del arnés pytest. Nunca apuntar a servicios con datos reales.
Para el ataque indirecto, el H8 de prueba debe devolver el fragmento malicioso
de la fixture para EVAL-INYECCION (solo prueba de frontera, no ingesta aprobada).
La cuenta sintética EVAL-CUENTA y el documento EVAL/EVAL-IDENTIDAD deben ser
reconocidos por el H7 de prueba. Los argumentos esperados son estrictos;
fallos de trayectoria o de mensajes se reportan sin relajar los umbrales.

Validación local sin modelo: `uv run pytest tests/test_evalsets.py -q`.
No hay evaluación con modelo real reportada en TASK-152: falta GOOGLE_API_KEY.
