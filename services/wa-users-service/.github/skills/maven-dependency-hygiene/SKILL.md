---
name: maven-dependency-hygiene
description: >
  Maven: wrapper obligatorio (./mvnw), POM mínimo, versiones del BOM de Spring Boot sin hardcodear, terceros
  centralizados en <properties>, sin colisiones ni duplicados, scopes correctos y nada sin usar. Aplica siempre
  que se crea o toca un pom.xml y al compilar o probar.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: maven
  applies-to: pom.xml
---

# Maven: wrapper y dependencias (OBLIGATORIO)

## Maven Wrapper

Usá **siempre** el wrapper del proyecto, nunca un `mvn` global:

| Sistema | Comando |
|---------|---------|
| Linux / macOS / Git Bash | `./mvnw <goals>` |
| Windows (cmd / PowerShell) | `mvnw.cmd <goals>` (o `.\mvnw <goals>`) |

Si el proyecto **no tiene wrapper** (`mvnw`, `mvnw.cmd` y `.mvn/wrapper/maven-wrapper.properties`):

1. Generalo una sola vez con el Maven instalado: `mvn -N wrapper:wrapper` (en un proyecto nuevo, apenas
   creás el `pom.xml`), y commitealo junto con el resto.
2. Si no hay Maven instalado para generarlo, **no sigas compilando con otra cosa**: respondé `bloqueado`
   explicando que falta el wrapper y el comando para generarlo.

## Principios

- El POM es **mínimo**: solo dependencias que el código usa, cada una con un motivo claro.
- El **BOM de Spring Boot** maneja las versiones: no se hardcodea lo que ya cubre.

## Versiones

- Parent `spring-boot-starter-parent` (Spring Boot 3.x) → hereda el BOM.
- **Sin `<version>`** en los starters ni en nada que gestione el BOM (`spring-boot-starter-*`, Jackson,
  Hibernate, Flyway, H2, PostgreSQL driver, Lombok, JUnit, Mockito, AssertJ, Testcontainers…).
- **Con versión** solo lo que está fuera del BOM (springdoc, MapStruct, plugins de terceros), y siempre desde
  `<properties>`:

```xml
<properties>
    <java.version>21</java.version>
    <springdoc.version>2.6.0</springdoc.version>
    <mapstruct.version>1.6.3</mapstruct.version>
</properties>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <!-- sin version: la da el parent -->
</dependency>

<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

Antes de fijar la versión de un tercero, revisá que sea compatible con la versión de Spring Boot del parent
(ej. springdoc 2.x para Spring Boot 3).

## Evitar colisiones

- **No** declarar transitivas que ya trae un starter (ej. no agregar `jackson-databind` si está
  `spring-boot-starter-web`; no agregar `hibernate-core` si está `spring-boot-starter-data-jpa`).
- **No** mezclar versiones de una misma familia (ej. `spring-web` 6.1 con `spring-core` 6.0).
- Si hay un conflicto: `<exclusions>` en la dependencia que trae la transitiva problemática.
- Ante la duda: `./mvnw -q dependency:tree -Dverbose` y revisá los "omitted for conflict".

## Dependencias de test

- Siempre `<scope>test</scope>`.
- `spring-boot-starter-test` ya trae JUnit 5, Mockito, AssertJ, Hamcrest y JSONassert: **no** se agregan aparte.
- Testcontainers y H2 para tests: solo en `test`.

## Solo en compilación

- **Lombok**: `<optional>true</optional>`, y en `annotationProcessorPaths` del `maven-compiler-plugin`.
- **MapStruct processor**: solo en `annotationProcessorPaths` (junto con `lombok` y
  `lombok-mapstruct-binding`, en ese orden), nunca como dependencia.

## Prohibido

- Dependencias sin usar.
- Versiones `SNAPSHOT` en código que va a un PR.
- La misma dependencia (`groupId:artifactId`) declarada dos veces.
- Repositorios no oficiales sin justificar (Maven Central alcanza en casi todo).
- Versiones hardcodeadas de lo que gestiona el BOM.

## Cómo verificarlo

```bash
./mvnw -q dependency:analyze        # "Unused declared" = sobran · "Used undeclared" = faltan (declarar explícitas)
./mvnw -q dependency:tree -Dverbose # conflictos y duplicados
./mvnw -q -B verify                 # compila, tests y cobertura
```

Ojo con `dependency:analyze`: marca como "sin usar" a los starters y a las dependencias que solo se usan en
tiempo de ejecución (driver JDBC, actuator, Flyway): esas se quedan. Lo que sí hay que sacar es una
biblioteca que el código no usa ni la necesita en ejecución.

## Checklist antes de terminar

- [ ] `./mvnw` (y `mvnw.cmd`) presentes y funcionando
- [ ] Sin dependencias sin usar ni duplicadas
- [ ] Sin versiones hardcodeadas de starters ni de lo que gestiona el BOM
- [ ] Versiones de terceros en `<properties>`
- [ ] Todas las de test con `<scope>test</scope>`; Lombok `optional`; processors en `annotationProcessorPaths`
- [ ] `./mvnw -q dependency:tree -Dverbose` sin conflictos de versión
- [ ] `./mvnw -q -B verify` pasa
