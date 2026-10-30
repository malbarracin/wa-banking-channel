"""Health del entrypoint operativo (sin FastMCP todavía)."""

from __future__ import annotations

import json
import socket
import threading
from http.client import HTTPConnection

from wa_mcp_layer.server import serve
from wa_mcp_layer.settings import Settings


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def test_health_endpoint_returns_ok() -> None:
    server = serve(Settings(port=_free_port()), host="127.0.0.1")
    _host, port = server.server_address[:2]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        conn = HTTPConnection("127.0.0.1", port, timeout=2)
        conn.request("GET", "/health")
        response = conn.getresponse()
        body = json.loads(response.read().decode("utf-8"))
        assert response.status == 200
        assert body == {"status": "ok"}
        conn.request("GET", "/mcp")
        missing = conn.getresponse()
        assert missing.status == 404
        missing.read()
        conn.close()
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
