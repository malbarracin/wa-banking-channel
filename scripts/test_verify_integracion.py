"""Pruebas del descubrimiento y de la propagación de fallos del gate del equipo."""
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
import os
import subprocess
import unittest
from unittest.mock import patch

import verify_integracion


class VerifyIntegracionTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parent.parent
        script = self.root / "scripts" / "verify_integracion.py"
        self.file_patch = patch.object(verify_integracion, "__file__", str(script))
        self.file_patch.start()
        self.addCleanup(self.file_patch.stop)
        self.projects = []
        glob_patch = patch.object(Path, "glob", side_effect=lambda pattern: [
            pom for pom in self.projects
            if pom.relative_to(self.root).match(pattern)
        ])
        glob_patch.start()
        self.addCleanup(glob_patch.stop)
        self.stdout = StringIO()
        self.stderr = StringIO()

    def component(self, relative):
        component = self.root / relative
        self.projects.append(component / "pom.xml")
        return component

    def run_gate(self):
        with redirect_stdout(self.stdout), redirect_stderr(self.stderr):
            return verify_integracion.main()

    def test_discovers_both_roots_and_uses_each_component_wrapper(self):
        gateway = self.component("gateway/example-gateway")
        platform = self.component("platform/example-platform")
        self.component("services/out-of-scope")
        self.component("platform/nested/ignored")
        with patch.object(verify_integracion.subprocess, "run",
                          return_value=subprocess.CompletedProcess([], 0)) as run:
            self.assertEqual(0, self.run_gate())
        self.assertEqual(2, run.call_count)
        wrapper = "mvnw.cmd" if os.name == "nt" else "mvnw"
        for call, component in zip(run.call_args_list, [gateway, platform]):
            self.assertEqual(([str(component / wrapper), "-q", "-B", "verify"],), call.args)
            self.assertEqual({"cwd": component, "check": False}, call.kwargs)

    def test_failed_build_returns_failure_and_still_checks_next_component(self):
        self.component("gateway/failing")
        self.component("platform/passing")
        with patch.object(verify_integracion.subprocess, "run", side_effect=[
            subprocess.CompletedProcess([], 2), subprocess.CompletedProcess([], 0)
        ]) as run:
            self.assertEqual(1, self.run_gate())
        self.assertEqual(2, run.call_count)
        self.assertIn(str(Path("gateway/failing")), self.stderr.getvalue())

    def test_missing_wrapper_returns_failure_and_still_checks_next_component(self):
        self.component("gateway/missing-wrapper")
        self.component("platform/passing")
        with patch.object(verify_integracion.subprocess, "run", side_effect=[
            FileNotFoundError("wrapper ausente"), subprocess.CompletedProcess([], 0)
        ]) as run:
            self.assertEqual(1, self.run_gate())
        self.assertEqual(2, run.call_count)
        self.assertIn("wrapper ausente", self.stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
