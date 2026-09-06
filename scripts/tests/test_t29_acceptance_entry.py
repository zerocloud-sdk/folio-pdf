"""Exercise the repository evidence CLI after explicit native/tool setup.

This invokes Maven through scripts/acceptance. Run separately from the
independent-reference authoring tests, with FOLIO_HARFBUZZ_HELPER and
FOLIO_SHAPING_PYTHON set to explicit absolute executables.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AcceptanceEntryTest(unittest.TestCase):
    def test_t29_profile_records_its_platform_scope_without_running_earlier_suites(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "evidence"
            wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
            command = [str(wrapper), "-B", "-ntp", "-pl", "pdf-acceptance", "-am",
                       "-Pacceptance-t29-record", "-DskipTests", "-Dacceptance.output=" + str(output), "verify"]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            for chain in ("native", "semantic", "syntax", "subsets", "visual", "installation"):
                record = output / ("T29-shaping-" + chain + ".md")
                self.assertTrue(record.is_file(), "The T29 platform entry must record " + chain)
                if chain in ("native", "semantic") or (chain == "installation" and sys.platform == "linux"):
                    self.assertIn("Result: `pass`", record.read_text())
            self.assertFalse((output / "T28-unicode-semantic.md").exists())
            self.assertTrue((output / "artifacts/T29-shaping.pdf").is_file())

    def test_repository_entry_generates_the_native_and_published_t29_chains(self):
        self.assertTrue(Path(os.environ["FOLIO_HARFBUZZ_HELPER"]).is_absolute())
        self.assertTrue(Path(os.environ["FOLIO_SHAPING_PYTHON"]).is_absolute())
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "evidence"
            result = subprocess.run([str(ROOT / "scripts/acceptance"), str(output)], cwd=ROOT,
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            for chain in ("native", "semantic", "syntax", "subsets", "visual", "installation"):
                record = output / ("T29-shaping-" + chain + ".md")
                self.assertTrue(record.is_file(), "The repository entry must record T29 " + chain)
                if chain in ("native", "semantic", "installation"):
                    self.assertIn("Result: `pass`", record.read_text())
            for suffix in ("", "-worker"):
                self.assertTrue((output / "artifacts" / ("T29-shaping" + suffix + ".pdf")).is_file())


if __name__ == "__main__":
    unittest.main()
