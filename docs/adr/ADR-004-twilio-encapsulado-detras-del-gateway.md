# ADR-004 — Twilio encapsulado detrás de un Gateway de canal

**Estado:** Aceptado  
**Fecha:** 2026-10-27  
**Decisión:** Encapsular toda la interacción con Twilio (webhooks, firma, callbacks, envío) en `gateway/wa-twilio-gateway`. El resto de componentes solo consume/publica eventos internos normalizados.

**Contexto:** El canal WhatsApp debe aislar el proveedor (Twilio) para evitar acoplar reglas de negocio/IA al contrato de Twilio, facilitar cambios de proveedor y aplicar políticas de seguridad/trazabilidad centralizadas.

**Consecuencias:**
- Único punto que conoce payload de Twilio.
- Normalización a `MessageReceived`/`MessageStatus` con contrato versionado.
- Seguridad (firma, anti-replay, límites) centralizada.
- H0.2/H9 no dependen del SDK/formato de Twilio.

**Alternativas consideradas:** Integrar Twilio directamente en orquestador/servicios → mayor acoplamiento y mayor superficie de PII.
