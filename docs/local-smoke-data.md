# Datos sintéticos del smoke local (TASK-186)

Aplicar únicamente al Compose raíz local, después de `scripts/start` y con H1,
H2, H3 y sus bases sanos y sus esquemas inicializados. No usa cuentas Twilio reales.
El comando requiere Python 3 y Docker Compose, sin paquetes adicionales.

En `.env`, configurar `CREDENTIAL_H3_SERVICE_TOKEN` y
`CREDENTIAL_BANK_SERVICE_TOKEN` con secretos locales distintos. H2 y H3 comparten
el primero; Compose lo pasa a ambos. Recrear H2/H3 si se cambiaron estas variables.
Exportar el mismo `CREDENTIAL_H3_SERVICE_TOKEN` en la terminal del seed; el script
no lee ni imprime `.env`. Si se personalizaron puertos, exportar también
`USERS_HOST_PORT`, `CHANNEL_HOST_PORT`, `CREDENTIALS_HOST_PORT`.

Desde la raíz, en PowerShell o Bash:

```text
python scripts/seed_smoke.py seed --local-only
python scripts/seed_smoke.py check --local-only
# Ejecutar el smoke E2E y después limpiar:
python scripts/seed_smoke.py cleanup --local-only
```

`seed` crea un cliente H1 `ACTIVE` con nombre `QA-Smoke`, documento `QA-<uuid>`,
email `@example.test` y número simulado `+1555` con siete dígitos aleatorios.
Acepta términos por `POST /api/v1/vinculos/terminos` de H3. No usa datos reales.
Los identificadores quedan en `.local-seed/smoke.json` (ignorado), nunca el JWT
ni el secreto de servicio. Un segundo seed exige limpiar primero; cada corrida
genera identificadores nuevos. No usar estos números en transporte Twilio real.

## Excepción local de activación

El verificador de H3 actualmente devuelve `false`. Por decisión explícita del
tech-lead, `scripts/local-init/activate-smoke-link.sql` activa **solo** el registro
pendiente que acaba de crear la API, con su UUID, número simulado y documento QA.
El script lo ejecuta con `docker compose exec -T channel-postgres ... psql`,
usando las variables del contenedor y parámetros SQL escapados por psql.
No cambia `services/` ni desactiva la seguridad de las APIs.

Esta inicialización se ejecuta **después** del arranque y de aceptar términos:
montarla en `docker-entrypoint-initdb.d` no sirve porque todavía no existirían
el vínculo y posiblemente el esquema. El UPDATE está basado en la
[guía local de H3](../services/wa-channel-service/delivery/qa-manual.md).
No acredita verificación de identidad real ni creación enteramente por APIs.
TASK-193 solicita al equipo dueño de H3 un verificador dev/local para sustituir
esta excepción por `/verificaciones`; fuera de local se mantiene la verificación real.

## Credencial, persistencia y consumo por el smoke

Después de confirmar `estado=ACTIVO`, el seed llama a H2
`POST /api/v1/credenciales/emitir`, autenticado como H3 con Bearer de servicio,
documento del cliente y confirmaciones locales de vínculo e identidad.
Exige `vigente=true` y número coincidente en `/validar`, y consulta `/perfil`
de H3 con `X-Canal-Credencial-Token`. Esa consulta alcanza H1 realmente.
El JWT solo existe en memoria del proceso y no se muestra al cliente final.

El arnés E2E puede leer el documento y teléfono del archivo e invocar `issue(data)`
desde `scripts/seed_smoke.py` para obtener el JWT en memoria mediante el mismo
contrato de H2. No guardar JWT en eventos, fixtures, logs o resultados.
`check` emite una credencial nueva e invalida la anterior: ejecutarlo antes del
smoke, nunca en paralelo con una conversación. Este seed no instala el contexto
técnico de sesión en H0.2; esa integración pertenece al arnés/orquestador.

Cliente, vínculo y credencial se almacenan en PostgreSQL, en los volúmenes
`users-postgres-data`, `channel-postgres-data`, `credentials-postgres-data`.
Persisten durante el stack y reinicios sin borrar volúmenes. La credencial tiene
la expiración de H2 (default 1440 minutos), no vigencia ilimitada: ejecutar `check`
para emitir otra al retomar. No se inventan productos ni conocimiento H8 para
este caso, que permite consultar el perfil del cliente H1.

## Limpieza y fallos parciales

`cleanup` emite una credencial fresca, llama a `PATCH /desvinculacion` con
`confirmacion=true` y el header de H3, comprueba que H2 rechaza el JWT con 401 y
aplica `PATCH /api/v1/users/DNI/{documento}/status` con `LOGICAL_DELETE`.
Conserva auditorías según los contratos; no hace DELETE físico ni SQL de limpieza.
Si la revocación best-effort de H3 falla, no da de baja H1: completar la revocación
de H2 con `POST /api/v1/credenciales/{numero}/revocacion`, Bearer H3 y
`{"motivo":"DESVINCULACION","solicitadoPor":"CANAL"}`.

Ante un fallo parcial se conserva el archivo de identificadores. El comando de
cleanup presupone vínculo activo y cliente ACTIVE; si falla antes de activarlo,
las APIs actuales no permiten borrar el pendiente. Dar de baja el cliente por
H1 si fue creado y conservar la evidencia; en un entorno desechable se puede
usar `scripts/reset` para borrar **todos** los volúmenes locales. Si la limpieza
falló tras desvincular, completar la baja de H1 por su API antes de retirar el
archivo local. No repetir seed a ciegas ni tratar fallos como éxitos.

Contratos: [H1](../services/wa-users-service/delivery/api.md),
[H2](../services/wa-credentials-service/delivery/api.md),
[H3](../services/wa-channel-service/delivery/api.md).

## Verificación del arnés

```text
python -m unittest discover -s scripts -p test_seed_smoke.py
python scripts/verify_integracion.py
```

Las pruebas aisladas verifican secuencia, datos sintéticos, reserva del JWT,
rechazo del vínculo pendiente y limpieza condicionada a revocación. No acreditan
servicios levantados: los comandos `seed` y `check` contra el stack son la
verificación de ejecución real.

Validación en el paso de TASK-186: las 7 pruebas de scripts pasaron (4 del seed),
y `docker compose --env-file .env.example config --no-env-resolution --quiet`
terminó con código 0. `docker info` rechazó el acceso a
`dockerDesktopLinuxEngine`; no se ejecutó seed contra las APIs ni se afirma que
existan datos creados en ese entorno. Repetir `seed` y `check` con Docker accesible.

`python scripts/verify_integracion.py` pasó gateway y orquestador; en plataforma
de eventos falló inicialmente `KafkaMessagingIntegrationTest` con
`No records found for topic`. Al repetir el módulo, los reportes Surefire
registraron 32 pruebas sin fallos ni errores, aunque la invocación terminó con
código 1 sin diagnóstico Maven en su log. Se completó después
`mvnw.cmd -q -B -DskipTests verify` con `MAVEN_EXIT=0`, usando los resultados ya
generados y pasando el chequeo de cobertura. No se afirma que el verificador
global terminara en verde; opensoma debe repetirlo en su entorno.
