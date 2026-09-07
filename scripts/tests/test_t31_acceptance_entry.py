"""Exercise the repository-only T31 Maven entry with explicitly unavailable tools."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class T31AcceptanceEntryTest(unittest.TestCase):
    def test_dedicated_profile_records_both_workflows_and_no_earlier_profiles(self):
        with tempfile.TemporaryDirectory(prefix="folio-t31-entry-") as temporary:
            directory = Path(temporary)
            pins = directory / "pins"
            pins.mkdir()
            output = directory / "evidence"
            command = [str(ROOT / "mvnw"), "-B", "-ntp", "-pl", "pdf-acceptance", "-am",
                       "-Pacceptance-t31-record", "-DskipTests", "-Dacceptance.output=" + str(output)]
            for name in ("qpdf", "pdfium", "imagemagick"):
                pin = pins / (name + ".properties")
                shutil.copyfile(ROOT / "scripts" / (name + "-pin.properties"), pin)
                command.append("-Dt31." + name + ".pin=" + str(pin))
            command.append("verify")
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            semantic = output / "T31-two-dimensional-barcodes-semantic.md"
            self.assertTrue(semantic.is_file(), result.stdout + result.stderr)
            self.assertIn("Result: `pass`", semantic.read_text())
            self.assertIn("Decoded pages: `448`", semantic.read_text())
            for chain in ("syntax", "visual", "standards"):
                self.assertIn("Result: `indeterminate`",
                              (output / ("T31-two-dimensional-barcodes-" + chain + ".md")).read_text())
            for suffix in ("in-process", "hardened-worker", "in-process-reference", "hardened-worker-reference"):
                self.assertTrue((output / "artifacts" / ("T31-two-dimensional-barcodes-" + suffix + ".pdf")).is_file())
            self.assertFalse((output / "T29-shaping-semantic.md").exists())
            self.assertFalse((output / "T30-one-dimensional-barcodes-semantic.md").exists())


if __name__ == "__main__":
    unittest.main()
