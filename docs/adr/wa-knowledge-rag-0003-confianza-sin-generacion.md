# ADR wa-knowledge-rag-0003 — Señal de confianza sin generación de texto

- Estado: aceptada
- Fecha: 2026-11-27
- Componente: `ai/wa-knowledge-rag`

## Contexto

El agente no debe inventar. Quien consulta necesita saber cuándo el contenido alcanza, cuándo pedir aclaración y
cuándo derivar a una persona.

## Decisión

- El componente no usa un LLM: devuelve solo fragmentos aprobados, con cita.
- El score de cada resultado es la similitud coseno de su mejor fragmento (más un bono chico si también coincide por
  texto). Con umbrales configurables se calcula `level` (`alta | baja | sin_respuesta`) y `suggestion`
  (`responder | aclarar | derivar`); `aclarar` cuando hay varias piezas parecidas por debajo del umbral.
- Los umbrales por defecto se calibran con el set dorado del componente.
- El acceso lo da una credencial de servicio por consumidor; ver lo interno depende de su configuración, no de la
  pregunta.

## Consecuencias

- El contrato del agente puede decidir de forma determinística cuándo responder, aclarar o derivar.
- Cambiar el modelo de embeddings obliga a recalibrar los umbrales.
