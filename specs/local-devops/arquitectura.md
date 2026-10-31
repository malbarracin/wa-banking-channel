---
title: Runtime local Compose (E2E) — arquitectura
status: aprobada
source: specs/local-devops/spec.md + int-architecture
updated: 2026-10-31T20:15:00Z
---

# Arquitectura — Runtime local Compose (E2E)

## Objetivo
Orquestar todo el canal WhatsApp bancario en un único `docker-compose.yml` en la raíz, con scripts para levantar/parar/resetear, bootstrap de topics Kafka (KRaft), healthchecks, red compartida, volúmenes y configuración vía `.env`.

## Topología
- **Red**: red única `wa-channel` (bridge) para resolución por nombre de servicio.
- **Kafka**: broker KRaft único compartido (`kafka:29092` interno, `localhost:9092` host). Sin ZooKeeper.
- **Init**: contenedor `kafka-init` crea topics del piloto con 3 particiones, `replication-factor 1`, `cleanup.policy=delete` y retenciones acordes a `shared/contracts/events/topics.md`.
- **Servicios Java**: `gateway/wa-twilio-gateway` (8090), `platform/wa-event-platform` (8092), `platform/wa-channel-orchestrator` (8091), `services/wa-*` (8080-8086) con sus Postgres.
- **IA**: `ai/wa-mcp-layer` (8101), `ai/wa-knowledge-rag` (8102), `ai/wa-conversation-agent` (8103), con `kb-postgres` (pgvector), `agent-redis`, `agent-memory-server`.
- **Persistencia**: volúmenes nombrados por componente (kafka, postgres, redis).

## Orden de arranque
1. Infra base: `kafka` (healthy) → `kafka-init` (completed) → Postgres/Redis según dependencia.
2. Plataforma: `wa-event-platform` (opcional pero parte del stack), `wa-twilio-gateway`, `wa-channel-orchestrator`.
3. Servicios H1–H6 con sus DBs.
4. IA: `kb-postgres` → `wa-knowledge-rag`; `agent-redis` → `agent-memory-server` → `wa-conversation-agent`; `wa-mcp-layer` depende de H1–H6.

## Configuración
- `.env` en raíz (no commiteado). `.env.example` con nombres completos.
- Puertos mapeados a host según `project.yaml` (MCP 8101, RAG 8102, agente 8103). Java usan puertos documentados.
- Variables compartidas: `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`.

## Seguridad
- Sin secretos reales. Solo `.env.example`.
- Kafka local sin TLS/SASL.
- Webhook Twilio validado por componente gateway (fuera de alcance de este compose).

## Observabilidad
- Healthchecks en todos los servicios (`/actuator/health` Java, `/health` Python).
- `depends_on` con `condition: service_healthy` / `service_completed_successfully`.
- `correlationId` propagado por componentes (ya implementado).

## ADRs
- ADR-001: Topología local multi-contenedor con docker-compose raíz vs monolito.
- ADR-002: Kafka KRaft para entorno local (sin ZooKeeper).

## Ampliación: verificación operativa del runtime (2026-10-31)

Entrega del paso arquitecto de `20261031-f8ae`. Conserva la topología aprobada;
define el trabajo verificable para levantar, corregir y probar el entorno existente.
No acredita ejecución del canal ni modifica contratos de otros componentes.
Decisión complementaria: [ADR-014](../../docs/adr/ADR-014-verificacion-local-e2e.md).

### Responsabilidades y organización

| Ubicación | Responsabilidad del siguiente paso |
| --- | --- |
| `docker-compose.yml` | Cableado, imágenes, volúmenes, dependencias y salud. |
| `scripts/start.sh`, `scripts/start.ps1` | Arranque y espera acotada; fallo observable si no alcanza disponibilidad. |
| `scripts/` | Arnés de smoke y simulador de transporte Twilio separados del producto. |
| `gateway/wa-twilio-gateway` | Adaptación Twilio, firma y envío mediante SDK oficial. |
| `platform/wa-channel-orchestrator` | Orden, deduplicación, turno H9 y outbound. |
| `docs/local-devops.md` | Comandos ejecutables, preparación de datos y evidencia final. |

Se mantienen los paquetes de cada componente; no se crea lógica bancaria en scripts,
gateway u orquestador. H7–H9 y H1–H6 conservan sus responsabilidades y contratos.

### Disponibilidad y orden

`docker compose up -d --build` inicia, pero por sí solo no demuestra salud final.
El arranque debe esperar con límite configurable, devolver código no cero ante
fallo y mostrar servicio/estado sin volcar variables resueltas ni secretos.
Un servicio permanente debe quedar running/healthy; `kafka-init` debe terminar
con código 0 y no se exige healthy a un proceso de inicialización terminado.
La espera cubre todos los servicios permanentes, no solo dependencias del gateway.
Comprobar que cada herramienta del healthcheck exista dentro de su imagen.
Los endpoints de proceso no demuestran conectividad funcional: el smoke cubre esa brecha.

Mantener la red, volúmenes y dependencias existentes; evitar ciclos al ajustar
H0.2 → H9 → H7 → servicios. H0.2 puede arrancar antes de H9 si su disponibilidad
funcional se comprueba antes de inyectar el mensaje. No resetear volúmenes como
parte de start ni para resolver fallos; reset es una operación explícita del operador.

### Contratos, Kafka y estado

