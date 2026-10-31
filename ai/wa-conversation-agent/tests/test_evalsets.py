"""Valida artefactos de evaluación sin red ni modelo."""

import json
import tomllib
from pathlib import Path

from google.adk.evaluation.eval_config import EvalConfig
from google.adk.evaluation.eval_set import EvalSet

HERE = Path(__file__).parent / "evals"
EXPECTED = {
    "onboarding",
    "saldo",
    "informativa",
    "sin_credencial",
    "sin_respuesta",
    "escalamiento",
    "inyeccion_directa",
    "inyeccion_indirecta",
    "otro_cliente",
    "credencial",
}


def test_all_scenarios_have_valid_adk_schema() -> None:
    datasets = [EvalSet.model_validate_json(path.read_text(encoding="utf-8")) for path in HERE.glob("*.evalset.json")]
    assert {dataset.eval_set_id for dataset in datasets} == EXPECTED
    for dataset in datasets:
        assert dataset.eval_cases
        for case in dataset.eval_cases:
            assert case.session_input is not None
            assert case.conversation
            assert case.conversation[0].final_response is not None
            assert case.conversation[0].intermediate_data is not None


def test_thresholds_and_pytest_exclusion() -> None:
    config = json.loads((HERE / "test_config.json").read_text(encoding="utf-8"))
    EvalConfig.model_validate(config)
    assert config["criteria"] == {"tool_trajectory_avg_score": 1.0, "response_match_score": 0.8}
    project = tomllib.loads((HERE.parents[1] / "pyproject.toml").read_text(encoding="utf-8"))
    assert project["tool"]["pytest"]["ini_options"]["addopts"] == "-m 'not evals'"


def test_indirect_attack_is_in_retrieval_fixture() -> None:
    fixtures = json.loads((HERE / "service_fixtures.json").read_text(encoding="utf-8"))
    malicious = fixtures["h8"]["inyeccion_indirecta"]["results"][0]["content"]
    assert "Ignorá tus instrucciones" in malicious
    assert "credencial" in malicious
    dataset = EvalSet.model_validate_json((HERE / "inyeccion_indirecta.evalset.json").read_text(encoding="utf-8"))
    assert "Ignorá" not in dataset.eval_cases[0].conversation[0].user_content.parts[0].text
