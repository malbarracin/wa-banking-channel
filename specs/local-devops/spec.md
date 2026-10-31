---
title: Runtime local Compose (E2E) — especificación
status: aprobada
source: Roadmap Notion — paso 13 (Runtime local Compose) + docker-compose
  existentes por componente
updated: 2026-10-31T20:08:04.670Z
---

# Runtime local Compose (E2E) — especificación

## Propósito

Materializar un entorno local multi-contenedor que levanta todo el canal WhatsApp bancario (Kafka, gateway Twilio, orquestador, servicios H1–H6, capa MCP, RAG y agente) mediante un `docker-compose.yml` en la raíz del programa y scripts para levantar, parar y resetear, habilitando smoke E2E del flujo completo (Twilio → H0 → Kafka → H0.2 → H9 → H7/H8 → servicios → Kafka → H0 → Twilio).

## Fuera de alcance (de la fuente)

- No es un monolito: cada componente mantiene su propia imagen/contenedor.
- No incluye Twilio real ni credenciales productivas; se usan simuladores/mocks y `.env.example`.
- No define lógica de negocio ni modifica servicios; solo orquesta su ejecución.
- No reemplaza CI/CD ni despliegue a producción.

## Decisiones de arquitectura

- Un `docker-compose.yml` en la raíz del programa que orquesta todos los contenedores.
- Imágenes construidas desde los Dockerfiles existentes de cada componente.
- Kafka en modo KRaft (sin ZooKeeper) para el piloto local.
- Variables de entorno centralizadas en `.env` (sin secretos commiteados; solo `.env.example`).
- Scripts en `scripts/` para bootstrap de topics, start, stop y reset.
- Healthchecks y `depends_on` con condición de salud para orden de arranque.
- Red compartida para comunicación inter-contenedor por nombre de servicio.
- Persistencia de datos en volúmenes nombrados para Kafka/Postgres cuando aplique.
- Puertos expuestos según `project.yaml` (MCP 8101, RAG 8102, agente 8103) y los de cada servicio.

## Requisitos

### Requisito: Compose raíz multi-contenedor
El sistema DEBE proveer un `docker-compose.yml` en la raíz que defina todos los servicios del canal.

#### Escenario: Arranque completo
- **DADO** el repositorio clonado y Docker disponible
- **CUANDO** se ejecuta el script de arranque
- **ENTONCES** se levantan Kafka, gateway, orquestador, servicios H1–H6, MCP, RAG y agente
- **Y** todos los contenedores reportan estado healthy

#### Escenario: Parada limpia
- **DADO** el entorno levantado
- **CUANDO** se ejecuta el script de parada
- **ENTONCES** todos los contenedores se detienen sin dejar procesos huérfanos

### Requisito: Bootstrap de topics
El sistema DEBE crear los topics del piloto (`wa.message.received.v1`, `wa.message.ready-to-send.v1`, `wa.message.status.v1`, `wa.message.dlq.v1`) al arrancar.

#### Escenario: Topics creados
- **DADO** Kafka healthy
- **CUANDO** se ejecuta el bootstrap
- **ENTONCES** los topics existen con partición por `conversationId` y retención configurada

### Requisito: Reset del entorno
El sistema DEBE proveer un script de reset que detenga contenedores y elimine volúmenes de datos.

#### Escenario: Reset completo
- **DADO** el entorno con datos
- **CUANDO** se ejecuta reset
- **ENTONCES** contenedores y volúmenes se eliminan y el siguiente arranque parte de cero

### Requisito: Configuración por entorno
El sistema DEBE usar `.env` (a partir de `.env.example`) para secretos y parámetros, sin commitear valores reales.

#### Escenario: Sin secretos en el repo
- **DADO** el repositorio
- **CUANDO** se inspecciona
- **ENTONCES** no hay credenciales reales; solo `.env.example` con nombres de variables

### Requisito: Orden de arranque y healthchecks
Los servicios DEBEN arrancar en orden correcto mediante healthchecks y `depends_on`.

#### Escenario: Dependencias respetadas
- **DADO** el compose
- **CUANDO** se levanta
- **ENTONCES** Kafka está healthy antes de gateway/orquestador; servicios antes de MCP/agente

### Requisito: Trazabilidad y observabilidad local
El entorno DEBE propagar `correlationId` y exponer endpoints de salud/métricas de cada componente.

#### Escenario: Health endpoints
- **DADO** el entorno levantado
- **CUANDO** se consulta `/actuator/health` (Java) o `/health` (Python)
- **ENTONCES** responde 200 para cada componente

## Criterios de aceptación

- [ ] Existe `docker-compose.yml` en la raíz con todos los servicios del canal.
- [ ] Scripts `scripts/start.sh` (o `.ps1`), `scripts/stop.sh`, `scripts/reset.sh` (o equivalentes) funcionan.
- [ ] Bootstrap crea los topics del piloto.
- [ ] No hay secretos reales en el repo; solo `.env.example`.
- [ ] Healthchecks y `depends_on` ordenan el arranque.
- [ ] `docker compose config` valida sin errores.
- [ ] Smoke E2E del flujo completo es posible (webhook simulado → respuesta).

## Requisitos no funcionales

- Tiempo de arranque razonable para desarrollo local.
- Consumo de recursos acotado (límites cuando sea posible).
- Reproducibilidad: mismo resultado en entornos limpios.
- Trazabilidad con `correlationId` extremo a extremo.

## ADRs requeridos

- ADR — Topología local multi-contenedor (Compose) vs monolito
- ADR — Kafka KRaft para entorno local

## Dependencias

- Todos los componentes H0.1, H0, H0.2, H1–H6, H7, H8, H9 deben estar construidos.
- Contratos en `shared/contracts/` (eventos, OpenAPI, handoff, turno).

## Supuestos

- Docker Desktop / Docker Engine disponible en el entorno del desarrollador.
- Los Dockerfiles existentes de cada componente se reutilizan.
- Twilio se simula para smoke E2E (no se requiere cuenta real).
- Los servicios Java se empaquetan como JARs en sus imágenes.

## Preguntas abiertas

- Scripts en Bash, PowerShell o ambos (entorno Windows/Linux/Mac).
- Estrategia de datos de prueba (seed) para smoke E2E.
- Nivel de paralelismo de build de imágenes.
- Retención exacta por topic en entorno local.
