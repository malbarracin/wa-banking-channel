# Contrato de retrieval — wa-knowledge-rag (H8)

**Versión:** v1  
**Consumidor:** H9 (Agente conversacional)  
**Publicado por:** H8 (Base de conocimiento RAG)  
**Ubicación:** `shared/contracts/wa-knowledge-rag/retrieval.md`

## Convención

- Cambio incompatible → nueva versión (v2) conviviendo con v1 hasta migración de H9.
- Toda consulta debe incluir `correlationId` para trazabilidad de punta a punta.
- Sin stack traces ni detalles internos en errores.

## Entrada

```json
{
  "query": "string (obligatorio)",
  "audience": "public|internal (obligatorio)",
  "top_k": "number (opcional, default 5)",
  "correlationId": "string (obligatorio)"
}
```

**Reglas:**
- `audience: "public"` → solo se devuelven fragmentos con `audience=public`. Obligatorio para respuestas directas al cliente.
- `audience: "internal"` → puede usarse para razonamiento/derivación por H9/soporte, nunca para exponer al cliente.
- `query` no debe contener PII en logs; H8 no almacena datos transaccionales.

## Salida exitosa

```json
{
  "results": [
    {
      "content": "string",
      "citation": {
        "source_id": "string (uuid)",
        "title": "string",
        "section": "string|null",
        "version": "number",
        "source_uri": "string|null"
      },
      "score": "number (0-1)"
    }
  ],
  "confidence": "high|medium|low",
  "version": "string (identificador de versión de KB)",
  "correlationId": "string"
}
```

**Notas:**
- Cada resultado incluye `citation` obligatoria para trazabilidad/auditoría.
- `confidence` se calcula con umbrales configurables, calibrados con set dorado.
- `results` puede estar vacío si no hay respuesta suficiente.

## Salida sin respuesta suficiente

```json
{
  "results": [],
  "confidence": "low",
  "reason": "sin_respuesta_suficiente",
  "correlationId": "string"
}
```

**Regla crítica:** Si `confidence == "low"` el consumidor **NO DEBE** inventar respuesta. Debe derivar a soporte humano o pedir aclaración según reglas transversales.

## Errores

Sin detalles internos. Ejemplos:
```json
{
  "error": {
    "code": "CONSULTA_INVALIDA|SERVICIO_NO_DISPONIBLE",
    "message": "mensaje de negocio claro, sin stack trace"
  },
  "correlationId": "string"
}
```

## Reglas de uso

1. **Solo contenido aprobado:** H8 solo retorna chunks de fuentes `status=approved` y versión vigente.
2. **Separación público/interno:** nunca usar resultados `internal` para texto directo al cliente.
3. **Citas obligatorias:** para respuestas informativas basadas en conocimiento aprobado, usar citas provistas.
4. **No inventar:** ante `confidence: low` o sin resultados, no generar información no recuperada.
5. **Prevalencia de reglas vigentes:** ante contradicción con política H4–H6, prevalece regla del banco vigente.
6. **Sin PII:** ni entrada ni salida deben exponer datos personales/transaccionales del cliente.
7. **Trazabilidad:** siempre propagar y registrar `correlationId`.
