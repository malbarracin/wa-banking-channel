# ADR-005 — El webhook se confirma antes de completar el procesamiento de negocio

**Estado:** Aceptado  
**Fecha:** 2026-10-27  
**Decisión:** El webhook de Twilio debe responder ACK (2xx) lo antes posible tras validación, normalización y publicación exitosa del evento, sin esperar el procesamiento completo del flujo conversacional.

**Contexto:** Twilio exige respuesta rápida a webhooks; el procesamiento (orquestación, agente) es asíncrono y puede tardar. Bloquear el webhook aumenta riesgo de reintentos y timeouts.

**Consecuencias:**
- Respuesta ACK rápida cumple con timeout de Twilio.
- Desacoplamiento vía Kafka (at-least-once).
- Requiere idempotencia (webhook + consumidores).
- Errores downstream no afectan ACK si ya se publicó correctamente.

**Alternativas consideradas:** Procesamiento síncrono completo → riesgo de timeouts/reintentos masivos de Twilio.
