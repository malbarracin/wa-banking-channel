---
name: java-best-practices
description: >
  Buenas prácticas Java obligatorias y anti-patterns prohibidos (Java 21 + Spring Boot 3): null safety con
  Optional, inmutabilidad con records, excepciones de dominio, logging SLF4J sin datos sensibles, colecciones por
  interfaz, inyección por constructor, sin código muerto ni magic numbers. Aplica a src/main/java y src/test/java.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: java-21, spring-boot-3
  applies-to: src/main/java/**, src/test/java/**
---

# Java — buenas prácticas (OBLIGATORIO)

Valen para todo código nuevo o modificado en `src/main/java/**` y `src/test/java/**`. No reescribas código que
no tocás solo para cumplirlas (fuera de alcance), salvo que sea un bug.

## Null safety

- **Nunca** devolver `null` desde un método público:
  - búsquedas que pueden no encontrar → `Optional<T>`;
  - colecciones → vacía (`List.of()`), nunca `null`;
  - si la ausencia es un error → excepción de dominio (`UserNotFoundException`) o `IllegalArgumentException`.
- **Nunca** `Optional.get()` sin verificar: usar `orElseThrow(...)`, `map`, `ifPresent`. Nunca `Optional` como
  campo ni como parámetro.
- No pasar `null` como argumento salvo que la API lo pida explícitamente.
- Fail-fast en constructores y fábricas: `Objects.requireNonNull(x, "x")`.

## Inmutabilidad

- **DTOs (request/response): `record`** (Java 21) en código nuevo. Si el módulo ya usa Lombok `@Value`/`@Builder`
  para sus DTOs, seguí esa convención (ver `code-quality.md`): lo importante es que sean inmutables.
  Validaciones de Bean Validation en los componentes del record; MapStruct mapea records sin problema.
- Si no puede ser record (ej. entidad JPA): campos `private`, sin setters públicos innecesarios; con Lombok,
  `@Getter` y los setters puntuales que el dominio necesita (ver `stack.md` para entidades).
- Colecciones expuestas: `List.copyOf(...)` / `Collections.unmodifiableList(...)`.
- Nunca mutar los argumentos de entrada.

## Excepciones

- **Nunca** `catch (Exception e)` genérico, salvo en el `@RestControllerAdvice` (GlobalExceptionHandler).
- **Nunca** tragarse una excepción (catch vacío o solo log sin relanzar).
- **Nunca** `throw new RuntimeException(e)`: crear excepciones de dominio (`DuplicateUserException`,
  `InvalidStatusTransitionException`) que el handler traduce al contrato de error.
- Si transformás una excepción, conservá la causa (`new XException(msg, e)`) y logueá una sola vez (no
  log-and-rethrow en cada capa).

Correcto:

```java
public UserResponse findById(UUID id) {
    return repository.findById(id)
        .map(mapper::toResponse)
        .orElseThrow(() -> new UserNotFoundException(id));
}
```

Incorrecto:

```java
public UserResponse findById(UUID id) {
    try {
        return mapper.toResponse(repository.findById(id).get()); // .get() sin verificar
    } catch (Exception e) {
        return null;                                             // se traga el error y devuelve null
    }
}
```

## Java 21

- `switch` como expresión y con **pattern matching** en vez de cadenas de `if/instanceof`:

```java
String detalle = switch (error) {
    case UserNotFoundException e -> "No existe el usuario " + e.getId();
    case DuplicateUserException e -> "Documento ya registrado";
    default -> "Error inesperado";
};
```

- `sealed` para jerarquías cerradas (ej. resultados o eventos de dominio); `record` con validación en el
  constructor compacto para value objects.
- `var` solo cuando el tipo es obvio a la derecha (`var users = new ArrayList<User>()`), nunca para ocultar un
  tipo importante.
- Text blocks (`"""`) para JSON o SQL largos en tests.

## Strings

- Comparación con literal: `"ACTIVO".equals(valor)` (o `Objects.equals(a, b)`), nunca `valor.equals("ACTIVO")`.
- Enums en vez de strings para estados y tipos (`UserStatus`, `TipoDocumento`).
- Concatenación dentro de loops: `StringBuilder`. Literales repetidos: `private static final`.

## Colecciones y streams

- Declarar por interfaz: `List<T>`, `Map<K, V>`, `Set<T>` (nunca `ArrayList<T>` como tipo de variable/campo).
- Streams para transformar (`map`/`filter`/`toList()`); más de dos niveles anidados → extraer un método.
- `stream().toList()` (Java 16+) devuelve inmodificable: usarlo por defecto.

## Logging

- **Nunca** `System.out` / `System.err` ni `e.printStackTrace()`.
- SLF4J (`@Slf4j` de Lombok) con placeholders: `log.info("Usuario creado: {}", id)` — no concatenar.
- **Nunca** loguear datos sensibles: documento completo, email, teléfono, tokens, contraseñas (enmascarar si hace
  falta: `30****22`).
- Niveles: `ERROR` falla inesperada · `WARN` validación fallida o dato sospechoso · `INFO` operación exitosa del
  flujo principal · `DEBUG` detalle para diagnóstico.

## Inyección y constructores

- Inyección por constructor: `@RequiredArgsConstructor` (Lombok) con campos `private final`. **Nunca**
  `@Autowired` en campos.
- Un solo constructor: Spring lo detecta solo.

## Primitivos y wrappers

- Entidades: wrappers (`Long`, `Boolean`) si la columna admite null; primitivos si no.
- Cálculos locales: primitivos.
- **Nunca** comparar wrappers con `==` (usar `equals` / `Objects.equals`).

## Tests

- Las mismas reglas (sin `System.out`, sin `Thread.sleep`: usar Awaitility si hay asincronía).
- AssertJ (`assertThat`) y nombres que describen el comportamiento; un comportamiento por test.
- Sin lógica condicional en los tests; datos de prueba claros (builders o text blocks).

## Prohibido

- `System.out` / `System.err` · `e.printStackTrace()`
- `Thread.sleep()` en producción
- `catch (Exception e)` fuera del handler global · catch vacío · `throw new RuntimeException(e)`
- `return null` en métodos públicos · `Optional.get()` sin verificar
- `@Autowired` en campos
- Reflection manual sin justificar
- Código muerto (métodos, clases, imports sin usar) y código comentado
- `TODO`/`FIXME` sin referencia a una tarea
- Magic numbers y strings: constantes con nombre o enums

## Cómo revisarlo rápido

```bash
rg -n "System\.(out|err)|printStackTrace|Thread\.sleep" src/main/java
rg -n "catch \(Exception |catch \(Throwable |new RuntimeException\(" src/main/java
rg -n "return null;|\.get\(\);|@Autowired" src/main/java
rg -n "TODO|FIXME" src
```

(Sin `rg`: `grep -rnE` con los mismos patrones.) Cada resultado se corrige o se justifica (ej. el handler global).

## Checklist antes de terminar

- [ ] Ningún método público devuelve `null`; `Optional` bien usado
- [ ] DTOs como `record`; colecciones expuestas inmodificables
- [ ] Excepciones de dominio, sin catch genérico ni tragadas
- [ ] SLF4J con placeholders, sin datos sensibles
- [ ] Inyección por constructor
- [ ] Sin magic numbers, código muerto ni TODO sin tarea
- [ ] Los `rg` de "Cómo revisarlo rápido" (en la skill `java-best-practices`) sin hallazgos (o justificados)
