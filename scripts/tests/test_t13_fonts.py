"""Original font authoring through its CLI; run with pinned fontTools 4.59.2."""
import hashlib
from io import BytesIO
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from fontTools.ttLib import TTFont
from fontTools.cffLib import CFFFontSet
from fontTools.pens.boundsPen import BoundsPen

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t13-fonts.py'


class T13FontsTest(unittest.TestCase):
    def test_cid_truetype_has_no_cmap_and_preserves_glyph_ids_outlines_and_metrics(self):
        with tempfile.TemporaryDirectory() as temporary:
            outputs = [Path(temporary) / name for name in ('first', 'second')]
            for output in outputs:
                command = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
                self.assertEqual(0, command.returncode, command.stderr)
            path = outputs[0] / 'FolioT13RectangleCID.ttf'
            self.assertTrue(path.is_file(), 'A CIDFontType2 requires its own cmap-free TrueType program')
            self.assertEqual(path.read_bytes(), (outputs[1] / path.name).read_bytes())
            with TTFont(outputs[0] / 'FolioT13Rectangle.ttf') as simple, TTFont(path) as cid:
                self.assertIn('cmap', simple)
                self.assertNotIn('cmap', cid)
                self.assertEqual(['.notdef', 'A'], cid.getGlyphOrder())
                self.assertEqual((500, 0), cid['hmtx']['A'])
                self.assertEqual([(0, 0), (400, 0), (400, 600), (0, 600)], list(cid['glyf']['A'].coordinates))
                for table in ('glyf', 'loca', 'hmtx', 'hhea', 'maxp', 'name', 'post'):
                    self.assertEqual(simple.getTableData(table), cid.getTableData(table), table)
                # OS/2 first/last character fields follow cmap removal; its metrics remain fixed.
                self.assertEqual((600, 600, 0),
                                 (cid['OS/2'].sCapHeight, cid['OS/2'].sTypoAscender, cid['OS/2'].sTypoDescender))
            record = json.loads((outputs[0] / 'fonts.json').read_text())
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), record['fonts'][path.name]['sha256'])

    def test_original_equal_width_font_programs_declare_fixed_pitch(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'fonts'
            command = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, command.returncode, command.stderr)
            with TTFont(output / 'FolioT13Rectangle.ttf') as font:
                self.assertEqual(1, font['post'].isFixedPitch)
            for name in ('FolioT13Rectangle.cff', 'FolioT13RectangleCID.cff'):
                cff = CFFFontSet()
                cff.decompile(BytesIO((output / name).read_bytes()), None)
                self.assertTrue(cff.topDictIndex[0].isFixedPitch)

    def test_original_cid_cff_binds_cid_one_to_the_literal_rectangle_with_one_font_dictionary(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'fonts'
            command = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, command.returncode, command.stderr)
            path = output / 'FolioT13RectangleCID.cff'
            data = path.read_bytes()
            cff = CFFFontSet()
            cff.decompile(BytesIO(data), None)
            self.assertEqual(['FolioT13RectangleCID'], cff.fontNames)
            top = cff.topDictIndex[0]
            self.assertEqual(('Folio', 'T13', 0), top.ROS)
            self.assertEqual(['.notdef', 'cid00001'], top.charset)
            self.assertEqual(2, top.CIDCount)
            self.assertEqual(1, len(top.FDArray))
            self.assertEqual([0, 0], top.FDSelect.gidArray)
            pen = BoundsPen(None)
            glyph = top.CharStrings['cid00001']
            glyph.draw(pen)
            self.assertEqual((0, 0, 400, 600), pen.bounds)
            self.assertEqual(500, glyph.width)
            record = json.loads((output / 'fonts.json').read_text())
            self.assertEqual(hashlib.sha256(data).hexdigest(), record['fonts'][path.name]['sha256'])
            second = Path(temporary) / 'second'
            self.assertEqual(0, subprocess.run([sys.executable, str(SCRIPT), str(second)], capture_output=True).returncode)
            self.assertEqual(data, (second / path.name).read_bytes())

    def test_original_type1_cff_snapshot_has_the_same_literal_rectangle_and_advance(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'fonts'
            command = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, command.returncode, command.stderr)
            path = output / 'FolioT13Rectangle.cff'
            data = path.read_bytes()
            cff = CFFFontSet()
            cff.decompile(BytesIO(data), None)
            self.assertEqual(['FolioT13RectangleCFF'], cff.fontNames)
            top = cff.topDictIndex[0]
            self.assertFalse(hasattr(top, 'ROS'))
            self.assertEqual(['.notdef', 'A'], top.charset)
            self.assertEqual([.001, 0, 0, .001, 0, 0], top.FontMatrix)
            pen = BoundsPen(None)
            glyph = top.CharStrings['A']
            glyph.draw(pen)
            self.assertEqual((0, 0, 400, 600), pen.bounds)
            self.assertEqual(500, glyph.width)
            record = json.loads((output / 'fonts.json').read_text())
            self.assertEqual(hashlib.sha256(data).hexdigest(), record['fonts'][path.name]['sha256'])
            second = Path(temporary) / 'second'
            self.assertEqual(0, subprocess.run([sys.executable, str(SCRIPT), str(second)], capture_output=True).returncode)
            self.assertEqual(data, (second / path.name).read_bytes())

    def test_original_truetype_rectangle_has_literal_metrics_and_reproducible_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            outputs = [Path(temporary) / name for name in ('first', 'second')]
            for output in outputs:
                command = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
                self.assertEqual(0, command.returncode, command.stderr)
            font_path = outputs[0] / 'FolioT13Rectangle.ttf'
            self.assertEqual(font_path.read_bytes(), (outputs[1] / font_path.name).read_bytes())
            with TTFont(font_path, recalcTimestamp=False) as font:
                self.assertEqual(1000, font['head'].unitsPerEm)
                self.assertEqual(2082844800, font['head'].created)
                self.assertEqual(2082844800, font['head'].modified)
                self.assertEqual(['.notdef', 'A'], font.getGlyphOrder())
                self.assertEqual({65: 'A'}, font.getBestCmap())
                self.assertEqual((500, 0), font['hmtx']['A'])
                glyph = font['glyf']['A']
                self.assertEqual((0, 0, 400, 600), (glyph.xMin, glyph.yMin, glyph.xMax, glyph.yMax))
                self.assertEqual([(0, 0), (400, 0), (400, 600), (0, 600)], list(glyph.coordinates))
                self.assertEqual(1, glyph.numberOfContours)
                self.assertEqual('FolioT13Rectangle', font['name'].getDebugName(6))
            record = json.loads((outputs[0] / 'fonts.json').read_text())
            self.assertEqual('4.59.2', record['fonttools-version'])
            self.assertEqual('Apache-2.0', record['fixture-license'])
            self.assertEqual(hashlib.sha256(font_path.read_bytes()).hexdigest(), record['fonts'][font_path.name]['sha256'])
            original = {file.name: file.read_bytes() for file in outputs[0].iterdir()}
            refused = subprocess.run([sys.executable, str(SCRIPT), str(outputs[0])], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(original, {file.name: file.read_bytes() for file in outputs[0].iterdir()})


if __name__ == '__main__':
    unittest.main()
