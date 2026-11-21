"""Behavioral checks for the integration verification entry point."""

import contextlib
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "verify_integracion", Path(__file__).with_name("verify_integracion.py")
)
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


class VerifyIntegrationTest(unittest.TestCase):
    def test_runs_each_component_and_reports_failure(self):
        root = Path(".").resolve()
        with patch.object(Path, "is_file", return_value=True), patch.object(
            Path, "mkdir"
        ), patch.object(Path, "write_text") as write_log:
            results = [
                unittest.mock.Mock(returncode=0, stdout="success", stderr=""),
                unittest.mock.Mock(returncode=1, stdout="", stderr="[ERROR] failed"),
            ]
            with patch.object(verify, "ROOT", root), patch.object(
                verify, "COMPONENTS", ["first", "second"]
            ), patch.object(verify.subprocess, "run", side_effect=results) as run:
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(verify.main(), 1)
            self.assertEqual(run.call_count, 2)
            self.assertEqual(run.call_args.args[0][1:], ["-q", "-B", "verify"])
            self.assertEqual(write_log.call_args.args[0], "[ERROR] failed")

    def test_successful_component_returns_zero(self):
        root = Path(".").resolve()
        with patch.object(Path, "is_file", return_value=True), patch.object(
            Path, "mkdir"
        ), patch.object(Path, "write_text"):
            result = unittest.mock.Mock(returncode=0, stdout="", stderr="")
            with patch.object(verify, "ROOT", root), patch.object(
                verify, "COMPONENTS", ["."]
            ), patch.object(verify.subprocess, "run", return_value=result):
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(verify.main(), 0)


if __name__ == "__main__":
    unittest.main()
