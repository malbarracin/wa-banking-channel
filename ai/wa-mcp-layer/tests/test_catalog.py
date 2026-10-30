"""Contrato publicado y registro versionado del catálogo (TASK-129)."""

from pathlib import Path

import pytest

from wa_mcp_layer.catalog import CATALOG, Capability, resolve

CONTRACT = Path(__file__).resolve().parents[3] / "shared" / "contracts" / "wa-mcp-layer"
SCHEMAS = CONTRACT / "schemas"

EXPECTED = {
    "identidad.consultar_cliente",
    "identidad.consultar_estado_cliente",
    "acceso.emitir_credencial",
    "acceso.validar_credencial",
    "acceso.revocar_credencial",
    "acceso.revocar_credenciales_masivo",
    "acceso.consultar_historial_credencial",
    "vinculo.aceptar_terminos",
    "vinculo.verificar_identidad",
    "vinculo.consultar_estado",
    "vinculo.consultar_perfil",
    "vinculo.actualizar_preferencias",
    "vinculo.bloquear",
    "vinculo.desvincular",
    "vinculo.consultar_historial",
    "cuentas.listar_cuentas",
    "cuentas.consultar_saldo",
    "cuentas.consultar_movimientos",
    "cuentas.listar_terceros",
    "cuentas.preparar_transferencia",
    "cuentas.confirmar_transferencia",
    "tarjetas.listar_tarjetas",
    "tarjetas.consultar_consumos",
    "tarjetas.bloquear_temporal",
    "tarjetas.desbloquear_temporal",
    "tarjetas.bloquear_definitivo",
    "tarjetas.registrar_aviso_viaje",
    "seguros.listar_polizas",
    "seguros.consultar_coberturas",
    "seguros.denunciar_siniestro",
    "seguros.consultar_siniestro",
    "seguros.consultar_documentacion_pendiente",
}


def test_catalog_publishes_exactly_32_capabilities() -> None:
    assert set(CATALOG) == EXPECTED
    assert len(CATALOG) == 32
    for name, entry in CATALOG.items():
        assert isinstance(entry, Capability)
        assert entry.nombre == name
        assert entry.version == 1
        assert entry.servicio in {"H1", "H2", "H3", "H4", "H5", "H6"}
        assert entry.actor in {"agente", "soporte", "banco"}
        assert entry.handler
        assert entry.tool_name == f"{name.replace('.', '_')}_v1"
        assert callable(entry.is_enabled)


def test_published_contract_files() -> None:
    assert (CONTRACT / "capabilities.md").is_file()
    assert (SCHEMAS / "_envelope.json").is_file()
    for name in EXPECTED:
        path = SCHEMAS / f"{name}.v1.schema.json"
        assert path.is_file(), path.name
        text = path.read_text(encoding="utf-8")
        assert '"entrada"' in text and '"salida"' in text
        assert f'"nombre": "{name}"' in text


def test_resolve_published_capability() -> None:
    entry = resolve("cuentas.listar_cuentas", actor="agente")
    assert entry is not None
    assert entry.requiere_credencial is True
    assert entry.handler == "listar_cuentas"
    assert entry.is_enabled() is True


def test_resolve_unpublished_capability() -> None:
    assert resolve("inventada.operacion", actor="agente") is None
    assert resolve("cuentas.listar_cuentas", actor="soporte") is None


def test_resolve_disabled_by_default_and_by_env(monkeypatch: pytest.MonkeyPatch) -> None:
    masivo = CATALOG["acceso.revocar_credenciales_masivo"]
    assert masivo.habilitada is False
    assert masivo.actor == "banco"
    assert resolve("acceso.revocar_credenciales_masivo", actor="banco") is None

    monkeypatch.setenv(masivo.env_flag, "true")
    enabled = resolve("acceso.revocar_credenciales_masivo", actor="banco")
    assert enabled is not None and enabled.is_enabled() is True

    monkeypatch.setenv("MCP_CAPABILITY_CUENTAS_LISTAR_CUENTAS_ENABLED", "false")
    assert resolve("cuentas.listar_cuentas", actor="agente") is None
