"""Reopened reference-font outlines, observed at the independent verifier CLI."""
from pathlib import Path
from io import BytesIO
import base64
import json
import subprocess
import sys
import tempfile
import unittest

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping"


class ReferenceSubsetsTest(unittest.TestCase):
    def test_reopened_cid_to_gid_mapping_selects_the_required_substituted_outline(self):
        qpdf = str(ROOT / "scripts/container-bin/qpdf")
        with tempfile.TemporaryDirectory() as temporary:
            objects = json.loads(subprocess.check_output([qpdf, "--json", "--json-stream-data=inline",
                                str(RESOURCES / "T29-shaping-reference.pdf")], text=True))
            graph = objects["qpdf"][1]
            reference = str(1 + max(int(key[4:].split()[0]) for key in graph if key.startswith("obj:"))) + " 0 R"
            mapping = bytearray(4002)
            for gid in range(653):
                mapping[gid * 2:gid * 2 + 2] = gid.to_bytes(2, "big")
            mapping[2000 * 2:2000 * 2 + 2] = (610).to_bytes(2, "big")
            mapping[610 * 2:610 * 2 + 2] = b"\x00\x00"
            for obj in list(graph.values()):
                value = obj.get("value", {})
                if isinstance(value, dict) and value.get("/Subtype") == "/CIDFontType2" \
                        and value.get("/BaseFont", "").endswith("NotoSansDevanagari-Regular"):
                    value["/CIDToGIDMap"] = reference
            graph["obj:" + reference] = {"stream": {"dict": {}, "data": base64.b64encode(mapping).decode("ascii")}}
            changed_glyphs = 0
            for ref in objects["pages"][4]["contents"]:
                stream = graph["obj:" + ref]["stream"]
                data = base64.b64decode(stream["data"])
                changed_glyphs += data.count(b"<0262> Tj")
                stream["data"] = base64.b64encode(data.replace(b"<0262> Tj", b"<07D0> Tj")).decode("ascii")
            self.assertEqual(1, changed_glyphs)
            root = Path(temporary)
            changed = root / "mapped.json"
            changed.write_text(json.dumps(objects))
            pdf = root / "mapped.pdf"
            subprocess.run([qpdf, "--json-input", str(changed), str(pdf)], check=True, capture_output=True)
            result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-verify-subsets.py"), qpdf,
                                     str(pdf), str(RESOURCES / "T29-oracle.json")], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("pass", json.loads(result.stdout)["result"])

    def test_omitting_a_shaped_glyph_or_its_component_is_detected_after_reopening(self):
        qpdf = str(ROOT / "scripts/container-bin/qpdf")
        source = RESOURCES / "T29-shaping-reference.pdf"
        for omitted in (610, 607):
            with self.subTest(omitted=omitted), tempfile.TemporaryDirectory() as temporary:
                objects = json.loads(subprocess.check_output(
                    [qpdf, "--json", "--json-stream-data=inline", str(source)], text=True))
                graph = objects["qpdf"][1]
                for obj in graph.values():
                    value = obj.get("value", {})
                    if isinstance(value, dict) and value.get("/FontName") == "/TREFAB+NotoSansDevanagari-Regular":
                        stream = graph["obj:" + value["/FontFile2"]]["stream"]
                        with TTFont(BytesIO(base64.b64decode(stream["data"]))) as font:
                            font["glyf"][font.getGlyphName(omitted)] = Glyph()
                            data = BytesIO()
                            font.save(data)
                        stream["data"] = base64.b64encode(data.getvalue()).decode("ascii")
                        stream["dict"]["/Length1"] = len(data.getvalue())
                        break
                root = Path(temporary)
                changed = root / "changed.json"
                changed.write_text(json.dumps(objects))
                pdf = root / "missing-outline.pdf"
                subprocess.run([qpdf, "--json-input", str(changed), str(pdf)], check=True, capture_output=True)
                result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-verify-subsets.py"),
                                         qpdf, str(pdf), str(RESOURCES / "T29-oracle.json")],
                                        capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("outline or composite component", result.stderr)

    def test_reopened_subsets_preserve_substituted_outlines_and_composite_components(self):
        result = subprocess.run([sys.executable, str(ROOT / "scripts/t29-verify-subsets.py"),
                                 str(ROOT / "scripts/container-bin/qpdf"),
                                 str(RESOURCES / "T29-shaping-reference.pdf"),
                                 str(RESOURCES / "T29-oracle.json")], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        receipt = json.loads(result.stdout)
        self.assertEqual("pass", receipt["result"])
        required = receipt["fonts"]["NotoSansDevanagari-Regular.ttf"]
        self.assertIn(610, required["shaped_glyphs"])
        self.assertIn(619, required["shaped_glyphs"])
        self.assertIn(607, required["composite_dependencies"])
        self.assertIn(6, required["composite_dependencies"])


if __name__ == "__main__":
    unittest.main()
