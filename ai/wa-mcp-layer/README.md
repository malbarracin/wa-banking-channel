# wa-mcp-layer

Base de H7 en Python 3.12, administrada con uv. Todavía no expone herramientas ni servidor MCP.

Desde esta carpeta, ejecutar `uv sync --frozen`. La configuración se lee del entorno y de un archivo
`.env` local opcional; `.env.example` documenta los valores de desarrollo. No almacenar credenciales
de sesión ni secretos en estos archivos.

Verificación completa desde la raíz: `python ai/verify.py`. Ejecuta sincronización congelada,
ruff (lint y formato), mypy estricto y pytest con cobertura mínima de 80% por componente.
Las pruebas actuales verifican únicamente la configuración y no usan red ni modelos.
