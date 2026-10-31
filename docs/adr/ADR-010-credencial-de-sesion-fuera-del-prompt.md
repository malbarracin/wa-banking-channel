# ADR-010 — La credencial de sesión (H2) viaja fuera del prompt del modelo y H7 no la persiste

**Estado:** Propuesto
**Fecha:** 2026-10-30
**Contexto:** El contrato provisional de turno H0.2 → H9 (`platform/wa-channel-orchestrator/H9.md`) no incluye la
credencial de sesión: H0.2 explícitamente no guarda credenciales en su contexto técnico
(`shared/contracts/wa-channel-orchestrator/handoff.md`). Cada invocación de una capacidad de producto (H4/H5/H6)
necesita, sin embargo, una credencial JWT vigente. Si el modelo tuviera que leer, repetir o decidir el valor de
la credencial dentro del prompt, quedaría expuesta a fuga por inyección de prompt y a aparecer en memoria de
largo plazo o logs (riesgo OWASP LLM "divulgación de información sensible").

**Decisión:**
1. H9 resuelve y cachea la credencial vigente en su propia memoria de sesión (Redis, `RedisSessionMemoryService`),
   nunca en el historial de conversación que ve el modelo ni en memoria de largo plazo.
2. El ejecutor de herramientas de H9 (código determinístico, no el LLM) completa el campo `contexto.credencial`
   del sobre MCP antes de invocar cualquier capacidad de producto; el modelo solo decide **qué** capacidad invocar
   y con **qué parámetros de negocio**, nunca con qué credencial.
3. H7 recibe la credencial, la reenvía al servicio origen (quien la valida contra H2) o la usa para armar el
   contexto que H3 espera, y la descarta: no la persiste, no la logea completa ni parcial, y no la repite en la
   respuesta salvo en las dos capacidades donde el propio H2 la emite.
4. Una credencial revocada o vencida se refleja de inmediato: la siguiente llamada del servicio origen a H2
   rechaza, y H7 traduce ese rechazo sin necesitar sincronizar un caché propio de vigencia.

**Consecuencias:**
- Cumple el requisito transversal de la spec: "MCP deja de autorizar operaciones de ese cliente en el mismo
  minuto operativo" sin estado adicional en H7.
- El diseño de memoria de H9 (TTL de sesión, qué se guarda a largo plazo) queda condicionado a nunca incluir la
  credencial en lo que persiste más allá de la sesión activa — a resolver en detalle por el arquitecto de H9
  (`adk-redis-memory`).
- H7 queda sin estado propio de sesión: más simple de operar y de auditar (logs, no DB).

**Alternativas consideradas:**
- H0.2 reenvía la credencial en cada turno: rechazada — ya está descartada por la implementación de H0.2
  (no guarda credenciales) y mezclaría estado de producto con el contexto técnico de la saga.
- H7 cachea la credencial validada para evitar llamadas repetidas a H2: rechazada por ahora — agregaría estado
  propio a H7 fuera del stack permitido (ia-stack.md no le da Redis ni Postgres a H7) y un riesgo de servir una
  credencial ya revocada.

**Aplicación:** Seguir `llm-security.md` y `ia-boundaries.md`. Cualquier capacidad de H7 cuyo esquema incluya
`contexto.credencial` la marca `"x-sensible": true`; el ejecutor de herramientas de H9 y los logs de H7 deben
respetar esa marca.
