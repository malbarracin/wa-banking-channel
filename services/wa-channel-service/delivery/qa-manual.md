# Manual de QA — wa-channel-service

Cómo importar y correr las colecciones Postman/Bruno contra el servicio local, qué escenarios son
alcanzables con la configuración por defecto y cuáles requieren un vínculo `ACTIVO`.

## Prerrequisitos

1. H3 levantado (local o Docker). Ver [`../README.md`](../README.md) o
   [`../../../delivery/devops-local.md`](../../../delivery/devops-local.md).
2. Health OK: `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`.
3. **H1 `wa-users-service`** en `http://localhost:8080` con la posibilidad de crear un cliente.
   Sin H1, la verificación y el perfil responden **500**.
4. H2 `credenciales-service` es opcional: si no está, bloqueo/desvinculación siguen funcionando y la
   revocación se audita como rechazada.

## Postman

1. Importar `delivery/postman/wa-channel-service.postman_collection.json`.
2. Importar el entorno `delivery/postman/wa-channel-service.postman_environment.json`
   (`baseUrl` H3 `http://localhost:8081`, `h1BaseUrl` H1 `http://localhost:8080`).
3. Seleccionar el entorno **wa-channel-service - local**.
4. Collection Runner → ejecutar en orden:
   **0. Preparación H1** → **1. Onboarding y verificación** → **2. Consultas y errores** → **3. Salud**.

La carpeta **0** genera un `docNumber` y un `numeroWhatsapp` únicos por corrida y crea el cliente
`ACTIVE` en H1. Es la forma recomendada de repetir la suite sin limpiar la base. Si se omite, hay que
garantizar a mano que el documento de `docNumber` exista y esté `ACTIVE` en H1.

Cada pedido tiene tests de status y de campos clave, más una respuesta de ejemplo guardada.

### Newman (opcional)

```bash
cd services/wa-channel-service
newman run delivery/postman/wa-channel-service.postman_collection.json \
  -e delivery/postman/wa-channel-service.postman_environment.json
```

Si `newman` no está instalado: `npm i -g newman`.

## Bruno

1. Open Collection → carpeta `delivery/bruno/wa-channel-service/`.
2. Entorno **local** (`environments/local.bru`): `baseUrl`, `h1BaseUrl`, `docTipo`, `docNumber` y
   `numeroWhatsapp`.
3. Ejecutar en orden `0-preparacion-h1`, `1-onboarding`, `2-consultas-errores`, `3-salud`.

Misma secuencia, datos y aserciones que Postman. También se puede importar el JSON de Postman en Bruno.

## Orden del onboarding

| # | Pedido | Esperado con la configuración por defecto |
|---|--------|-------------------------------------------|
| 1 | Crear cliente en H1 (`POST /api/v1/users`) | 201, `estado=ACTIVE` |
| 2 | Aceptar términos `POST /terminos` | 201, `PENDIENTE_VERIFICACION` |
| 3 | Estado `GET .../estado` | 200, `vinculado=true` |
| 4 | Verificación 1 `POST .../verificaciones` | 200, `exitosa=false`, `intentosRestantes=2` |
| 5 | Verificación 2 | 200, `exitosa=false`, `intentosRestantes=1` |
| 6 | Verificación 3 | 200, `exitosa=false`, `intentosRestantes=0` |
| 7 | Verificación 4 | **423** `LOCKED` |
| 8 | Historial `GET .../historial` | 200, evento `ACEPTACION_TERMINOS` y fallos de verificación |

Con la estrategia `VerificadorIdentidad` por defecto **ningún intento activa el vínculo**: es el
placeholder hasta que Compliance defina el método. Por eso el flujo llega a 423 y no a `ACTIVO`.

## Casos de error cubiertos

| Caso | Esperado |
|------|----------|
| Aceptar términos sin número | 400 `VALIDATION_ERROR` (`details` con `numeroWhatsapp`) |
| Aceptar términos con número fuera de E.164 | 400 `VALIDATION_ERROR` |
| Verificación sin términos aceptados | 404 `NOT_FOUND` |
| Verificación con body vacío | 400 `VALIDATION_ERROR` |
| Historial de un número sin vínculo | 404 `NOT_FOUND` |
| Perfil sin `X-Canal-Credencial-Valida` | 401 `UNAUTHORIZED` |
| Perfil con credencial sobre vínculo no `ACTIVO` | 409 `CONFLICT` |
| Preferencias sin headers de documento | 401 `UNAUTHORIZED` |
| Preferencias sobre vínculo no `ACTIVO` | 409 `CONFLICT` |
| Preferencias con propiedad desconocida | 400 `VALIDATION_ERROR` |
| Bloqueo `SOLICITUD_CLIENTE` sin credencial | 401 `UNAUTHORIZED` |
| Bloqueo `POLITICA_BANCO` sobre vínculo no `ACTIVO` | 409 `CONFLICT` |
| Desvinculación sin credencial | 401 `UNAUTHORIZED` |
| Desvinculación sobre vínculo no `ACTIVO` | 409 `CONFLICT` |
| Estado de un número libre | 200, `vinculado=false` |

Los casos que exigen un número ya activo (verificación exitosa, número ya vinculado, perfil 200,
preferencias 200, bloqueo 200 y desvinculación 200) están cubiertos por los tests de integración del
repo con clientes H1/H2 simulados.

## Vínculo ACTIVO para pruebas manuales (solo local)

Con el perfil `dev` (H2 en memoria) se puede preparar un vínculo activo sin instalar la estrategia de
Compliance. **Solo para QA local; nunca en producción.**

1. Levantar con perfil `dev` y abrir `http://localhost:8081/h2-console`
   (JDBC `jdbc:h2:mem:wa_channel_dev`, usuario `sa`, sin contraseña).
2. Aceptar términos para un número de prueba, por ejemplo `+5491100012345` (ver `api.md`).
3. Crear en H1 un cliente `ACTIVE` con documento `DNI 30111222` (ver `api.md`, “Preparación local”).
4. Ejecutar en la consola H2:

```sql
UPDATE vinculo
SET estado = 'ACTIVO',
    documento_tipo = 'DNI',
    documento_numero = '30111222',
    fecha_activacion = CURRENT_TIMESTAMP,
    fecha_ultimo_cambio_estado = CURRENT_TIMESTAMP
WHERE numero_whatsapp = '+5491100012345'
  AND estado = 'PENDIENTE_VERIFICACION';
```

5. Probar con `api.md` el perfil, las preferencias, el bloqueo y la desvinculación sobre ese número.
   No reutilizar ese número en la suite automatizada: la carpeta 2 espera que el vínculo **no** esté
   activo.

En PostgreSQL local el mismo `UPDATE` se ejecuta con `psql` sobre `wa_channel` (esquema
`V001__vinculo.sql`). Los datos de H2 se pierden al reiniciar el servicio.

## Tests automatizados del repo

```bash
cd services/wa-channel-service
./mvnw -q -B verify
```

Unitarios + integración (perfil `test`, H2 y clientes H1/H2 simulados). Regenera
`delivery/openapi/` y exige JaCoCo ≥ 80 % de líneas.

## Qué no cubre la suite manual

- Verificación exitosa real: depende del método aprobado por Compliance.
- Bloqueo automático por criterios de riesgo: no hay reglas definidas; el endpoint acepta
  `POLITICA_BANCO`, pero la decisión es externa.
- Representante autorizado: no modelado.
- Retención o paginación del historial: el endpoint devuelve todos los eventos del número.
