# ADR-014: Verificación funcional del runtime local Compose

- Estado: Aceptado
- Fecha: 2026-10-31
- Alcance: ampliación de specs/local-devops/arquitectura.md para el run 20261031-f8ae.

## Contexto

El Compose raíz y scripts existen. start ejecuta up en segundo plano y la guía
enumera el recorrido sin un envío reproducible ni captura de la respuesta.
La salud de proceso no prueba el acceso al modelo, herramientas o servicios.
El cliente outbound del gateway no expone actualmente destino local del simulador.

## Decisión

Conservar componentes, contratos, Kafka KRaft y persistencia existentes. Añadir
en los pasos de ejecución una espera acotada de salud y un arnés externo que
firme el webhook, capture la API Twilio simulada y contraste la respuesta con
datos sintéticos preparados por las APIs del banco. Mantener el SDK oficial y
habilitar sustitución del transporte solo en local, sin alterar semántica de envío.

La prueba completa exige H9 real y una llamada H7 a un servicio real del stack.
Un saludo puede servir como prueba adicional de transporte. Los dobles del modelo
o respuestas de contingencia se informan como cobertura parcial. No se modifican
topics, retenciones, estados de saga ni políticas de retry para esta verificación.

## Consecuencias

Se necesita Docker accesible y credencial del modelo para acreditar todo el flujo.
El simulador evita cuenta Twilio real y permite observar el efecto de salida.
La evidencia incluye correlación, resultado del servicio y captura outbound;
excluye secretos y datos reales. Los inicializadores se evalúan por código de salida,
los procesos permanentes por salud y el canal por su resultado funcional.

## Alternativas consideradas

- Solo healthchecks: insuficientes para acreditar mensaje y respuesta.
- Solo pruebas con H9 simulado: útiles para transporte, cobertura parcial del pedido.
- Twilio real: fuera del alcance local aprobado y requiere recursos externos.
