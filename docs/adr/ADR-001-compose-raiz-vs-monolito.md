# ADR-001: Topología local multi-contenedor con docker-compose raíz vs monolito

- **Estado**: Aceptado
- **Fecha**: 2026-10-31
- **Contexto**: Necesitamos levantar todo el canal (Kafka, gateway, orquestador, H1–H6, MCP, RAG, agente) para smoke E2E local.
- **Decisión**: Usar un único `docker-compose.yml` en la raíz que orquesta todos los contenedores, construyendo desde Dockerfiles existentes. Cada componente sigue aislado (imagen/contenedor propio).
- **Consecuencias**: Arranque unificado, red compartida, fácil reset con volúmenes. Requiere Docker y recursos locales.
- **Alternativas consideradas**: Composes por componente (actual) — más complejo para E2E; monolito — va en contra de arquitectura del programa.
