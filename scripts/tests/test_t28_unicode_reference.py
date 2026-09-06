"""Contract for the independent, offline T28 reference PDF/geometry producer."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class UnicodeReferenceTest(unittest.TestCase):
    def test_reference_uses_manual_lines_and_original_font_metrics(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "oracle"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/t28-unicode-reference.py"), str(target)],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            rows = (target / "T28-glyphs.tsv").read_text().splitlines()
            self.assertIn("latin\t0\t0\t0041\tNotoSans-Regular\t36\t72.000\t707.196\t7.668", rows)
            self.assertIn("latin\t2\t0\t0301\tNotoSans-Regular\t2663\t82.788\t707.196\t0.000", rows)
            self.assertIn("latin\t3\t1\t0042\tNotoSans-Regular\t37\t72.000\t659.196\t7.800", rows)
            self.assertEqual({"latin", "greek", "cyrillic", "cjk-sc", "cjk-tc", "cjk-jp", "cjk-kr"},
                             {row.split("\t")[0] for row in rows[1:]})
            pdf = (target / "T28-unicode-reference.pdf").read_bytes()
            self.assertTrue(pdf.startswith(b"%PDF-1.7"))
            self.assertIn(b"/Count 7", pdf)
            self.assertIn(b"/FontFile2", pdf)
            self.assertIn(b"1 0 0 1 82.788 707.196 Tm", pdf)
            self.assertIn(b"1 0 0 1 72.000 659.196 Tm", pdf)
            self.assertIn(b"/ToUnicode", pdf)


if __name__ == "__main__":
    unittest.main()
