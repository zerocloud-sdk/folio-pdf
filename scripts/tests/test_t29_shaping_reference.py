"""Public CLI contract for the independent, offline T29 reference producer.

Run with fontTools 4.59.2 and T29_HB_SHAPE naming official hb-shape 10.2.0.
Expected glyph literals were observed directly with that official tool before
the project reference writer, adapter or Composition shaping existed.
"""
import json
import csv
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ShapingReferenceTest(unittest.TestCase):
    def test_java_acceptance_inputs_keep_the_frozen_numeric_and_logical_run_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "reference"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-shaping-reference.py"),
                                     os.environ["T29_HB_SHAPE"], str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            with (target / "T29-glyphs.tsv").open() as stream:
                rows = list(csv.DictReader(stream, delimiter="\t"))
            arabic = [r for r in rows if r["profile"] == "arabic" and r["line"] == "2"]
            self.assertEqual(["292", "704"], [r["gid"] for r in arabic])
            self.assertEqual(["0644064e0627"] * 2, [r["run_hex"] for r in arabic])
            self.assertEqual(["0"] * 2, [r["cluster_start"] for r in arabic])
            self.assertEqual(["3"] * 2, [r["cluster_end"] for r in arabic])
            self.assertEqual([("Arab", "ar")] * 2, [(r["script"], r["language"]) for r in arabic])
            latin = [r for r in rows if r["font"] == "NotoSans-Regular.ttf"]
            self.assertEqual({("Latn", "en")}, {(r["script"], r["language"]) for r in latin})
            self.assertEqual(("249", "256", "26.988", "106.764"),
                             tuple(arabic[0][key] for key in ("dx", "dy", "x", "y")))
            self.assertEqual({str(page) for page in range(1, 9)}, {r["page"] for r in rows})
            corpus = (target / "T29-corpus.properties").read_text()
            self.assertIn("arabic.5.text=ا َ\n", corpus)
            self.assertIn("devanagari.4.width=16\n", corpus)

    def test_fallback_oracle_keeps_a_space_and_script_mark_in_one_script_font(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "reference"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-shaping-reference.py"),
                                     os.environ["T29_HB_SHAPE"], str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            oracle = json.loads((target / "T29-oracle.json").read_text())
            for profile, text, font, gids in [
                    ("arabic", "ا \u064e", "NotoSansArabic-Regular.ttf", [292, 3, 46]),
                    ("hebrew", "ש \u05b8", "NotoSansHebrew-Regular.ttf", [79, 106, 96]),
                    ("devanagari", "क \u093f", "NotoSansDevanagari-Regular.ttf", [25, 3, 67, 134]),
                    ("thai", "ก \u0e34", "NotoSansThai-Regular.ttf", [29, 111, 92])]:
                with self.subTest(profile=profile):
                    line = oracle["profiles"][profile][5]
                    self.assertEqual(2, line["page"])
                    self.assertEqual(1, len(line["runs"]))
                    run = line["runs"][0]
                    self.assertEqual(text, run["text"])
                    self.assertEqual(font, run["font"])
                    self.assertEqual(gids, [g["g"] for g in run["glyphs"]])
                    self.assertEqual({3}, {g["end"] for g in run["glyphs"] if g["cluster"] == 1})

    def test_official_tool_reporting_a_mismatched_linked_engine_cannot_publish_a_reference(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shim = root / "wrong-engine-version.so"
            subprocess.run(["cc", "-shared", "-fPIC",
                            str(ROOT / "scripts/tests/fixtures/t29-version-mismatch.c"),
                            "-o", str(shim)], check=True, capture_output=True)
            environment = dict(os.environ, LD_PRELOAD=str(shim))
            target = root / "reference"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-shaping-reference.py"),
                                     os.environ["T29_HB_SHAPE"], str(target)],
                                    capture_output=True, text=True, env=environment)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(target.exists())

    def test_oracle_and_pdf_do_not_depend_on_native_installation_path(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            relocated = root / "relocated" / "hb-shape"
            relocated.parent.mkdir()
            shutil.copy2(os.environ["T29_HB_SHAPE"], relocated)
            for name, executable in [("first", os.environ["T29_HB_SHAPE"]), ("second", relocated)]:
                result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-shaping-reference.py"),
                                         str(executable), str(root / name)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
            for name in ["T29-oracle.json", "T29-shaping-reference.pdf"]:
                self.assertEqual((root / "first" / name).read_bytes(), (root / "second" / name).read_bytes())

    def test_reference_retains_ligatures_clusters_offsets_and_glyph_only_subsets(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "reference"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-shaping-reference.py"),
                                     os.environ["T29_HB_SHAPE"], str(target)],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            oracle = json.loads((target / "T29-oracle.json").read_text())
            self.assertEqual({"arabic", "hebrew", "devanagari", "thai"}, set(oracle["profiles"]))
            arabic = oracle["profiles"]["arabic"][1]["runs"][0]["glyphs"]
            self.assertEqual([292, 704], [g["g"] for g in arabic])
            self.assertEqual([0, 0], [g["cluster"] for g in arabic])
            self.assertEqual([3, 3], [g["end"] for g in arabic])
            self.assertEqual((249, 256, 0), (arabic[0]["dx"], arabic[0]["dy"], arabic[0]["ax"]))
            self.assertEqual(582, arabic[1]["ax"])
            deva = oracle["profiles"]["devanagari"][0]["runs"][0]["glyphs"]
            self.assertEqual([610, 179], [g["g"] for g in deva])
            thai = oracle["profiles"]["thai"][0]["runs"][0]["glyphs"]
            self.assertEqual([71, 59, 49, 86], [g["g"] for g in thai])
            self.assertEqual(-29, thai[2]["dx"])
            pdf = (target / "T29-shaping-reference.pdf").read_bytes()
            self.assertTrue(pdf.startswith(b"%PDF-1.7"))
            self.assertIn(b"/Count 8", pdf)
            self.assertIn(b"/ActualText", pdf)
            self.assertIn(b"/CIDToGIDMap /Identity", pdf)
            self.assertIn(b"1 0 0 1 26.988 106.764 Tm <0124>", pdf)
            receipt = json.loads((target / "T29-reference-receipt.json").read_text())
            self.assertEqual("10.2.0", receipt["harfbuzz"])
            self.assertEqual("4.59.2", receipt["fontTools"])
            self.assertEqual("c1f95d16a61a8eb4f0c60f980522d57bf0c8fddbf62851c49cf77d2664b8044b",
                             receipt["native_library_sha256"])


if __name__ == "__main__":
    unittest.main()
