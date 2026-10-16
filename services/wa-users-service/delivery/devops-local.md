# Operación local y despliegue preparado

Servicio `com.bank:wa-users-service`, Java 21, Spring Boot 3, PostgreSQL 16 y HTTP 8080.
Fuentes: `project.yaml`, `pom.xml`, `specs/arquitectura/spec.md` y TASK-9.

## Requisitos y variables

Java 21 y Maven Wrapper para verificar; Docker con daemon Linux para Compose;
Helm 3 y acceso autorizado a un cluster solo para un despliegue posterior.
No se instala ninguna herramienta ni se ejecuta un despliegue en este cierre.

Copiar `.env.example` a `.env` y completar `DB_PASSWORD` con una contraseña local.
`.env` está excluido de Git y del contexto Docker. No compartir la salida completa de
`docker compose config`, porque puede contener credenciales; usar `--quiet`.

| Variable | Uso |
|---|---|
| `APP_NAME` | Nombre de imagen local; default `wa-users-service` |
| `APP_PORT` | Puerto del host; default 8080, interno fijo 8080 |
| `DB_NAME` | Base local, ejemplo `wa_users` |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales locales obligatorias |
| `DB_PORT` | Puerto PostgreSQL en loopback del host, default 5432 |
| `DB_URL` | JDBC externo obligatorio en producción |
| `SPRING_PROFILES_ACTIVE` | `dev`, `docker`, `test` o `prod` según entorno |

## Docker y desarrollo local (PowerShell)

```powershell
Copy-Item .env.example .env
# Editar .env y completar DB_PASSWORD antes de continuar.
docker compose config --quiet
docker compose up --build -d --wait
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose logs --tail 100 app
docker compose down
```

`down` conserva el volumen `postgres-data`. App y PostgreSQL tienen healthchecks;
app espera a PostgreSQL healthy. PostgreSQL solo publica en `127.0.0.1` para el perfil
dev. La red bridge propia de Compose permite resolver `postgres` desde app.

Para ejecutar la app fuera del contenedor:

```powershell
docker compose up -d --wait postgres
# Exportar en esta sesión los mismos valores de DB_NAME, DB_USERNAME,
# DB_PASSWORD y DB_PORT usados en .env. Spring no carga .env automáticamente.
$env:SPRING_PROFILES_ACTIVE='dev'
.\mvnw.cmd spring-boot:run
```

Decisión de esquema: no hay migraciones versionadas en la implementación integrada.
Los perfiles `dev` y `docker` usan Hibernate `update` para crear un esquema local vacío.
`prod` usa `validate`: el esquema debe estar preparado por un proceso de migración
aprobado antes de iniciar; este chart no crea ni modifica el esquema productivo.
Test usa H2 en memoria, `create-drop`, consola desactivada y driver solo de test.
Las credenciales PostgreSQL se reciben por entorno, nunca desde YAML versionado.

## Maven y CI

```powershell
.\mvnw.cmd -B clean verify
.\mvnw.cmd -B dependency:tree -Dverbose
```

En Linux: `chmod +x mvnw` y `./mvnw -B clean verify`. El gate existente de JaCoCo
exige al menos 80% de líneas. CI activa `test`, usa Temurin 21 y cache Maven.
PR y push a main/develop ejecutan verificación, build de imagen y lint/render Helm.
La imagen se etiqueta `ghcr.io/<owner>/<repo>:<sha>` en minúsculas.
Para habilitar push en main configurar la variable de repositorio `GHCR_PUSH_ENABLED=true`
y permitir escritura de packages al `GITHUB_TOKEN` del workflow (token automático,
sin credenciales adicionales versionadas). Por defecto solo se construye la imagen.
La publicación GHCR queda pendiente de habilitar y probar en el repositorio remoto.
`cd.yml` es un template manual informativo; no contiene comandos de despliegue.

## Helm

Release corto `wa-users`, chart `helm/wa-users-service`, sin ingress por no existir
dominio acordado. Service ClusterIP 8080, probes `/actuator/health`, perfil `prod`,
usuario sin privilegios, sin token de ServiceAccount y recursos configurables.
Dev: requests 100m/256Mi y limits 1 CPU/768Mi; prod: 250m/512Mi y 2 CPU/1Gi.
Los valores de imagen locales son placeholders: para deploy definir repository/tag
del registry y, si corresponde, `imagePullSecrets` con credenciales preexistentes.

```powershell
helm lint helm/wa-users-service
helm template wa-users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml
helm template wa-users helm/wa-users-service -f helm/wa-users-service/values-prod.yaml
```

Por defecto se referencia un Secret externo `wa-users-db-dev` o `wa-users-db-prod`
con las claves `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Crearlo con el gestor de secretos
del entorno antes de instalar. La opción `database.createSecret=true` permite crear el
Secret mediante un archivo de valores privado fuera de Git que defina `database.url`,
`database.username` y `database.password`; no pasar secretos por argumentos de shell.
Helm guarda los valores en el estado del release: preferir Secret externo en producción.
Rotar un Secret externo requiere reiniciar el Deployment para recargar el entorno.

Solo cuando exista cluster local autorizado, esquema y Secret preparados, e imagen disponible:

```powershell
helm upgrade --install wa-users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml --set image.repository=wa-users-service --set image.tag=local -n dev --create-namespace --wait
```

## Verificación de este cierre

- `.\mvnw.cmd -B clean verify`: BUILD SUCCESS, 122 tests, cero fallos/errores;
  JaCoCo 279 líneas cubiertas de 286 (97,55%), gate 80% cumplido.
- `.\mvnw.cmd -q -B verify` con `SPRING_PROFILES_ACTIVE=test`: exit 0;
  validado el perfil que utiliza CI.
- `.\mvnw.cmd -B dependency:tree -Dverbose`: BUILD SUCCESS; dependencias
  administradas por BOM, sin cambios en POM ni conflictos de versión detectados.
- `java -Djarmode=layertools -jar target/wa-users-service-0.1.0-SNAPSHOT.jar list`:
  capas esperadas presentes (dependencies, spring-boot-loader, snapshot-dependencies, application).
- `docker compose config --quiet`: válido usando variables ficticias de validación.
- Docker CLI disponible; daemon Linux inaccesible (pipe dockerDesktopLinuxEngine ausente).
- Helm no está instalado; no se instala ni descarga por este paso.
- Validación sintáctica YAML local con SnakeYAML del cache Maven; templates Helm requieren Helm.

## Pendiente de verificar

Con daemon Docker activo y `.env` completado:

```powershell
docker compose config --quiet
docker compose up --build -d --wait
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose down
```

Con Helm disponible, ejecutar los tres comandos `lint`/`template` de la sección Helm.
No se verificaron construcción/runtime de la imagen, servicios healthy ni render Helm.
En GitHub ejecutar CI mediante PR/push y, tras configurar GHCR, comprobar el push de
`ghcr.io/<owner>/<repo>:<sha>` en main. No se verificó CI remoto ni deploy en cluster.
