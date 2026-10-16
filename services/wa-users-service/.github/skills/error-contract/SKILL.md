---
name: error-contract
description: >
  Contrato de errores único de la API: un solo JSON de error (code, message, details, timestamp) para todos los
  endpoints, GlobalExceptionHandler con @RestControllerAdvice en exception/, mapeo de excepciones a códigos HTTP
  (400 VALIDATION_ERROR / BAD_REQUEST, 404 NOT_FOUND, 409 CONFLICT, 500 INTERNAL_ERROR), excepciones de dominio
  y nunca stacktrace al cliente. Fuente única del contrato que usan dev, tester, reviewer y documentador.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: spring-boot-3
  applies-to: "src/main/java/**/exception/**, src/main/java/**/controller/**"
---

# Contrato de errores

Todos los endpoints de todas las versiones responden los errores **con el mismo JSON**. Lo crea el scaffold
(`backend-java-scaffold`, paquete `exception/`) y cada feature solo agrega sus excepciones de dominio.

## La respuesta de error

```json
{
  "code": "VALIDATION_ERROR",
  "message": "La solicitud tiene datos inválidos",
  "details": [
    { "field": "numeroDocumento", "message": "no debe estar vacío" }
  ],
  "timestamp": "2026-10-05T14:30:00Z"
}
```

| Campo | Qué lleva |
|-------|-----------|
| `code` | Uno de los códigos de la tabla de abajo (enum `ErrorCode`), estable: los clientes lo usan para decidir |
| `message` | Texto para una persona, en el idioma del proyecto; sin datos internos (SQL, clases, rutas) |
| `details` | Lista, **vacía** si no hay nada que detallar (nunca `null`). En validación: un `{field, message}` por campo |
| `timestamp` | ISO-8601 en UTC (`Instant`) |

En código: `record ErrorResponse(ErrorCode code, String message, List<FieldError> details, Instant timestamp)`
y `record FieldError(String field, String message)`, en `<base_package>/exception/`.

## Códigos y mapeo (mínimo)

| HTTP | `code` | Cuándo / qué excepción |
|------|--------|------------------------|
| 400 | `VALIDATION_ERROR` | Bean Validation: `MethodArgumentNotValidException` (body con `@Valid`), `ConstraintViolationException` (`@Validated` en params) → `details` con cada campo |
| 400 | `BAD_REQUEST` | `IllegalArgumentException`, JSON mal formado (`HttpMessageNotReadableException`), tipo de parámetro inválido (`MethodArgumentTypeMismatchException`), parámetro obligatorio faltante (`MissingServletRequestParameterException`) |
| 404 | `NOT_FOUND` | El recurso no existe: excepción de dominio (`UserNotFoundException extends NotFoundException`) |
| 409 | `CONFLICT` | Duplicados y estado inválido: `DuplicateUserException`, `InvalidStatusTransitionException` (`extends ConflictException`) |
| 500 | `INTERNAL_ERROR` | Cualquier otra (`Exception`): mensaje genérico, sin detalle |

Si la especificación pide otro caso (ej. 422, 403), se agrega un código nuevo al enum y una fila acá; nunca un
formato de error distinto.

## `GlobalExceptionHandler` (obligatorio)

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "La solicitud tiene datos inválidos", details);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Error inesperado", ex);   // el stacktrace va al log, nunca al cliente
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Error interno", List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
            List<FieldError> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, details, Instant.now()));
    }
}
```

(Más los handlers de `BAD_REQUEST` y `ConstraintViolationException` de la tabla.) Es el **único** lugar donde
se permite `catch`/handler de `Exception` genérica (`java-best-practices`).

## Excepciones de dominio

- Dos bases en `exception/`: `NotFoundException` y `ConflictException` (abstractas, `extends RuntimeException`).
- Cada módulo crea las suyas en su paquete (`user/exception/` o junto al service) extendiendo esas bases:
  el handler no cambia cuando aparece una nueva.
- El service **lanza**; el controller **no** atrapa excepciones ni arma errores a mano.
- El mensaje explica el caso sin datos sensibles: `"No existe el usuario " + id`, no el documento completo.

## Nunca exponer internals

- Sin stacktrace, nombres de clases, SQL ni mensajes de excepciones de terceros en la respuesta.
- `application.yml`: `server.error.include-stacktrace: never` y `server.error.include-message: never` (por si
  algo escapa al handler).
- No usar `ProblemDetail` de Spring en paralelo: el contrato es este JSON (dejá
  `spring.mvc.problemdetails.enabled` en `false`, que es el valor por defecto).

## Quién hace qué

| Rol | Qué le toca |
|-----|-------------|
| Arquitecto (plan) | Lista los errores de cada endpoint (código HTTP + `code` + caso) y las excepciones de dominio nuevas |
| Dev | Crea las excepciones de dominio y las lanza desde el service; el handler ya existe (scaffold) |
| Tester | Por endpoint, al menos un test de error que verifique status **y** cuerpo: `$.code`, `$.details`, `$.timestamp` |
| Reviewer | Formato único, sin stacktrace, sin `try/catch` en controllers, sin errores armados a mano |
| Documentador | Ejemplos de error en Swagger (`components.examples`, skill `api-openapi-docs`), README y colecciones con este mismo JSON |

Test de ejemplo:

```java
mockMvc.perform(get("/api/v1/users/{id}", idInexistente))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.details").isArray())
        .andExpect(jsonPath("$.timestamp").exists());
```

## Checklist

- [ ] `ErrorResponse`, `FieldError`, `ErrorCode` y `GlobalExceptionHandler` (`@RestControllerAdvice`) en `exception/`
- [ ] Cada excepción de la tabla mapeada a su HTTP y `code`; `details` nunca `null`
- [ ] Excepciones de dominio extienden `NotFoundException` / `ConflictException`
- [ ] Sin `try/catch` en controllers; sin stacktrace ni internals en la respuesta
- [ ] `server.error.include-stacktrace: never`
- [ ] Tests de error verifican status y cuerpo
