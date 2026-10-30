# wa-mcp-layer

Capa MCP (H7) en Python 3.12, administrada con uv. Expone el catálogo versionado de
capacidades sobre H1–H6. El contenedor sirve `GET /health` (`python -m wa_mcp_layer.server`);
el transporte FastMCP Streamable HTTP en `/mcp` se agrega en un paso posterior.

Desde esta carpeta, ejecutar `uv sync --frozen`. La configuración se lee del entorno y de un archivo
`.env` local opcional; `.env.example` documenta los valores de desarrollo. No almacenar credenciales
de sesión ni secretos en estos archivos.

Contenedores (desde `ai/`): copiar `.env.example` → `.env` y `docker compose up -d --build`.
Incluye H7, H1–H6, pgvector, Redis y Agent Memory Server. CI: `.github/workflows/ai.yml`.

Verificación completa desde la raíz: `python ai/verify.py`. Ejecuta sincronización congelada,
ruff (lint y formato), mypy estricto y pytest con cobertura mínima de 80% por componente.
Las pruebas usan servicios simulados, sin red ni modelos.

## Contrato publicado

`shared/contracts/wa-mcp-layer/capabilities.md` lista las **32** capacidades v1.
`shared/contracts/wa-mcp-layer/schemas/_envelope.json` define el sobre común; cada capacidad
tiene `schemas/<dominio>.<accion>.v1.schema.json`.

`catalog.CATALOG` registra nombre, versión, servicio, `requiere_credencial`, actor, handler y
flag `habilitada`. `resolve(nombre, actor=...)` valida publicación + actor + flag de entorno.
`Catalog.invoke` despacha H1/H3; H2/H4/H5/H6 se invocan por sus módulos `capabilities/*`.
Herramienta MCP: `Capability.tool_name` (`<dominio>_<accion>_v1`).

El actor lo resuelve el consumidor confiable: nunca desde argumentos del modelo.
Identidad, historial de vínculo/credencial y revocación masiva son `soporte`/`banco`.

Para deshabilitar: `MCP_CAPABILITY_<DOMINIO>_<ACCION>_ENABLED=false`
(`acceso.revocar_credenciales_masivo` arranca deshabilitada).
