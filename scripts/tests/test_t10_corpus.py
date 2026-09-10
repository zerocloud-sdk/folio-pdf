"""Public generator contract for the independently authored T10 corpus."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t10-corpus.py'


def rgb(path):
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise AssertionError('Expected an original PNG raster')
    offset, compressed = 8, bytearray()
    while offset < len(data):
        size, = struct.unpack('>I', data[offset:offset + 4])
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + size]
        if kind == b'IHDR':
            width, height, depth, color, *_ = struct.unpack('>IIBBBBB', payload)
            if (depth, color) != (8, 2):
                raise AssertionError('Expected opaque 8-bit RGB')
        elif kind == b'IDAT':
            compressed.extend(payload)
        offset += size + 12
    raw = zlib.decompress(compressed)
    rows = []
    for row in range(height):
        start = row * (width * 3 + 1)
        if raw[start] != 0:
            raise AssertionError('Original corpus PNG rows use the fixed None filter')
        rows.append(raw[start + 1:start + width * 3 + 1])
    return width, height, lambda x, y: tuple(rows[y][x * 3:x * 3 + 3])


class T10CorpusTest(unittest.TestCase):
    def test_visual_profiles_bind_every_product_page_to_original_pixels(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            corpus = json.loads((target / 'corpus.json').read_text())
            expected_profiles = sum(len(product['pages']) for product in corpus['products'].values())
            profiles = sorted((target / 'visual').glob('*.properties'))
            self.assertEqual(15, expected_profiles)
            self.assertEqual(expected_profiles, len(profiles))
            for product_name, product in corpus['products'].items():
                for selection, page_name in enumerate(product['pages'], 1):
                    path = target / 'visual' / f'{product_name}-page-{selection}.properties'
                    values = dict(line.split('=', 1) for line in path.read_text().splitlines()
                                  if line and not line.startswith('#'))
                    page = corpus['pages'][page_name]
                    self.assertEqual(f'T10-page-manipulation-merge-split-{product_name}-page-{selection}',
                                     values['PROFILE_ID'])
                    self.assertEqual(str(len(product['pages'])), values['PAGE_COUNT'])
                    self.assertEqual(str(selection), values['PAGE_SELECTION'])
                    self.assertEqual('144', values['DPI'])
                    self.assertEqual(str(page['raster-width']), values['RASTER_WIDTH'])
                    self.assertEqual(str(page['raster-height']), values['RASTER_HEIGHT'])
                    self.assertEqual('../' + page['raster'], values['EXPECTED_RASTER'])
                    self.assertEqual(page['raster-sha256'], values['EXPECTED_RASTER_SHA256'])
                    self.assertEqual('AE', values['COMPARISON_METRIC'])
                    self.assertEqual('0', values['COMPARISON_FUZZ_PERCENT'])
                    self.assertEqual('0', values['COMPARISON_THRESHOLD'])
                    self.assertEqual('0', values['RENDERER_AGREEMENT_THRESHOLD'])

    def test_java_expectations_keep_ordered_values_and_exact_content_programs(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            expectations = target / 'corpus.properties'
            self.assertTrue(expectations.is_file(), 'The public corpus must expose Java 8 readable expectations')
            values = dict(line.split('=', 1) for line in expectations.read_text().splitlines() if line and not line.startswith('#'))
            self.assertEqual('4', values['products.edited.pages.count'])
            self.assertEqual('copy-A', values['products.edited.pages.2'])
            self.assertEqual('4', values['products.merged.destinations.shared-1'])
            self.assertEqual('90', values['pages.A.rotation'])
            self.assertEqual('q 0 0 0 rg 0 0 15 25 re f 0 1 1 rg 20 30 80 60 re f Q\\n',
                             values['pages.copy-A.contents.0'])

    def test_fresh_generation_is_deterministic_and_pins_sources_and_original_pixels(self):
        with tempfile.TemporaryDirectory() as temporary:
            first, second = Path(temporary) / 'first', Path(temporary) / 'second'
            for target in (first, second):
                generated = subprocess.run([sys.executable, str(SCRIPT), str(target)],
                                           capture_output=True, text=True)
                self.assertEqual(0, generated.returncode, generated.stderr)
            original = {p.relative_to(first): p.read_bytes() for p in first.rglob('*') if p.is_file()}
            self.assertEqual(original, {p.relative_to(second): p.read_bytes() for p in second.rglob('*') if p.is_file()})
            corpus = json.loads((first / 'corpus.json').read_text())
            self.assertEqual('T10-page-manipulation-merge-split', corpus['profile'])
            self.assertEqual(['A', 'B', 'C'], corpus['sources']['primary']['pages'])
            self.assertEqual(['D'], corpus['sources']['appendix']['pages'])
            self.assertEqual(['E'], corpus['sources']['cover']['pages'])
            self.assertEqual(['A', 'blank', 'copy-A', 'B'], corpus['products']['edited']['pages'])
            self.assertEqual({'shared': 2, 'shared-1': 4, 'shared-2': 5}, corpus['products']['merged']['destinations'])
            self.assertEqual({'shared-1': 2, 'shared-2': 3}, corpus['products']['right']['destinations'])
            for source in corpus['sources'].values():
                pdf = (first / source['path']).read_bytes()
                self.assertEqual(source['sha256'], hashlib.sha256(pdf).hexdigest())
                start = int(pdf.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', pdf[start:start + 4])
            sizes = {'A': (240, 360), 'B': (480, 360), 'C': (300, 340), 'D': (360, 240),
                     'E': (240, 360), 'copy-A': (240, 360), 'blank': (1224, 1584)}
            pixels = {}
            for name, size in sizes.items():
                raster = first / corpus['pages'][name]['raster']
                width, height, pixels[name] = rgb(raster)
                self.assertEqual(size, (width, height))
                self.assertEqual(corpus['pages'][name]['raster-sha256'], hashlib.sha256(raster.read_bytes()).hexdigest())
            self.assertEqual((0, 0, 0), pixels['A'](5, 5))
            self.assertEqual((255, 255, 0), pixels['A'](80, 60))
            self.assertEqual((0, 255, 255), pixels['copy-A'](80, 60))
            self.assertEqual((255, 0, 0), pixels['A'](80, 100))
            self.assertEqual((255, 0, 0), pixels['copy-A'](80, 100))
            self.assertEqual((0, 255, 0), pixels['D'](160, 179))
            self.assertEqual((0, 0, 255), pixels['E'](99, 179))
            self.assertEqual((255, 255, 255), pixels['blank'](1000, 1400))
            refused = subprocess.run([sys.executable, str(SCRIPT), str(first)], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(original, {p.relative_to(first): p.read_bytes() for p in first.rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
