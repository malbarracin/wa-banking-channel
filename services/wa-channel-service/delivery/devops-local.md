# Operación local

Se necesitan Java 21, Docker con Compose y, para Kubernetes, Helm y un clúster kind o minikube. Ejecutar desde `services/wa-channel-service`.

Copiar `.env.example` a `.env`, reemplazar `DB_PASSWORD` por una contraseña local y ajustar `USERS_SERVICE_BASE_URL` para que apunte al servicio de usuarios accesible desde el contenedor. `host.docker.internal` apunta al equipo anfitrión. No versionar `.env`.

```sh
cp .env.example .env
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f app
curl http://localhost:8080/actuator/health
docker compose down
./mvnw -q -B clean verify
```

En Windows usar `Copy-Item .env.example .env` y `mvnw.cmd -q -B clean verify`. El puerto de host se configura con `APP_PORT` y el del proceso con `SERVER_PORT`. Para ejecutar la aplicación en el host con perfil `dev`, levantar `docker compose up -d postgres` y exportar `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`.

El chart usa una imagen configurable y un Secret existente, sin crear credenciales. Antes de instalarlo, crear el Secret indicado por `database.existingSecret` con las claves `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD`; configurar la imagen y `usersServiceBaseUrl` para el entorno. El pipeline construye la imagen en main y no la publica automáticamente; configurar un registro antes de desplegar. No hay despliegue automático habilitado.

```sh
helm lint helm/wa-channel-service
helm template channel helm/wa-channel-service -f helm/wa-channel-service/values-dev.yaml
helm upgrade --install channel helm/wa-channel-service -f helm/wa-channel-service/values-dev.yaml -n dev --create-namespace
```

## Pendiente de verificar

- Helm no está instalado en este entorno: ejecutar `helm lint helm/wa-channel-service` y `helm template channel helm/wa-channel-service -f helm/wa-channel-service/values-dev.yaml`.
- La construcción Docker quedó esperando la descarga de dependencias Maven; verificar `docker compose up -d --build`, `docker compose ps` y `curl http://localhost:8080/actuator/health` con acceso a los repositorios Maven. La configuración se validó con `docker compose --env-file .env.example config --quiet`.
- El workflow debe ejecutarse en GitHub después de publicar los cambios.
