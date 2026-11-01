# wa-banking-channel

Canal bancario sobre WhatsApp: gateway Twilio, plataforma de eventos, orquestador, servicios
H1–H6, capa MCP, base de conocimiento y agente conversacional, con un entorno local completo en
`docker-compose.yml`.

Documentación de integración (README del stack, catálogo de eventos, configuración de Twilio y
sagas): [documentation.md](documentation.md).

## Arranque rápido

```powershell
# Desde la raíz; crear .env solo si falta y completar los secretos locales (nunca commitear).
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
./scripts/start.ps1
```

Bash: `test -f .env || cp .env.example .env` y `./scripts/start.sh`. Los scripts inician pero no
esperan salud; para esperar todos los servicios y habilitar el simulador de Twilio:

```text
docker compose -f docker-compose.yml -f scripts/compose.smoke.yml up -d --build --wait --wait-timeout 600
```

## Documentos

| Documento | Contenido |
|---|---|
| [documentation.md](documentation.md) | README del stack, catálogo de eventos, configuración de Twilio y sagas |
| [docs/local-devops.md](docs/local-devops.md) | Operación del entorno local y smoke E2E |
| [docs/local-smoke-data.md](docs/local-smoke-data.md) | Datos sintéticos del smoke y limpieza |
| [delivery/devops.md](delivery/devops.md) | Dockerfiles, Kafka KRaft/topics, PostgreSQL del orquestador y CI |
| [delivery/devops-local.md](delivery/devops-local.md) | Operación de cada servicio H1–H6 |
| [shared/contracts/events/README.md](shared/contracts/events/README.md) | Contratos de eventos v1 |

## Verificación

```text
python -m unittest discover -s scripts -p "test_*.py"
python scripts/verify_integracion.py
```

Por componente se usa el Maven Wrapper (`./mvnw -q -B clean verify`, JaCoCo ≥ 80 %) y
`python ai/verify.py` para los componentes de IA. Sin Docker, las pruebas de Testcontainers se
omiten y no acreditan integración.

## Estado

La configuración del stack está validada y los componentes tienen sus gates propios. El recorrido
E2E completo **no está acreditado**: el orquestador todavía no conecta el turno con H9, la capa
MCP no publica `/mcp` y en el último run no hubo Docker accesible ni `GOOGLE_API_KEY`. Detalle en
[documentation.md, sección 5](documentation.md#5-smoke-e2e-cómo-se-ejecuta-y-qué-falta).
