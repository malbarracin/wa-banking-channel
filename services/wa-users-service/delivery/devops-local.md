# Operación local y despliegue preparado

Servicio: wa-users-service, Java 21 / Spring Boot 3.5, PostgreSQL 16, puerto interno 8080.
Fuentes: project.yaml, pom.xml y specs/arquitectura/plan.md.

## Build y pruebas

Requisitos: JDK 21 y Maven Wrapper incluido. En PowerShell:

```powershell
.\mvnw.cmd -B clean verify
.\mvnw.cmd -q dependency:tree -Dverbose
```

En Linux/CI: `chmod +x mvnw` y `./mvnw -B clean verify`.
El gate JaCoCo existente exige cobertura de líneas >=80%; reportes en target/site/jacoco.
Los tests activan test y usan H2 en memoria. El perfil test también está en main/resources
para herramientas de pruebas; H2 solo está en el classpath de tests, no en la imagen.

## Docker Compose

Requisitos: Docker Engine Linux y Compose. Desde la raíz del servicio:

```powershell
Copy-Item .env.example .env
# Editar .env y completar DB_PASSWORD con una contraseña local.
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/info
docker compose logs -f app
docker compose down
```

`docker compose up --build -d` también permite arrancar y luego inspeccionar health con ps.
Se esperan app y postgres healthy. down conserva postgres-data; no usar down -v salvo
que se quiera borrar explícitamente la base local. No se creó ni versionó un .env real.

| Variable | Uso |
|---|---|
| APP_NAME | Nombre del proyecto Compose y de la imagen local; default wa-users-service |
| APP_PORT | Puerto publicado en loopback; default 8080 (contenedor siempre 8080) |
| DB_PORT | Puerto PostgreSQL publicado en loopback; default 5432 |
| DB_NAME | Nombre de base; ejemplo wa_users |
| DB_USERNAME | Usuario PostgreSQL y aplicación; ejemplo app |
| DB_PASSWORD | Obligatoria, sin default ni valor real en Git |
| DB_URL | JDBC completo obligatorio en prod |
| PORT | Puerto del servidor Spring; Compose y Helm lo fijan en 8080 |
| JAVA_OPTS | Flags JVM de contenedor; default MaxRAMPercentage=75 y ExitOnOutOfMemoryError |

La red por defecto de Compose permite resolver postgres sin exponerlo fuera del host.
APP_PORT solo cambia el puerto del host. Si se cambia, adaptar la URL de health.
La imagen compila con wrapper en JDK Alpine, extrae capas Spring Boot y ejecuta con JRE
Alpine, USER app y exec para propagar señales. El build de imagen omite tests porque
CI ya ejecuta clean verify antes del job Docker.

## Perfiles

| Perfil | Base y esquema |
|---|---|
| dev | localhost:DB_PORT/DB_NAME; ddl-auto update para crear el esquema local |
| docker | postgres:5432/DB_NAME; ddl-auto update solo para el entorno local |
| test | H2 PostgreSQL mode; create-drop |
| prod | DB_URL externo; validate, sin creación ni modificación automática |

Credenciales dev/docker/prod por entorno; consola H2 desactivada en base y prod.
Actuator expone health/info. Springdoc conserva sus claves existentes.
Para correr la app en el host, primero iniciar solo la base y exportar credenciales
en la sesión PowerShell (Compose lee .env; Spring Boot en el host no lo importa):

```powershell
docker compose up -d postgres
$env:SPRING_PROFILES_ACTIVE='dev'
$env:DB_NAME='wa_users'
$env:DB_USERNAME='app'
# Definir DB_PASSWORD en la sesión con el mismo valor local de .env.
.\mvnw.cmd spring-boot:run
```

Prod requiere un esquema previamente provisionado compatible con las entidades User
y AuditLog; este paso no introduce migraciones ni ejecuta DDL de producción.

## GitHub Actions

ci.yml: PR y push main ejecutan wrapper clean verify en Temurin 21.
El job helm-lint valida y renderiza dev/prod. Push main construye una imagen
`ghcr.io/<owner>/<repo>:<commit-sha>` (repositorio convertido a minúsculas).
Para publicar, configurar la variable GitHub `GHCR_PUSH_ENABLED=true` y permitir
packages:write al GITHUB_TOKEN del workflow. Sin esa variable, solo construye.
Usa GITHUB_TOKEN efímero; no requiere GHCR_TOKEN persistente.
Configurar imagePullSecrets en Helm si el paquete GHCR es privado.

