# TASK-184 — intento de arranque local

Fecha: 2026-11-01 (Europe/Madrid). Entorno: PowerShell, copia de trabajo TASK-184.

Se ejecutó `scripts/start.ps1` desde la raíz. El script creó `.env` desde
`.env.example`; ese archivo local no se incluye como evidencia ni se imprimieron
sus valores. No se modificaron servicios, configuración ni volúmenes.

| Comando | Salida | Resultado observado |
| --- | --- | --- |
| `docker info --format '{{.ServerVersion}}'` | 1 | Acceso denegado al pipe de Docker Engine. |
| `& ./scripts/start.ps1` (`$LASTEXITCODE`) | 1 | No pudo obtener la imagen `postgres:17` por acceso denegado a Docker Engine. |
| `docker compose config --quiet` | 0 | Configuración Compose válida con `.env` presente. |
| `docker compose ps --all --format json` | 1 | Acceso denegado; no se pudo obtener el estado de los contenedores. |
| `docker compose logs --no-color --tail 15 kafka-init` | 1 | Acceso denegado; no se pudo consultar la ejecución del inicializador. |
| `python -m unittest discover -s scripts -p test_verify_integracion.py` | 0 | Tres pruebas correctas del verificador del equipo. |
| `python scripts/verify_integracion.py` | 0 | Verificación correcta de gateway, orquestador y plataforma de eventos mediante sus Maven Wrappers (`-q -B verify`). |

Error común: `permission denied while trying to connect to the docker API at
npipe:////./pipe/dockerDesktopLinuxEngine`.

## Resumen de estados de contenedores

Todos los servicios permanentes: **estado no observable**. No hay evidencia de
`running/healthy`, ni de contenedores detenidos: Docker rechazó la consulta.
`kafka-init`: **estado y código de salida no observables**. Tampoco se pudo
verificar la lista de topics en el broker. La declaración de los cuatro topics
en Compose no demuestra su creación.

Los criterios de ejecución #1–#3 no se acreditan en este entorno. El criterio #4
queda cubierto por este resumen del intento y de las consultas fallidas.
La limitación del entorno afecta la verificación; no se declara un arranque sano.

## Repetición con Docker Engine accesible

1. Ejecutar `./scripts/start.ps1` desde la raíz y comprobar su salida.
2. Consultar `docker compose ps --all` hasta un plazo finito de 600 segundos;
   exigir `running/healthy` en cada servicio permanente. El script actual solo
   ejecuta `up -d --build`, por lo que su salida 0 no acredita salud final.
3. Comprobar `kafka-init` terminado con código 0 mediante
   `docker compose ps --all --format json` y consultar
   `docker compose logs --no-color --tail 30 kafka-init`.
4. Exigir en su lista `wa.message.received.v1`, `wa.message.ready-to-send.v1`,
   `wa.message.status.v1` y `wa.message.dlq.v1`; guardar el resumen de estados y
   el resultado del inicializador, sin configuración resuelta ni secretos.

No se ejecutó el smoke E2E: corresponde a una tarea posterior y requiere el
stack disponible.
