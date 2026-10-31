# Despliegue H9

Desde la raíz del repositorio, copiar `ai/.env.example` a `ai/.env` local
y completar `GOOGLE_API_KEY`, `AGENT_SERVICE_TOKEN` y
`AGENT_MEMORY_KEY_SECRET` (mínimo 32 caracteres). No versionar `.env`.
El token de servicio debe coincidir con el configurado en H0.2.

```sh
docker compose -f ai/docker-compose.yml --env-file ai/.env config --quiet
docker compose -f ai/docker-compose.yml --env-file ai/.env up -d --build
```

H9 publica el puerto 8103 y un endpoint operativo `GET /health` sin autenticación.
El healthcheck comprueba que HTTP responde; no certifica disponibilidad de Gemini,
H7 o H8. El turno sigue requiriendo el token de servicio.

El compose propio reutiliza Redis y Agent Memory Server del compose compartido:

```sh
docker compose -f ai/wa-conversation-agent/docker-compose.yml --env-file ai/.env config --quiet
docker compose -f ai/wa-conversation-agent/docker-compose.yml --env-file ai/.env up -d --build
```

Ambos usan la red `ai_default` con el nombre de proyecto predeterminado del stack
compartido. Para usar el compose propio, iniciar H7/H8 en esa red mediante el stack
compartido. No iniciar las dos variantes de H9 simultáneamente. Las URLs internas
son `http://wa-mcp-layer:8101/mcp`, `http://wa-knowledge-rag:8102` y
`http://agent-memory-server:8000`; Redis usa `redis://agent-redis:6379`.
H9 utiliza Redis para su memoria implementada; la URL del memory server queda
configurada para la integración prevista, sin modificar el comportamiento del agente.

CI agrega H9 a calidad, auditoría de dependencias, build y evals manuales.
Las evals manuales requieren el secreto `GOOGLE_API_KEY` en GitHub.

En TASK-154 ambos compose validaron con Docker CLI. Docker Desktop estaba apagado:
quedan pendientes `docker build -t wa-conversation-agent:local ai/wa-conversation-agent`
y el arranque anterior, seguido de `curl http://localhost:8103/health`.