cd.yml solo acepta workflow_dispatch y muestra un mensaje: no accede a clúster
ni despliega. Antes de habilitar CD se deben acordar clúster, namespace, acceso
de mínimo privilegio y aprobación del entorno. No se publicó imagen ni se hizo deploy.

## Helm

Requisito: Helm disponible. Release corto wa-users; chart helm/wa-users-service.

```powershell
helm lint helm/wa-users-service
helm template wa-users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml
helm template wa-users helm/wa-users-service -f helm/wa-users-service/values-prod.yaml
```

Los values por defecto renderizan sin secretos. Antes de instalar, fijar
image.repository al repositorio GHCR real e image.tag a un SHA construido.
El chart usa ClusterIP, resources configurables, startup/readiness/liveness en
/actuator/health, perfil prod y ConfigMap para configuración no sensible.
No instala PostgreSQL dentro de Kubernetes.

Crear mediante el gestor de secretos del entorno un Secret en el mismo namespace:
wa-users-db-dev para dev o wa-users-db-prod para prod, con claves DB_URL,
DB_USERNAME y DB_PASSWORD. No incluir credenciales en values versionados.
Alternativamente database.createSecret=true habilita la plantilla secret.yaml;
inyectar database.url/username/password mediante un archivo privado protegido
fuera de Git. Helm guarda valores y Secrets en sus metadatos: preferir existingSecret.
Tras rotar un Secret externo, reiniciar el rollout.

Ejemplo para un clúster local ya preparado y con el Secret creado
(comando documentado, no ejecutado):

```powershell
helm upgrade --install wa-users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml --set image.repository=wa-users-service --set image.tag=local -n dev --create-namespace
```

La imagen local debe haberse cargado en el runtime del clúster. En un registro remoto,
reemplazar repository/tag y configurar imagePullSecrets según corresponda.

## Verificación de este paso

- `.\mvnw.cmd -q -B clean verify`: correcto, 174 tests sin fallos ni errores;
  JaCoCo 228/234 líneas cubiertas (97,44%), gate >=80% superado.
- `.\mvnw.cmd -q dependency:tree -Dverbose`: finalizó con código 0; POM sin cambios.
- Segunda pasada `.\mvnw.cmd -q -B verify` con los perfiles finales: código 0.
- `java '-Djarmode=layertools' -jar target/wa-users-service-0.1.0-SNAPSHOT.jar list`:
  verificadas las cuatro capas usadas por el Dockerfile (comando soportado, deprecado).
- Docker Compose config --quiet: correcto, con variables efímeras de validación.
- YAML de Compose, workflows, perfiles, Chart y values: parseado con SnakeYAML local,
  rechazando claves duplicadas. Esto valida sintaxis; Helm requiere su propio render.
- Sin cambios en código de dominio, POM, springdoc ni configuración opensoma.

## Pendiente de verificar

Reintento 2 (2026-10-19): se repitieron `clean verify` (código 0, 174 tests,
sin fallos ni errores, cobertura 228/234 líneas), `dependency:tree -Dverbose`
(código 0), `docker compose config --quiet` y el parseo YAML con SnakeYAML
sin claves duplicadas. La infraestructura existente no requirió cambios.
Se confirmó nuevamente que el daemon Docker no está accesible y Helm no está
instalado; las verificaciones externas siguientes siguen pendientes.

- Docker CLI y Compose disponibles, pero el daemon desktop-linux no responde
  (pipe dockerDesktopLinuxEngine inexistente). Con Docker Desktop iniciado:
  `docker compose up --build -d --wait --wait-timeout 240`,
  `docker compose ps`, `Invoke-RestMethod http://localhost:8080/actuator/health`,
  `docker compose down`. Pendiente confirmar build de imagen y ambos healthy.
- Helm no está instalado; no se instaló ni descargó. Ejecutar
  `helm lint helm/wa-users-service`,
  `helm template wa-users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml` y
  `helm template wa-users helm/wa-users-service -f helm/wa-users-service/values-prod.yaml`.
- GitHub Actions no se ejecutó remotamente en este paso. Abrir un PR para ejecutar CI;
  verificar los jobs build-test y helm-lint, y en push main el build Docker.
  El push GHCR requiere la variable y permisos indicados. CD permanece sin despliegue.
