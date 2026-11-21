"""Check that the repository verifier propagates Maven's result."""

import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[3] / "scripts" / "verify_ia.py"
SPEC = importlib.util.spec_from_file_location("verify_ia", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
verify_ia = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify_ia)


class VerifyIaTest(unittest.TestCase):
    def test_maven_exit_codes_are_propagated(self):
        for exit_code in (0, 1):
            with self.subTest(exit_code=exit_code):
                result = subprocess.CompletedProcess([], exit_code, "", "")
                with patch.object(verify_ia.subprocess, "run", return_value=result) as run:
                    with patch.object(Path, "write_text"), contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify_ia.main(), exit_code)
                args, kwargs = run.call_args
                self.assertEqual(args[0][1:], ["-q", "verify"])
                self.assertEqual(kwargs["cwd"], verify_ia.ROOT / "services/wa-users-service")


if __name__ == "__main__":
    unittest.main()
