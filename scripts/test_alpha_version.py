import subprocess
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("alpha_version.py")


class AlphaVersionTests(unittest.TestCase):
    def run_version(self, version, code, run_number):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--base-version",
                version,
                "--base-code",
                str(code),
                "--run-number",
                str(run_number),
            ],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_three_segment_stable_date_uses_zero_mod_slot(self):
        result = self.run_version("26.10.06", 522012000, 1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.splitlines(),
            ["name=26.10.06-alpha.1", "code=522013001", "tag=v26.10.06-alpha.1"],
        )

    def test_four_segment_modification_keeps_alpha_code_date_based(self):
        result = self.run_version("26.10.06.007", 522012007, 42)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.splitlines(),
            ["name=26.10.06.007-alpha.42", "code=522013042", "tag=v26.10.06.007-alpha.42"],
        )

    def test_rejects_stable_code_that_does_not_match_name(self):
        result = self.run_version("26.10.06", 522012001, 1)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not match", result.stderr)

    def test_rejects_invalid_calendar_date(self):
        result = self.run_version("26.02.30", 522004000, 1)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("valid date", result.stderr)

    def test_rejects_invalid_version_shape_and_alpha_run(self):
        for version, code, run_number in (
            ("26.10.06.1", 522012001, 1),
            ("26.10.06", 522012000, 0),
            ("26.10.06", 522012000, 1000),
        ):
            with self.subTest(version=version, run_number=run_number):
                result = self.run_version(version, code, run_number)
                self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
