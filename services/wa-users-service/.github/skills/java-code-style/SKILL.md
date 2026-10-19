---
name: java-code-style
description: >
  Estilo de código Java obligatorio: formato (4 espacios, 120 columnas, K&R), imports, naming estricto
  (clases, métodos, constantes, paquetes, booleanos, idioma), orden dentro de la clase, anotaciones, tests, y
  qué no escribir (nombres genéricos, abreviaturas, comentarios obvios, código comentado). Aplica a
  src/main/java y src/test/java.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: java
  applies-to: src/main/java/**, src/test/java/**
---

# Java — estilo de código (OBLIGATORIO)

Vale para todo archivo `.java` nuevo o modificado. Si el proyecto ya tiene un formateador o reglas propias
(`.editorconfig`, Spotless, Checkstyle), **seguilas**: mandan sobre lo de acá. No agregues plugins de formato al
`pom.xml` sin una tarea que lo pida (ver `maven-dependency-hygiene`). No reformatees archivos que no tocás.

## Formato

- Indentación: **4 espacios**, nunca tabs. Continuación de línea: 8 espacios.
- Largo máximo de línea: **120** caracteres.
- Llaves estilo **K&R**: la de apertura en la misma línea; `else`, `catch` y `finally` en la línea de la llave
  que cierra. Llaves siempre, aunque el bloque tenga una sola línea.
- Un statement por línea; una declaración de variable por línea.
- Una línea en blanco: después de `package`, entre grupos de imports, después de los imports, entre métodos y
  entre grupos de campos. Nunca dos líneas en blanco seguidas.
- Cadenas de streams o builders largas: un paso por línea, alineado con el punto adelante.

```java
package com.bank.users.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bank.users.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_RESULTADOS = 50;

    private final UserRepository repository;
    private final UserMapper mapper;

    public List<UserResponse> listarActivos() {
        return repository.findByStatus(UserStatus.ACTIVO)
                .stream()
                .limit(MAX_RESULTADOS)
                .map(mapper::toResponse)
                .toList();
    }
}
```

## Imports

- **Nunca** imports con comodín (`import java.util.*;`).
- Sin imports sin usar.
- Orden por grupos, separados por una línea en blanco: `java.*` → `jakarta.*`/`javax.*` → terceros
  (`org.*`, `com.*` externos) → paquetes del proyecto → `lombok.*`; los `import static` al final, en su grupo.
  Si el proyecto ya tiene otro orden consistente, seguí ese.

## Naming

| Qué | Convención | Ejemplos |
|-----|------------|----------|
| Clases, records, enums, interfaces | `PascalCase`, sustantivo | `UserService`, `CreateUserRequest`, `UserStatus` |
| Métodos | `camelCase`, verbo | `crearUsuario`, `findById`, `cambiarEstado` |
| Variables y parámetros | `camelCase`, descriptivo | `usuarioId`, `numeroDocumento` |
| Constantes (`static final`) | `UPPER_SNAKE_CASE` | `MAX_RESULTADOS`, `DEFAULT_PAGE_SIZE` |
| Valores de enum | `UPPER_SNAKE_CASE` | `ACTIVO`, `BAJA_LOGICA` |
| Paquetes | minúsculas, sin guiones ni `_` | `com.bank.users.user.dto` |
| Tipos genéricos | una mayúscula | `T`, `E`, `K`, `V` |
| Sufijos por capa | según `architecture.md` | `*Controller`, `*Service`, `*Repository`, `*Mapper`, `*Request`, `*Response`, `*Exception` |

- **Métodos que devuelven boolean**: prefijo `is`/`has`/`can` (o `es`/`tiene`/`puede` si el dominio está en
  español): `isEligible()`, `tieneDocumentoDuplicado()`.
- **Campos boolean** de entidades y DTOs: sin prefijo `is` (Lombok ya genera `isX()` y el JSON queda limpio):
  `activo`, no `isActivo`.
- **Idioma**: el del proyecto. Si el dominio ya está en español (`tipoDocumento`, `AuditOperacion`), los conceptos
  nuevos del dominio también; los términos técnicos (`Repository`, `Mapper`, `Request`) quedan en inglés. Nunca
  dos nombres para el mismo concepto (`usuario` y `user` para lo mismo).
- Acrónimos como palabra: `UserId`, `HttpClient`, `parseJson` (no `UserID`, `HTTPClient`).

## Prohibido

- Nombres de una letra, salvo índices de loops y parámetros de lambdas cortas (`i`, `e`, `u -> ...`).
- Nombres genéricos sin contexto: `data`, `info`, `obj`, `temp`, `result`, `aux`, `item`, `manager`, `util`.
- Abreviaturas ambiguas: `usr`, `acc`, `srv`, `doc`, `cfg` (escribir `usuario`, `cuenta`, `servicio`,
  `documento`, `configuracion`).
- Comentarios obvios (`// getter`, `// constructor`, `// incrementa i`): el comentario explica el **porqué**.
- Código comentado: se borra (está en git).
- Clases "utilitarias" genéricas (`Utils`, `Helper`) cuando la lógica pertenece a una clase del dominio.

## Orden dentro de una clase

1. Constantes `static final`
2. Campos estáticos (evitarlos si no son constantes)
3. Campos de instancia (los `final` inyectados primero)
4. Constructores (o `@RequiredArgsConstructor`)
5. Métodos públicos (el flujo principal primero)
6. Métodos protegidos / de paquete
7. Métodos privados (cerca del público que los usa)
8. Clases internas y records internos (evitar; si hacen falta, al final)

## Anotaciones

- Una anotación por línea en clases, métodos y campos; antes de los modificadores.
- Orden en clases: las de Spring (`@RestController`, `@Service`, `@RequestMapping`) → las de Lombok
  (`@RequiredArgsConstructor`, `@Slf4j`) → las de documentación (`@Tag`).
- En parámetros pueden ir en la misma línea: `@PathVariable UUID id`, `@Valid @RequestBody CreateUserRequest request`.

## Tests

- Clase: `<ClaseProbada>Test` (unitarios) o `<Caso>IntegrationTest` (integración).
- Método: describe el comportamiento en `camelCase`, sin `test` adelante: `creaUsuarioCuandoElDocumentoEsNuevo`,
  `devuelve404CuandoElUsuarioNoExiste`; y `@DisplayName` en español con la frase completa.
- Estructura dada / cuando / entonces (Given / When / Then), separada por una línea en blanco.

## Cómo revisarlo rápido

```bash
rg -n "\t" --glob "*.java" src                       # tabs
rg -n ".{121,}" --glob "*.java" src                  # líneas de más de 120
rg -n "^import .*\.\*;" --glob "*.java" src          # imports con comodín
rg -n "^\s*//\s*(getter|setter|constructor)" -i --glob "*.java" src
rg -n "\b(data|info|obj|temp|aux)\b\s*=" --glob "*.java" src   # nombres genéricos
```

(Sin `rg`: `grep -rnE` con los mismos patrones.) Si el IDE o el proyecto tienen formateador, pasalo sobre los
archivos que tocaste.

## Checklist antes de terminar

- [ ] 4 espacios, ≤120 columnas, llaves K&R, sin líneas en blanco dobles
- [ ] Imports sin comodines, sin usar ni desordenados
- [ ] Naming según la tabla, en el idioma del proyecto, sin abreviaturas ni nombres genéricos
- [ ] Orden de la clase respetado
- [ ] Sin comentarios obvios ni código comentado
- [ ] Tests con nombres de comportamiento y `@DisplayName`
