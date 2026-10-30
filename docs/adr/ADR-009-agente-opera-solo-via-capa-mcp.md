# ADR-009 — El agente conversacional (H9) opera sobre H1–H6 únicamente a través de la capa MCP (H7)

**Estado:** Propuesto
**Fecha:** 2026-10-30
**Contexto:** H9 necesita consultar y operar productos bancarios (cuentas H4, tarjetas H5, seguros H6) y
capacidades de canal/identidad (H1–H3) para responder y actuar en nombre del cliente. Si H9 llamara directo a
esos servicios duplicaría validación de credencial (H2) y vínculo (H3), mapeo de errores y auditoría — y
rompería el límite que fija el roadmap entre "entender y conversar" (H9) e "integrar con los servicios" (H7).

**Decisión:** H9 no tiene clientes HTTP hacia `wa-users-service`, `wa-credentials-service`, `wa-channel-service`,
`wa-accounts-service`, `wa-cards-service` ni `wa-insurance-service`. Toda acción sobre esos servicios pasa por
una capacidad publicada en el catálogo de `ai/wa-mcp-layer` (`shared/contracts/wa-mcp-layer/capabilities.md`),
invocada por el ejecutor de herramientas de H9 (ADK `Agent`/`Runner`), nunca por una llamada de red armada por el
modelo o por código de H9 fuera del cliente MCP.

**Consecuencias:**
- La lógica de negocio y la validación de sesión quedan en un solo lugar (H7 + los propios H1–H6); H9 no la
  reimplementa.
- Agregar o cambiar una capacidad es un cambio en el catálogo de H7, versionado (`_v1` → `_v2`), sin tocar H9
  hasta que migre.
- Si el catálogo no cubre algo que H9 necesita, es una capacidad nueva a pedir a H7, no un atajo directo a H1–H6.
- Limita la "agencia" del modelo a las herramientas que el catálogo expone (`tool_filter`), controlando el riesgo
  de agencia excesiva (OWASP LLM).

**Alternativas consideradas:**
- H9 con clientes HTTP propios hacia H1–H6: rechazada — duplica validación de credencial/vínculo y mezcla
  responsabilidades de integración con las de conversación (viola `ia-boundaries.md`).
- H7 como simple proxy sin catálogo versionado (pass-through genérico): rechazada — no permite auditar "quién
  puede invocar qué" ni deshabilitar capacidades por política, requisitos explícitos de la spec de H7.

**Aplicación:** Seguir `ia-boundaries.md` y `ia-stack.md`; todo cliente HTTP hacia H1–H6 vive en
`ai/wa-mcp-layer/src/wa_mcp_layer/clients/`, nunca en `ai/wa-conversation-agent/`.
