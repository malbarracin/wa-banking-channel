# Operación del servicio de usuarios

Java 21 y MongoDB 7.0. Los comandos parten de la raíz del repositorio y usan PowerShell.

## Desarrollo y Docker

```powershell
cd services/wa-users-service
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose exec app id
docker compose down
```

El volumen `mongo-data` conserva los datos al apagar. Mongo y HTTP se publican solo en
127.0.0.1. Mongo local no tiene autenticación; esta composición es para desarrollo.
No ejecutar `down -v` si se necesitan conservar datos.

Para ejecutar Java fuera de Docker, iniciar únicamente Mongo y usar el wrapper:

```powershell
cd services/wa-users-service
docker compose up -d --wait mongo
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

En POSIX usar `./mvnw`. No es necesario crear `.env`: los valores por defecto permiten
el arranque local. `.env.example` documenta las opciones; cualquier `.env` local debe
permanecer fuera de Git.

| Variable | Uso / valor local |
|---|---|
| `APP_NAME` | Nombre de aplicación, `wa-users-service` |
| `APP_PORT` | Puerto HTTP del host, `8080` |
| `MONGO_PORT` | Puerto Mongo del host, `27017` |
| `APP_IMAGE` | Tag local de imagen, `wa-users-service:local` |
| `SPRING_DATA_MONGODB_URI` | En Compose: `mongodb://mongo:27017/wa_users`; fuera: `mongodb://localhost:27017/wa_users` |
| `SPRING_PROFILES_ACTIVE` | `local`, `dev`, `docker`, `test` o `prod` |
| `SERVER_PORT` | Puerto local Java; Docker y producción fijan el interno en `8080` |
| `JAVA_TOOL_OPTIONS` | Flags JVM; imagen usa heap máximo del 75% y salida ante OOM |
| `USERS_QUERY_FULL_VIEW_ACTORS` | Restricción opcional de actores internos; valor por defecto `BUSINESS_OPERATIONS,SUPPORT` |

`local` es el perfil por defecto; `dev` comparte su configuración y permite URI externa.
`docker` conecta por nombre de servicio. `test` usa `wa_users_it`; los tests de integración
existentes fijan `localhost:27017/wa_users_it`, por lo que requieren ese puerto libre.
`prod` requiere URI por entorno y deshabilita API docs y Swagger UI. No hay consola de BD.
Actuator expone únicamente `health` e `info`.

## Verificación y CI

```powershell
cd services/wa-users-service
docker compose up -d --wait mongo
.\mvnw.cmd -B clean verify
.\mvnw.cmd -q dependency:tree -Dverbose
```

La verificación raíz `.\mvnw.cmd -B clean verify` delega al mismo servicio.
CI (`.github/workflows/ci.yml`) ejecuta `./mvnw -B clean verify` desde el servicio con
Temurin 21 y Mongo como servicio Linux en `localhost:27017`, sin npipe. El gate existente
exige cobertura JaCoCo LINE >= 80%. CI falla si los tests Mongo quedan omitidos y conserva
reportes como artifacts. No activar `test` globalmente en CI: algunos tests MVC comprueban Swagger.

## Imagen

```powershell
docker build -t wa-users-service:local services/wa-users-service
```

Imagen multi-stage JDK21/JRE21 Alpine, capas Spring Boot, usuario `app`, healthcheck HTTP
y flags JVM para límites de contenedor. El contexto excluye secretos, builds locales y
configuración de herramientas. El wrapper se descarga dentro del builder.

CI construye la imagen tras Maven en PR y push. Publicar es opcional: ejecutar manualmente
`Users service CI` en `main` con `publish=true`. Usa `GITHUB_TOKEN` con `packages:write`
y tag `ghcr.io/<owner>/<repo>/wa-users-service:<commit-sha>`; el nombre se convierte a minúsculas.
La organización debe permitir publicación de paquetes al token. No se incluye despliegue automático.

## Kubernetes y Helm

Mongo es externo al chart. Aprovisionar un Secret en el namespace destino con clave `uri`
que contenga la URI autenticada, mediante el gestor de secretos del entorno. El chart solo
lo referencia y no almacena credenciales. Nombres por defecto: `wa-users-mongodb-dev` y
`wa-users-mongodb-prod`. Configurar `mongodb.existingSecret` y `mongodb.uriKey` para cambiarlos.

```powershell
helm lint helm/wa-users-service
helm lint helm/wa-users-service -f helm/wa-users-service/values-prod.yaml
helm template users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml
helm template users helm/wa-users-service -f helm/wa-users-service/values-prod.yaml
# Solo con cluster dev, Secret e imagen accesible ya preparados:
helm upgrade --install users helm/wa-users-service -f helm/wa-users-service/values-dev.yaml -n dev --create-namespace
```

Antes de un despliegue productivo manual, reemplazar `image.repository` e `image.tag` en
los valores o mediante parámetros; `values-prod.yaml` contiene marcadores explícitos.
Configurar `imagePullSecrets` para registry privado. Dev usa una réplica; prod dos.
El chart entrega Deployment, Service ClusterIP, ConfigMap, referencia a Secret,
startup/readiness/liveness sobre `/actuator/health` y recursos configurables.
El contenedor no tiene privilegios, monta `/tmp` temporal y usa raíz de solo lectura.
Las probes incluyen salud Mongo: una caída prolongada de BD puede reiniciar el contenedor.

## Verificación realizada

- `docker compose config --quiet`: correcto.
- `docker compose up --build -d --wait --wait-timeout 180`: imagen construida;
  app y Mongo saludables. HTTP `/actuator/health`: `UP`; `docker compose exec app id`
  confirma usuario `app` sin privilegios.
- `.\mvnw.cmd -B clean verify`: correcto, 185 tests, cero fallos y cero omitidos;
  incluye 12 tests de integración Mongo. Cobertura LINE 97,07%; gate superado.
- `.\mvnw.cmd -q dependency:tree -Dverbose`: correcto.
- YAML de workflow, perfiles y valores Helm: sintaxis validada con PyYAML.
- Perfil `prod` probado con la imagen, UID/GID 1000, raíz de solo lectura y `/tmp`
  temporal: salud HTTP 200. Swagger UI y API docs están deshabilitados; sus rutas
  responden 500 por el manejador global existente de recursos ausentes, sin servir
  documentación. Corregir ese código HTTP requiere una revisión del contrato de errores.

## Pendiente de verificar

- Helm no está instalado en el entorno local. Ejecutar los cuatro comandos `helm lint`
  y `helm template` anteriores; no se instalaron herramientas para verificarlos.
- Ejecución del workflow en GitHub y publicación GHCR: ejecutar `Users service CI` en
  GitHub; para publicar, manualmente sobre `main` con `publish=true`.
- Despliegue Kubernetes: requiere cluster, Secret Mongo e imagen publicados; ejecutar
  el comando `helm upgrade --install` de desarrollo solo en el entorno preparado.