Se conservan los cuatro topics, schemas y políticas de
`shared/contracts/events/topics.md`: 3 particiones, réplica local 1, retención
received/ready 7 días, status 30 días, DLQ 90 días. Sin nuevos topics ni versiones.
Todos los eventos se validan al producir y consumir, con sobre cerrado;
clave `conversationId`, headers `correlationId`, `eventType`, `eventVersion`.
Los productores usan idempotencia y `acks=all`; consumidores sin auto-commit,
ACK después del efecto durable, procesamiento secuencial por partición.
Se mantienen los reintentos/backoff y DLQ implementados y documentados en los handoffs;
un evento inválido va a DLQ sin reintento. No ajustar políticas para hacer pasar el smoke.

Inbound conserva `messageId=MessageSid` y conversación SHA-256 de `From + "\n" + To`
canónicos, conforme al handoff H0. H0 deduplica replay temporalmente en memoria;
H0.2 deduplica durablemente por messageId; H9 recibe la misma clave en reintentos;
H0 persiste el envío outbound en su volumen H2. No usar eventId para deduplicar
ni generar otro messageId al reintentar una misma operación.

La máquina de estados, timeouts y compensaciones siguen el handoff de H0.2 y
ADR-007; este paso no agrega flujos de onboarding ni adaptadores de H2/H3.
El handoff declara limitaciones de esos adaptadores: no afirmar cobertura de
sagas por observar una respuesta simple. Para H9 conservar
`H9_TIMEOUT_MS > 1000 * AGENT_TURN_TIMEOUT_SECONDS` (defaults 5000 y 4 s),
mismo request/clave en transporte/5xx y sin retry de 4xx, incluido 409.

### Twilio simulado y seguridad

Usar un simulador HTTP de la API de mensajes de Twilio que capture la petición
outbound y devuelva una respuesta compatible con el SDK, con SID sintético único.
El arnés envía el formulario inbound firmado a `POST /webhook/twilio` con
MessageSid, From, To y Body sintéticos. Firma con el token local y la URL pública
exacta configurada; no desactivar RequestValidator, anti-replay, límites de tamaño
ni de tasa. Los callbacks usan `/webhooks/twilio/status` y firma propia.

Hallazgo de implementación: `OutboundConfiguration.twilioSender` construye hoy
un cliente contra Twilio sin configuración de destino local. El ejecutor debe
habilitar una sustitución de transporte exclusiva del entorno local conservando
SDK, serialización, reserva e idempotencia; no sustituir TwilioSender por una
respuesta fija. Configurar solo un hostname no garantiza redirección del SDK:
verificar en el simulador la recepción efectiva. Fuera del entorno local se
mantiene el transporte habitual. No usar cuenta ni números reales para el smoke.

Secretos únicamente por entorno, sin imprimir `.env` ni `docker compose config`
resuelto. H0.2 y H9 comparten el valor de H9_SERVICE_TOKEN/AGENT_SERVICE_TOKEN.
El modelo configurado es gemini-2.5-flash; una clave GOOGLE_API_KEY válida es
prerrequisito para acreditar inferencia real. Si falta, registrar el tramo no
verificado: una respuesta de contingencia o un doble de H9 no acredita H9/H7/servicios.
Kafka local conserva su configuración; fuera de local requiere TLS/SASL y ACL.

### Secuencia y evidencia de aceptación

1. Validar configuración con `docker compose --env-file .env.example config --quiet`.
2. Ejecutar scripts/start para el sistema operativo; esperar salud de todos los
   servicios permanentes y salida 0 de kafka-init. Guardar resumen de estados.
3. Preparar datos sintéticos por las APIs publicadas de los servicios. Elegir
   una consulta soportada por H7 cuya respuesta pueda contrastarse con esos datos;
   documentar autenticación/vínculo necesarios y limpieza por sus contratos.
4. Iniciar captura en el simulador antes del webhook. Enviar MessageSid nuevo y
   correlación UUID, recoger ACK y esperar con plazo finito la respuesta outbound.
5. Correlacionar inbound, turno H9, llamada H7 al servicio seleccionado, resultado
   de negocio y outbound. Un saludo sin herramientas solo acredita transporte.
   No exigir que un único mensaje invoque los seis servicios: todos deben estar
   sanos y al menos un servicio debe participar realmente en el recorrido.
6. Afirmar destino/cuerpo esperado en la captura Twilio, conservación de
   conversación/correlación y ausencia de DLQ para ese caso. El ACK inbound por sí
   solo no demuestra entrega. El resultado toolsUsed por sí solo no demuestra
   éxito del servicio. Las aserciones deben admitir redacción variable del modelo.
7. Repetir el mismo webhook dentro de la ventana anti-replay y comprobar que no
   aparece un segundo envío. No extender esta evidencia a reinicios o réplicas.
8. Documentar comandos exactos, plazos, resultado observado y limitaciones en
   docs/local-devops.md; evidencias sin cuerpos privados, tokens ni teléfonos reales.

Registrar latencia del recorrido, métricas de duplicados/errores y estado de DLQ
para la correlación del caso. No reproducir DLQ automáticamente durante el smoke;
seguir el procedimiento existente y conservar claves si se requiere reproceso.

### Verificación de este diseño y límites del paso

- [x] Contratos, identificación, topics, retenciones y responsabilidades conservados.
- [x] Condición de arranque, transporte simulado y evidencia E2E definidos.
- [x] Riesgos de firma, idempotencia, timeouts, PII y límites de sagas documentados.
- [x] ADR complementario escrito, sin código de producto.
- [ ] Arranque y smoke ejecutados: corresponde al ejecutor y verificador posteriores.

En este paso `docker info --format '{{.ServerVersion}}'` devolvió acceso denegado
al pipe `dockerDesktopLinuxEngine`. No se verificó ejecución de contenedores;
reintentar con Docker accesible en el paso de ejecución. Esta limitación de
verificación no impide entregar el diseño. El texto previo de aceptación en
spec.md no constituye evidencia de un smoke ejecutado en esta sesión.
