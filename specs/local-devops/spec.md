---
title: Runtime local Compose (E2E) â€” especificaciÃ³n
status: aprobada
source: Roadmap Notion â€” paso 13 (Runtime local Compose) + docker-compose
  existentes por componente
updated: 2026-10-31T20:08:04.670Z
---

# Runtime local Compose (E2E) â€” especificaciÃ³n

## PropÃ³sito

Materializar un entorno local multi-contenedor que levanta todo el canal WhatsApp bancario (Kafka, gateway Twilio, orquestador, servicios H1â€“H6, capa MCP, RAG y agente) mediante un `docker-compose.yml` en la raÃ­z del programa y scripts para levantar, parar y resetear, habilitando smoke E2E del flujo completo (Twilio â†’ H0 â†’ Kafka â†’ H0.2 â†’ H9 â†’ H7/H8 â†’ servicios â†’ Kafka â†’ H0 â†’ Twilio).

## Fuera de alcance (de la fuente)

- No es un monolito: cada componente mantiene su propia imagen/contenedor.
- No incluye Twilio real ni credenciales productivas; se usan simuladores/mocks y `.env.example`.
- No define lÃ³gica de negocio ni modifica servicios; solo orquesta su ejecuciÃ³n.
- No reemplaza CI/CD ni despliegue a producciÃ³n.

## Decisiones de arquitectura

- Un `docker-compose.yml` en la raÃ­z del programa que orquesta todos los contenedores.
- ImÃ¡genes construidas desde los Dockerfiles existentes de cada componente.
- Kafka en modo KRaft (sin ZooKeeper) para el piloto local.
- Variables de entorno centralizadas en `.env` (sin secretos commiteados; solo `.env.example`).
- Scripts en `scripts/` para bootstrap de topics, start, stop y reset.
- Healthchecks y `depends_on` con condiciÃ³n de salud para orden de arranque.
- Red compartida para comunicaciÃ³n inter-contenedor por nombre de servicio.
- Persistencia de datos en volÃºmenes nombrados para Kafka/Postgres cuando aplique.
- Puertos expuestos segÃºn `project.yaml` (MCP 8101, RAG 8102, agente 8103) y los de cada servicio.

## Requisitos

### Requisito: Compose raÃ­z multi-contenedor
El sistema DEBE proveer un `docker-compose.yml` en la raÃ­z que defina todos los servicios del canal.

#### Escenario: Arranque completo
- **DADO** el repositorio clonado y Docker disponible
- **CUANDO** se ejecuta el script de arranque
- **ENTONCES** se levantan Kafka, gateway, orquestador, servicios H1â€“H6, MCP, RAG y agente
- **Y** todos los contenedores reportan estado healthy

#### Escenario: Parada limpia
- **DADO** el entorno levantado
- **CUANDO** se ejecuta el script de parada
- **ENTONCES** todos los contenedores se detienen sin dejar procesos huÃ©rfanos

### Requisito: Bootstrap de topics
El sistema DEBE crear los topics del piloto (`wa.message.received.v1`, `wa.message.ready-to-send.v1`, `wa.message.status.v1`, `wa.message.dlq.v1`) al arrancar.

#### Escenario: Topics creados
- **DADO** Kafka healthy
- **CUANDO** se ejecuta el bootstrap
- **ENTONCES** los topics existen con particiÃ³n por `conversationId` y retenciÃ³n configurada

### Requisito: Reset del entorno
El sistema DEBE proveer un script de reset que detenga contenedores y elimine volÃºmenes de datos.

#### Escenario: Reset completo
- **DADO** el entorno con datos
- **CUANDO** se ejecuta reset
- **ENTONCES** contenedores y volÃºmenes se eliminan y el siguiente arranque parte de cero

### Requisito: ConfiguraciÃ³n por entorno
El sistema DEBE usar `.env` (a partir de `.env.example`) para secretos y parÃ¡metros, sin commitear valores reales.

#### Escenario: Sin secretos en el repo
- **DADO** el repositorio
- **CUANDO** se inspecciona
- **ENTONCES** no hay credenciales reales; solo `.env.example` con nombres de variables

### Requisito: Orden de arranque y healthchecks
Los servicios DEBEN arrancar en orden correcto mediante healthchecks y `depends_on`.

#### Escenario: Dependencias respetadas
- **DADO** el compose
- **CUANDO** se levanta
- **ENTONCES** Kafka estÃ¡ healthy antes de gateway/orquestador; servicios antes de MCP/agente

### Requisito: Trazabilidad y observabilidad local
El entorno DEBE propagar `correlationId` y exponer endpoints de salud/mÃ©tricas de cada componente.

#### Escenario: Health endpoints
- **DADO** el entorno levantado
- **CUANDO** se consulta `/actuator/health` (Java) o `/health` (Python)
- **ENTONCES** responde 200 para cada componente

## Criterios de aceptacion

- [x] Existe docker-compose.yml en la raiz con todos los servicios del canal.
- [x] Scripts scripts/start.sh (y .ps1), scripts/stop.sh (y .ps1), scripts/reset.sh (y .ps1) funcionan.
- [x] Bootstrap crea los topics del piloto (wa.message.received.v1, wa.message.ready-to-send.v1, wa.message.status.v1, wa.message.dlq.v1).
- [x] No hay secretos reales en el repo; solo .env.example.
- [x] Healthchecks y depends_on ordenan el arranque.
- [x] docker compose config valida sin errores (con .env basado en .env.example).
- [ ] Smoke E2E del flujo completo es posible (webhook simulado -> respuesta). Documentado en docs/local-devops.md. Pendiente: integrar outbound H0.2 y transporte /mcp H7, y demostrar el recorrido con evidencia runtime; la entrega E2E permanece abierta.

## Requisitos no funcionales

- Tiempo de arranque razonable para desarrollo local.
- Consumo de recursos acotado (lÃ­mites cuando sea posible).
- Reproducibilidad: mismo resultado en entornos limpios.
- Trazabilidad con `correlationId` extremo a extremo.

## ADRs requeridos

- ADR â€” TopologÃ­a local multi-contenedor (Compose) vs monolito
- ADR â€” Kafka KRaft para entorno local

## Dependencias

- Todos los componentes H0.1, H0, H0.2, H1â€“H6, H7, H8, H9 deben estar construidos.
- Contratos en `shared/contracts/` (eventos, OpenAPI, handoff, turno).

## Supuestos

- Docker Desktop / Docker Engine disponible en el entorno del desarrollador.
- Los Dockerfiles existentes de cada componente se reutilizan.
- Twilio se simula para smoke E2E (no se requiere cuenta real).
- Los servicios Java se empaquetan como JARs en sus imÃ¡genes.

## Preguntas abiertas

- Scripts en Bash, PowerShell o ambos (entorno Windows/Linux/Mac).
- Estrategia de datos de prueba (seed) para smoke E2E.
- Nivel de paralelismo de build de imÃ¡genes.
- RetenciÃ³n exacta por topic en entorno local.

