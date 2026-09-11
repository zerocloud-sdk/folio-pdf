"""Public generator checks against independently worked T11 expectations."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from test_t10_corpus import rgb

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t11-corpus.py'


class T11CorpusTest(unittest.TestCase):
    def test_original_sources_expectations_and_pixels_are_pinned_and_reproducible(self):
        with tempfile.TemporaryDirectory() as temporary:
            first, second = Path(temporary) / 'first', Path(temporary) / 'second'
            for target in (first, second):
                result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
            original = {p.relative_to(first): p.read_bytes() for p in first.rglob('*') if p.is_file()}
            self.assertEqual(original, {p.relative_to(second): p.read_bytes() for p in second.rglob('*') if p.is_file()})
            corpus = json.loads((first / 'corpus.json').read_text())
            self.assertEqual('T11-metadata-outlines-destinations-attachments', corpus['profile'])
            self.assertEqual(['A', 'B', 'C'], corpus['sources']['primary']['pages'])
            self.assertEqual(['D'], corpus['sources']['appendix']['pages'])
            for source in corpus['sources'].values():
                data = (first / source['path']).read_bytes()
                self.assertTrue(data.startswith(b'%PDF-2.0\n'), 'AFRelationship requires the PDF 2.0 profile')
                self.assertEqual(source['sha256'], hashlib.sha256(data).hexdigest())
                xref = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[xref:xref + 4])
                self.assertIn(b'/Info ', data)
            self.assertEqual(['C', 'A', 'B', 'A'], corpus['products']['edited']['pages'])
            self.assertEqual(['A', 'B', 'C', 'D'], corpus['products']['merged']['pages'])
            self.assertEqual(['A', 'B'], corpus['products']['left']['pages'])
            self.assertEqual(['C', 'D'], corpus['products']['right']['pages'])
            self.assertEqual(['b', 'bh', 'bv', 'fit', 'h', 'r', 'shared', 'v', 'xyz', 'z', 'za'],
                             corpus['products']['edited']['destination-order'])
            self.assertEqual(['b', 'bh', 'bv', 'fit', 'h', 'r', 'shared', 'shared-1', 'v', 'xyz', 'z', 'za'],
                             corpus['products']['merged']['destination-order'])
            self.assertEqual(['b', 'bh', 'fit', 'h', 'r', 'shared', 'z', 'za'],
                             corpus['products']['left']['destination-order'])
            self.assertEqual(['bv', 'shared-1', 'v', 'xyz'], corpus['products']['right']['destination-order'])
            self.assertEqual({'page': 3, 'style': 'FIT', 'parameters': []},
                             corpus['products']['edited']['destinations']['shared'])
            self.assertEqual({'page': 4, 'style': 'FIT', 'parameters': []},
                             corpus['products']['merged']['destinations']['shared-1'])
            self.assertEqual({'page': 2, 'style': 'FIT', 'parameters': []},
                             corpus['products']['right']['destinations']['shared-1'])
            payload = corpus['products']['merged']['attachments'][0]
            self.assertEqual('payload.txt', payload['name'])
            self.assertEqual('616263', payload['hex'])
            self.assertEqual('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', payload['sha256'])
            self.assertEqual('900150983cd24fb0d6963f7d28e17f72', payload['md5'])
            self.assertEqual('secondary.bin', corpus['products']['merged']['attachments'][2]['name'])
            props = (first / 'corpus.properties').read_text()
            self.assertIn('products.edited.destinations.h.parameters.0=\n', props)
            self.assertIn('products.merged.attachments.1.name=payload.txt-1\n', props)
            self.assertIn('products.edited.info.Title=Changed title\n', props)
            for name, color in [('A', (255, 0, 0)), ('B', (0, 255, 0)), ('C', (0, 0, 255)), ('D', (0, 0, 0))]:
                page = corpus['pages'][name]
                raster = first / page['raster']
                self.assertEqual(page['raster-sha256'], hashlib.sha256(raster.read_bytes()).hexdigest())
                width, height, pixel = rgb(raster)
                self.assertEqual((240, 200), (width, height))
                for x, y in [(20, 100), (99, 159), (65, 125)]:
                    self.assertEqual(color, pixel(x, y))
                for x, y in [(19, 100), (100, 159), (20, 99), (99, 160), (0, 0), (239, 199)]:
                    self.assertEqual((255, 255, 255), pixel(x, y))
            profiles = list((first / 'visual').glob('*.properties'))
            self.assertEqual(12, len(profiles))
            for product, definition in corpus['products'].items():
                for selection, name in enumerate(definition['pages'], 1):
                    values = dict(line.split('=', 1) for line in (first / 'visual' / f'{product}-page-{selection}.properties').read_text().splitlines()
                                  if line and not line.startswith('#'))
                    self.assertEqual('144', values['DPI'])
                    self.assertEqual('AE', values['COMPARISON_METRIC'])
                    self.assertEqual('0', values['COMPARISON_FUZZ_PERCENT'])
                    self.assertEqual('0', values['COMPARISON_THRESHOLD'])
                    self.assertEqual('0', values['RENDERER_AGREEMENT_THRESHOLD'])
                    self.assertEqual(str(selection), values['PAGE_SELECTION'])
                    self.assertEqual(str(len(definition['pages'])), values['PAGE_COUNT'])
                    self.assertEqual(corpus['pages'][name]['raster-sha256'], values['EXPECTED_RASTER_SHA256'])
            refused = subprocess.run([sys.executable, str(SCRIPT), str(first)], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(original, {p.relative_to(first): p.read_bytes() for p in first.rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
