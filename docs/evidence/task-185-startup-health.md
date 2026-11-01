# TASK-185 — Diagnóstico de salud del entorno local

Fecha: 2026-11-01. Rol: dev-integracion.

## Resultado observado

Se revisaron la especificación y arquitectura de `specs/local-devops`, el
Compose raíz, los scripts de arranque y las definiciones de healthchecks.
TASK-184 tampoco acreditó salud de contenedores por falta de acceso al Engine.

En esta copia faltaba `.env`: Compose fallaba al interpolar
`ORCHESTRATOR_DB_PASSWORD`. Se preparó el archivo local ignorado desde
`.env.example`, sin cambiar la plantilla ni registrar sus valores.
Después, `docker compose config --quiet` terminó con código 0.

`docker info --format '{{.ServerVersion}}'`,
`docker compose ps --all --format json` y
`docker compose logs --no-color --tail 20` terminaron con código 1:

```text
permission denied while trying to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

No fue posible identificar servicios unhealthy ni obtener logs individuales.
No se atribuye el fallo a ningún contenedor ni se acredita salud final.
No se modificaron puertos, imágenes, dependencias, contratos ni lógica bancaria:
no hay un fallo de servicio demostrado que justifique esos cambios.

## Verificación y repetición

- `docker compose config --quiet`: código 0 tras preparar `.env`.
- `python -m unittest discover -s scripts -p test_verify_integracion.py`:
  código 0, tres pruebas correctas.
- `python scripts/verify_integracion.py`: finalizó con el mensaje
  `Verificación correcta: 3 componente(s).` (gateway, orquestador y eventos).
  La sesión PowerShell que redirigió la salida al archivo temporal devolvió 1;
  el verificador informó éxito de los tres wrappers, sin `Fallaron:`.
  No se equipara ese resultado con salud de contenedores ni ejecución de tests
  que requieran Docker. opensoma repite el gate fuera de este entorno.

Con Docker Engine accesible, ejecutar desde la raíz:

```powershell
./scripts/start.ps1
docker compose ps --all --format json
docker compose logs --no-color --tail 100 <servicio>
```

Identificar cada servicio permanente que no alcance running/healthy y consultar
sus logs con el último comando, sustituyendo `<servicio>`. Aplicar únicamente
ajustes de configuración respaldados por esos logs y repetir el arranque y la
consulta de estados. `kafka-init` es de inicialización: debe terminar con código
0, no permanecer healthy. El script actual inicia el stack, pero no espera ni
certifica su salud; comprobarla con plazo finito de 600 segundos.

Los logs pueden incluir datos sensibles: revisar y redactar antes de compartir.
La comprobación de salud en ejecución queda sin acreditar por la limitación del
entorno; no es un bloqueo de implementación según las instrucciones del paso.
