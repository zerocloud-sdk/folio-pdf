"""Public command contract for the offline Noto reference-data producer.

Run with fontTools 4.59.2 and T28_UPSTREAM pointing at the pinned downloads.
This is acceptance tooling; it is not a product dependency or font lookup.
"""
import os
from pathlib import Path
import subprocess
import struct
import sys
import tempfile
import unittest

from fontTools.ttLib import TTFont


ROOT = Path(__file__).resolve().parents[2]


class ReferenceFontsTest(unittest.TestCase):
    def test_unpinned_source_is_rejected_before_creating_a_reference(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "NotoSansCJKjp-VF.ttf"
            data = bytearray((Path(os.environ["T28_UPSTREAM"]) / source.name).read_bytes())
            for record in range(12, 12 + 16 * struct.unpack_from(">H", data, 4)[0], 16):
                if data[record:record + 4] == b"head":
                    offset = struct.unpack_from(">I", data, record + 8)[0]
                    data[offset + 20] ^= 1  # Valid timestamp change; FontTools alone admits it.
                    break
            source.write_bytes(data)
            target = root / "reference.ttf"
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/t28-reference-fonts.py"),
                 str(source), str(target)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("SHA-256", result.stderr)
            self.assertFalse(target.exists())

    def test_regular_instance_retains_the_complete_regional_font(self):
        upstream = Path(os.environ["T28_UPSTREAM"])
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "NotoSansCJKjp-Regular.ttf"
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/t28-reference-fonts.py"),
                 str(upstream / "NotoSansCJKjp-VF.ttf"), str(target)],
                capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            with TTFont(target) as font:
                self.assertNotIn("fvar", font)
                self.assertNotIn("gvar", font)
                self.assertEqual(65535, font["maxp"].numGlyphs)
                self.assertEqual(400, font["OS/2"].usWeightClass)
                self.assertEqual("NotoSansCJKJP-Regular", font["name"].getDebugName(6))
                self.assertIn("GPOS", font)
                self.assertIn("GSUB", font)
                self.assertIn(0x9AA8, font.getBestCmap())
                self.assertIn(0x2000B, font.getBestCmap())
                self.assertIn("SIL Open Font License", font["name"].getDebugName(13))


if __name__ == "__main__":
    unittest.main()
