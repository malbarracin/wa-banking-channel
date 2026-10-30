# wa-mcp-layer

Base de H7 en Python 3.12, administrada con uv. Todavía no expone herramientas ni servidor MCP.

Desde esta carpeta, ejecutar `uv sync --frozen`. La configuración se lee del entorno y de un archivo
`.env` local opcional; `.env.example` documenta los valores de desarrollo. No almacenar credenciales
de sesión ni secretos en estos archivos.

Verificación completa desde la raíz: `python ai/verify.py`. Ejecuta sincronización congelada,
ruff (lint y formato), mypy estricto y pytest con cobertura mínima de 80% por componente.
Las pruebas usan servicios simulados, sin red ni modelos.

`catalog.CATALOG` registra las diez capacidades H1/H3 de TASK-124. `Catalog.invoke(nombre,
contexto, parametros, actor="agente")` devuelve `Resultado` o `Fallo`, ambos con auditoría.
El servidor debe resolver el actor desde su consumidor confiable: nunca aceptar `actor` desde
argumentos de una herramienta o desde el modelo. Las dos consultas de identidad y el historial
de vínculo requieren `soporte`; el resto requiere `agente`. `tool_name` proporciona el nombre MCP
versionado para su registro posterior. Este paso no incorpora el servidor MCP.

Parámetros H1: `tipo`, `numero`. En H3 se usa el número del contexto; el opcional
`numero_whatsapp` debe coincidir para el agente. Verificación recibe `documentoTipo`,
`documentoNumero`, `datosVerificacion`; preferencias recibe `idioma`, `notificacionesHabilitadas`,
`horarioPreferido`; bloqueo recibe `motivo="SOLICITUD_CLIENTE"`, `confirmacion=true`;
desvinculación recibe `confirmacion=true`. Las demás operaciones reciben `{}`.
Perfil, preferencias, bloqueo y desvinculación revalidan H2 en cada llamada.
El flujo inicial (términos, verificación, estado) funciona sin credencial.

Para deshabilitar una operación: `MCP_CAPABILITY_<DOMINIO>_<ACCION>_ENABLED=false`
(por ejemplo `MCP_CAPABILITY_VINCULO_CONSULTAR_PERFIL_ENABLED=false`). El rechazo se audita
y no invoca servicios. Tampoco se ejecutan nombres no publicados ni operaciones de otro actor.

Contratos utilizados: `services/wa-users-service/delivery/openapi/openapi.yaml` y
`services/wa-channel-service/delivery/openapi/openapi.yaml`, junto con sus `delivery/api.md`.
No hay copias OpenAPI de H1/H3 en `shared/contracts/` en esta revisión del repositorio.
