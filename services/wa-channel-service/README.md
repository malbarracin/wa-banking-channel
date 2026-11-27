# wa-channel-service

Servicio de dominio que gestiona el vínculo entre un número de WhatsApp y un cliente del banco:
aceptación de términos, verificación de identidad, estados del canal, preferencias, bloqueo y
desvinculación.

## Compilar y correr

```bash
./mvnw spring-boot:run
```

Puerto por defecto: `8080` (`SERVER_PORT`). Datasource: `DATABASE_URL`, `DATABASE_USERNAME`,
`DATABASE_PASSWORD`.

## Probar

```bash
./mvnw verify
```
