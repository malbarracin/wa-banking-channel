"""Entrypoint operativo con /health. FastMCP Streamable HTTP se agrega en un paso posterior."""

from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from wa_mcp_layer.settings import Settings


class HealthHandler(BaseHTTPRequestHandler):
    """Responde solo GET /health para el HEALTHCHECK del contenedor."""

    def do_GET(self) -> None:  # noqa: N802 — firma de BaseHTTPRequestHandler
        path = self.path.split("?", 1)[0]
        if path != "/health":
            self.send_error(404)
            return
        body = json.dumps({"status": "ok"}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:  # noqa: A003
        del format, args


_CONTAINER_BIND = "0.0.0.0"  # noqa: S104 — interfaz de contenedor / red Docker


def serve(settings: Settings | None = None, *, host: str = _CONTAINER_BIND) -> ThreadingHTTPServer:
    """Levanta el servidor de health en el puerto configurado (bloqueante vía serve_forever)."""
    cfg = settings or Settings()
    # Contenedor: host por defecto 0.0.0.0 (HEALTHCHECK / red Docker). Tests usan 127.0.0.1.
    return ThreadingHTTPServer((host, cfg.port), HealthHandler)


def main() -> None:
    server = serve()
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
