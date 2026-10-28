---
name: backend-java-scaffold
description: >
  Base del servicio Java / Spring Boot desde cero (CREATE_FROM_ZERO): Maven Wrapper, pom.xml, application.yml,
  clase principal, paquetes por módulo y capa, .gitignore, README inicial y, si se pide, configuración para
  imagen nativa. En un proyecto existente (CHANGE_EXISTING) solo toca la base si la tarea lo requiere. Usa las
  plantillas pom.xml, application.yml y Application.java de la biblioteca.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: spring-boot-3, java-21, maven
---

# Scaffold del servicio

## Cuándo

- **CREATE_FROM_ZERO** — la carpeta del servicio no tiene `pom.xml`: armás la base completa (abajo). Es la
  **primera tarea** del proyecto; las demás dependen de ella.
- **CHANGE_EXISTING** — ya hay `pom.xml`: **no** rehagas la base. Solo tocá lo de scaffold que la tarea pida
  (ej. agregar un perfil, un módulo nuevo con sus paquetes, la optimización nativa).

## Parámetros (de `project.yaml`, nunca inventados)

| Parámetro | De dónde sale | Por defecto |
|-----------|---------------|-------------|
| `PROJECT_NAME`, `ARTIFACT_ID` | `project.name` | — (obligatorio) |
| `DESCRIPTION` | `project.description` | — |
| `GROUP_ID` | `java.group_id` | — (obligatorio) |
| `BASE_PACKAGE` | `java.base_package` | — (obligatorio) |
| `JAVA_VERSION` | `java.version` | **21** |
| `SPRING_BOOT_VERSION` | `java.spring_boot` | la última estable de Spring Boot 3.x |
| `DATABASE` | `service.database` (`postgresql`, `mysql`, `mongodb`, `h2`) | `postgresql` |
| `PORT` | `service.port` | `8080` |
| `OPTIMIZATION` | `service.optimization` (`none`, `native`) | `none` |
| `API_VERSIONING` | `api.versioning.enabled` | `false` (todo bajo `/api/v1`) |
| `APP_CLASS` | nombre del servicio en PascalCase + `Application` | ej. `UsersServiceApplication` |

Si falta un obligatorio (`group_id`, `base_package`), no lo inventes: respondé `bloqueado` diciendo qué completar
en `project.yaml` (ver `java-project-identity.md`).

## Base completa (CREATE_FROM_ZERO)

En la carpeta del servicio (donde va el `pom.xml`):

1. **`pom.xml`** desde la plantilla **`pom.xml`**, reemplazando sus placeholders. Ajustá la persistencia a
   `DATABASE` y sacá lo que el servicio no use (`maven-dependency-hygiene`).
2. **Maven Wrapper**: `mvn -N wrapper:wrapper` (deja `mvnw`, `mvnw.cmd` y `.mvn/wrapper/`). Desde acá, siempre
   `./mvnw`.
3. **`src/main/resources/application.yml`** desde la plantilla **`application.yml`**; y
   `src/test/resources/application.yml` con H2 en memoria para los tests.
4. **Clase principal** desde la plantilla **`Application.java`**, en `src/main/java/<BASE_PACKAGE>/`.
5. **Paquetes** según `architecture.md` (por módulo de dominio, con capas adentro):

```
src/main/java/<BASE_PACKAGE>/
├── <APP_CLASS>.java
├── config/                    # configuración transversal (OpenAPI, beans)
├── exception/                 # GlobalExceptionHandler + ErrorResponse + excepciones compartidas
└── <modulo>/                  # uno por módulo de dominio de la especificación (ej. user)
    ├── controller/
    ├── service/
    ├── repository/
    ├── mapper/
    ├── entity/
    └── dto/
src/test/java/<BASE_PACKAGE>/
└── <APP_CLASS>Test.java       # contextLoads
```

   Con `api.versioning.enabled: true`, `controller/`, `dto/` y `mapper/` del módulo van en `api/v1/` (skill
   `api-versioning`). Creá solo los módulos que pide la especificación; sin clases vacías de relleno (en Git las carpetas vacías no
   existen: el primer archivo real de cada capa la crea).
6. **`exception/`**: el contrato de error del servicio (`ErrorResponse`, `ErrorCode`, `GlobalExceptionHandler` y
   las bases `NotFoundException` / `ConflictException`) según la skill **`error-contract`**, porque lo usan todas
   las features.
7. **`.gitignore`** del servicio: `target/`, `*.log`, `.idea/`, `*.iml`, `.vscode/`, `.DS_Store` (y lo de
   optimización nativa si aplica). No borres las líneas que ya tenga.
8. **`README.md` inicial**: título, qué hace, cómo compilar y correr (`./mvnw spring-boot:run`) y cómo probar
   (`./mvnw verify`). Corto: el documentador lo completa al final.
9. **Verificá**: `./mvnw -q -B verify` pasa (con el test `contextLoads`).

Docker, docker-compose y CI **no** son parte del scaffold: los arma DevOps (regla `devops.md`, skill
`docker-support`, plantillas `Dockerfile`, `Dockerfile.native` y `docker-compose.yml`).

## Optimización nativa (`service.optimization: native`)

Spring Boot 3 ya trae el perfil `native` en el parent (con el procesamiento AOT): no lo redefinas. Alcanza con:

1. Agregar el plugin en `<build><plugins>` (la versión la da el parent):

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
</plugin>
```

2. Si hay clases que se usan por reflexión y AOT no detecta (ej. DTOs que se serializan sin pasar por un
   controller), registrarlas en `config/NativeHints.java`:

```java
@Configuration
@ImportRuntimeHints(NativeHints.Registrar.class)
public class NativeHints {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(UserResponse.class, MemberCategory.values());
            hints.resources().registerPattern("application*.yml");
        }
    }
}
```

   (Preferí `RuntimeHints` a escribir `reflect-config.json` a mano.)
3. En `.gitignore`: `target/native-image/`.
4. Compilación nativa local (requiere GraalVM): `./mvnw -Pnative native:compile`. La imagen Docker nativa la
   arma DevOps con la plantilla `Dockerfile.native`.

## Checklist antes de terminar

- [ ] Parámetros tomados de `project.yaml` (nada inventado)
- [ ] `pom.xml` desde la plantilla, mínimo y sin versiones del BOM
- [ ] `mvnw`, `mvnw.cmd` y `.mvn/wrapper/` commiteados
- [ ] `application.yml` (main y test), clase principal y paquetes según `architecture.md`
- [ ] Contrato de error en `exception/`
- [ ] `.gitignore` y README inicial
- [ ] Nativa solo si `service.optimization: native`
- [ ] `./mvnw -q -B verify` pasa
